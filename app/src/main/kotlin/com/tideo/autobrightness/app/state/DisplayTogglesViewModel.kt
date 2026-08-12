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
    val hdrAvailable: Boolean = false,
    val adbCommand: String = "",
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
    val grantMessage: String? = null,
    val writeFailed: Boolean = false,
)

/**
 * Privileged Display screen driver (D-149/D-152): grant card, tier, device facts.
 * [applyNow] writes directly when service is OFF; with it ON, Apply flows through coordinator.
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
        ),
    )
    val state: StateFlow<PrivilegedDisplayUiState> = _state.asStateFlow()

    private val _deviceSnapshot = MutableStateFlow<DeviceDisplaySnapshot?>(null)

    /** DB-034: last device read-back, or null below ELEVATED (the toggles do not compose there). */
    val deviceSnapshot: StateFlow<DeviceDisplaySnapshot?> = _deviceSnapshot.asStateFlow()

    // Serializes device access: [io] is a thread POOL; prevent refresh/applyNow interleave (D-143).
    private val deviceLock = Mutex()

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
        viewModelScope.launch(io) {
            deviceLock.withLock {
                _state.update {
                    it.copy(
                        nightLightAutoMode = display.readNightLightAutoMode(),
                        hdrAvailable = display.hdrForceSdrAvailable,
                        writeFailed = false,
                    )
                }
                _deviceSnapshot.value = readSnapshotLocked()
            }
        }
    }

    /** DB-034: reads need no grant (only writes are tier-gated), but below ELEVATED nothing renders. */
    private fun readSnapshotLocked(): DeviceDisplaySnapshot? {
        if (privilegeManager.currentTier() < Tier.ELEVATED) return null
        return DeviceDisplaySnapshot(
            nightLight = display.readNightLight(),
            temperatureK = display.readNightLightTemperature(),
            daltonizer = display.readDaltonizer(),
            inversion = display.readInversion(),
            alwaysOn = display.readAlwaysOnDisplay(),
            stayAwake = display.readStayAwakePlugged(),
            hdrForceSdr = if (display.hdrForceSdrAvailable) display.readHdrForceSdr() else null,
        )
    }

    /**
     * Direct device write of display-toggle fields (D-152, service-OFF path).
     * All fields idempotent; null temperature and unavailable HDR stay untouched.
     */
    fun applyNow(settings: AabSettings) {
        viewModelScope.launch(io) {
            deviceLock.withLock {
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
                    if (display.hdrForceSdrAvailable) add(display.setHdrForceSdr(settings.hdrForceSdrEnabled))
                }
                _state.update { it.copy(writeFailed = results.any { r -> r.isFailure }) }
            }
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
