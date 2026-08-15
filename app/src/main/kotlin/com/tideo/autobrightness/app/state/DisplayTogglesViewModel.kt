package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.AndroidSecureDisplayController
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.ShizukuAvailability
import com.tideo.autobrightness.platform.privilege.ShizukuGrantGateway
import com.tideo.autobrightness.platform.privilege.Tier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Privileged Display screen state (D-149/D-152): tier, device facts, grant affordances. */
data class PrivilegedDisplayUiState(
    val tier: Tier = Tier.NONE,
    val nightLightAutoMode: NightLightAutoMode = NightLightAutoMode.MANUAL,
    val nightLightAvailable: Boolean = false,
    val alwaysOnDisplayAvailable: Boolean = false,
    val hdrAvailable: Boolean = false,
    val hdrPreferenceCustom: Boolean = false,
    val adbCommand: String = "",
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
    val grantMessage: String? = null,
    val writeFailed: Boolean = false,
)

/**
 * Privileged Display screen driver (D-149/D-152): grant card, tier, device facts.
 * The screen's Apply enters at [applyDraft]: [applyNow] writes directly when the service is OFF;
 * with it ON, Apply flows through the coordinator and only the read-back is invalidated (DB-048).
 */
class DisplayTogglesViewModel @JvmOverloads constructor(
    application: Application,
    private val privilegeManager: PrivilegeManager = AppModule(application).privilegeManager,
    private val display: SecureDisplayController =
        AndroidSecureDisplayController(application, privilegeManager),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        PrivilegedDisplayUiState(
            tier = privilegeManager.currentTier(),
            adbCommand = privilegeManager.adbGrantInstruction(),
            shizukuAvailability = privilegeManager.shizukuAvailability(),
            nightLightAvailable = display.nightLightAvailable,
            alwaysOnDisplayAvailable = display.alwaysOnDisplayAvailable,
        ),
    )
    val state: StateFlow<PrivilegedDisplayUiState> = _state.asStateFlow()

    private val _deviceSnapshot = MutableStateFlow<DeviceDisplaySnapshot?>(null)

    /** DB-034: last device read-back, or null below ELEVATED (the toggles do not compose there). */
    val deviceSnapshot: StateFlow<DeviceDisplaySnapshot?> = _deviceSnapshot.asStateFlow()


    // DB-047: invocation-ordered device access with atomic stale-publication suppression.
    private val deviceLock = Mutex()
    private val deviceScheduleLock = Any()
    private var deviceRequestGeneration = 0L
    private var deviceOperationTail: Job? = null

    init {
        // Live tier: in-app Shizuku/root grant flips screen from grant card to toggles without leaving.
        viewModelScope.launch {
            privilegeManager.tierFlow().collect { tier -> _state.update { it.copy(tier = tier) } }
        }
        refresh()
    }

    /** Re-probe tier and device facts; clear lingering write-failure banner. */
    fun refresh() {
        privilegeManager.refresh()
        _state.update { it.copy(shizukuAvailability = privilegeManager.shizukuAvailability()) }
        scheduleDeviceOperation { generation ->
            deviceLock.withLock {
                val snapshot = readSnapshotLocked()
                val nightLightAutoMode = display.readNightLightAutoMode()
                publishIfCurrent(generation) {
                    _state.update {
                        it.copy(
                            nightLightAutoMode = nightLightAutoMode,
                            nightLightAvailable = display.nightLightAvailable,
                            alwaysOnDisplayAvailable = display.alwaysOnDisplayAvailable,
                            hdrAvailable = display.hdrForceSdrAvailable && snapshot?.hdrForceSdr != null,
                            hdrPreferenceCustom = display.hdrForceSdrAvailable &&
                                snapshot != null && snapshot.hdrForceSdr == null,
                            writeFailed = false,
                        )
                    }
                    _deviceSnapshot.value = snapshot
                }
            }
        }
    }

    /** DB-034: reads need no grant (only writes are tier-gated), but below ELEVATED nothing renders. */
    private fun readSnapshotLocked(): DeviceDisplaySnapshot? {
        if (privilegeManager.currentTier() < Tier.ELEVATED) return null
        return DeviceDisplaySnapshot(
            nightLight = if (display.nightLightAvailable) display.readNightLight() else null,
            temperatureK = display.readNightLightTemperature(),
            daltonizer = display.readDaltonizer(),
            inversion = display.readInversion(),
            alwaysOn = if (display.alwaysOnDisplayAvailable) display.readAlwaysOnDisplay() else null,
            stayAwake = display.readStayAwakePlugged(),
            hdrForceSdr = if (display.hdrForceSdrAvailable) display.readHdrForceSdr() else null,
        )
    }

    /**
     * DB-048: the screen's Apply, both halves of D-152's split. With the service OFF this VM writes
     * the device; with it ON the runtime coordinator does, later and with no completion signal to
     * wait on. Either way the published snapshot describes the PRE-Apply device, so it must stop
     * being mergeable before the draft epoch advances — otherwise DB-047's rollback simply moves to
     * the service-ON path. The coordinator path re-reads on the next ON_RESUME, not here: a read
     * scheduled now would race the coordinator's own write and republish the same stale truth.
     */
    fun applyDraft(settings: AabSettings, serviceEnabled: Boolean) {
        if (serviceEnabled) invalidateDeviceSnapshot() else applyNow(settings)
    }

    /** DB-048: drop the snapshot and suppress any in-flight operation still holding an older one. */
    private fun invalidateDeviceSnapshot() {
        synchronized(deviceScheduleLock) {
            deviceRequestGeneration++
            _deviceSnapshot.value = null
        }
    }

    /**
     * Direct device write of display-toggle fields (D-152, service-OFF path).
     * All fields idempotent; null temperature and unavailable HDR stay untouched.
     */
    fun applyNow(settings: AabSettings) {
        scheduleDeviceOperation(invalidateSnapshot = true) { generation ->
            deviceLock.withLock {
                val hdrState = if (display.hdrForceSdrAvailable) display.readHdrForceSdr() else null
                val results = buildList {
                    add(display.setNightLight(settings.nightLightEnabled))
                    settings.nightLightTemperature?.let { add(display.setNightLightTemperature(it)) }
                    add(
                        display.setDaltonizer(
                            DaltonizerMode.entries.firstOrNull { it.name == settings.daltonizerMode }
                                ?: DaltonizerMode.OFF,
                        ),
                    )
                    add(display.setInversion(settings.inversionEnabled))
                    add(display.setAlwaysOnDisplay(settings.alwaysOnDisplayEnabled))
                    add(display.setStayAwakePlugged(settings.stayAwakeChargingEnabled))
                    if (display.hdrForceSdrAvailable && hdrState != null) {
                        add(display.setHdrForceSdr(settings.hdrForceSdrEnabled))
                    }
                }
                // DB-047: publish the post-write device truth; retaining the pre-Apply snapshot
                // makes the draft merge immediately undo the visible toggle.
                val snapshot = readSnapshotLocked()
                publishIfCurrent(generation) {
                    _state.update {
                        it.copy(
                            hdrAvailable = display.hdrForceSdrAvailable && snapshot?.hdrForceSdr != null,
                            hdrPreferenceCustom = display.hdrForceSdrAvailable &&
                                snapshot != null && snapshot.hdrForceSdr == null,
                            writeFailed = results.any { r -> r.isFailure },
                        )
                    }
                    _deviceSnapshot.value = snapshot
                }
            }
        }
    }

    private fun scheduleDeviceOperation(
        invalidateSnapshot: Boolean = false,
        operation: suspend (Long) -> Unit,
    ) {
        synchronized(deviceScheduleLock) {
            val generation = ++deviceRequestGeneration
            if (invalidateSnapshot) _deviceSnapshot.value = null
            val predecessor = deviceOperationTail
            deviceOperationTail = viewModelScope.launch(io) {
                predecessor?.join()
                operation(generation)
            }
        }
    }

    private fun publishIfCurrent(generation: Long, publication: () -> Unit) {
        synchronized(deviceScheduleLock) {
            if (generation == deviceRequestGeneration) publication()
        }
    }

    fun requestShizukuGrant() {
        _state.update { it.copy(grantMessage = getApplication<Application>().getString(R.string.pd_grant_requesting)) }
        privilegeManager.requestShizukuGrant { result ->
            _state.update {
                it.copy(
                    grantMessage = result.toMessage(getApplication()),
                    shizukuAvailability = privilegeManager.shizukuAvailability(),
                )
            }
            if (result is ShizukuGrantGateway.Result.Success) refresh()
        }
    }

    fun tryRootGrant() {
        viewModelScope.launch(io) {
            val granted = privilegeManager.tryGrantViaRoot()
            _state.update {
                it.copy(
                    grantMessage = getApplication<Application>().getString(
                        if (granted) R.string.pd_grant_root_ok else R.string.pd_grant_root_failed,
                    ),
                )
            }
            if (granted) refresh()
        }
    }

    private fun ShizukuGrantGateway.Result.toMessage(app: Application): String = when (this) {
        ShizukuGrantGateway.Result.Success -> app.getString(R.string.pd_grant_shizuku_ok)
        ShizukuGrantGateway.Result.Unavailable -> app.getString(R.string.pd_grant_shizuku_unavailable)
        ShizukuGrantGateway.Result.PermissionDenied -> app.getString(R.string.pd_grant_shizuku_denied)
        is ShizukuGrantGateway.Result.Failed -> app.getString(R.string.pd_grant_shizuku_failed, reason)
    }
}
