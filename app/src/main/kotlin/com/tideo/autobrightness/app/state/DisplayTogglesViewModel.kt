package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.AppModule
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

/** Everything the Privileged Display screen renders (D-149). */
data class PrivilegedDisplayUiState(
    val tier: Tier = Tier.NONE,
    val nightLight: Boolean = false,
    /** null = the device default is in effect (`night_display_color_temperature` never set). */
    val nightLightTemperature: Int? = null,
    val nightLightAutoMode: NightLightAutoMode = NightLightAutoMode.MANUAL,
    val daltonizer: DaltonizerMode = DaltonizerMode.OFF,
    val inversion: Boolean = false,
    val alwaysOnDisplay: Boolean = false,
    val stayAwakePlugged: Boolean = false,
    /** HDR force-SDR needs Android 14+; the Experimental section is hidden when false. */
    val hdrAvailable: Boolean = false,
    val hdrForceSdr: Boolean = false,
    /** Grant-card affordances (mirrors Onboarding's ELEVATED step, shown below ELEVATED). */
    val adbCommand: String = "",
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
    /** Already-resolved Shizuku/root grant feedback (built from string resources in the VM). */
    val grantMessage: String? = null,
    /** The last write returned failure (revoked/stale grant) — surfaced as an error banner; the
     *  unconditional read-back below already snapped the control to the device's real value. */
    val writeFailed: Boolean = false,
)

/**
 * Drives the Privileged Display screen (D-149, `plans/privileged-display.md` Segment 2). All toggle
 * state is **read back from the device** (never cached optimistically): every write is followed by a
 * full re-read on [io], so the UI always shows what the device actually has — a failed or
 * OEM-ignored write snaps the control back instead of lying. `refresh()` re-probes on resume, so
 * changes made in the system Settings app (or an adb grant) surface on return.
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

    /** Serializes write→read-back→publish. [io] is a thread POOL: two rapid toggles would otherwise
     *  run concurrently and the loser's stale `writeFailed` could overwrite the newer result
     *  (glue-review, D-143 bug class: stale async completion published over newer state). */
    private val deviceLock = Mutex()

    init {
        // Live tier, not a one-shot probe: an in-app Shizuku/root grant (which refreshes the shared
        // manager) flips the screen from the grant card to the toggles without leaving it.
        viewModelScope.launch {
            privilegeManager.tierFlow().collect { tier -> _state.update { it.copy(tier = tier) } }
        }
        refresh()
    }

    /** Re-probe the tier + re-read every toggle (call on resume and after grants). Also clears a
     *  lingering write-failure banner: it describes "the last change", which is stale news once the
     *  user has left and returned (the read-back below shows the current device truth anyway). */
    fun refresh() {
        privilegeManager.refresh()
        _state.update { it.copy(shizukuAvailability = privilegeManager.shizukuAvailability()) }
        viewModelScope.launch(io) {
            deviceLock.withLock { _state.update { readBack(it).copy(writeFailed = false) } }
        }
    }

    fun setNightLight(on: Boolean) = write { display.setNightLight(on) }
    fun setNightLightTemperature(kelvin: Int) = write { display.setNightLightTemperature(kelvin) }
    fun setDaltonizer(mode: DaltonizerMode) = write { display.setDaltonizer(mode) }
    fun setInversion(on: Boolean) = write { display.setInversion(on) }
    fun setAlwaysOnDisplay(on: Boolean) = write { display.setAlwaysOnDisplay(on) }
    fun setStayAwakePlugged(on: Boolean) = write { display.setStayAwakePlugged(on) }
    fun setHdrForceSdr(on: Boolean) = write { display.setHdrForceSdr(on) }

    /** One-tap Shizuku grant (needs a running Shizuku); tier refresh happens inside the manager. */
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

    private fun write(op: () -> Result<Unit>) {
        viewModelScope.launch(io) {
            deviceLock.withLock {
                val result = op()
                _state.update { readBack(it).copy(writeFailed = result.isFailure) }
            }
        }
    }

    private fun readBack(current: PrivilegedDisplayUiState): PrivilegedDisplayUiState = current.copy(
        nightLight = display.readNightLight(),
        nightLightTemperature = display.readNightLightTemperature(),
        nightLightAutoMode = display.readNightLightAutoMode(),
        daltonizer = display.readDaltonizer(),
        inversion = display.readInversion(),
        alwaysOnDisplay = display.readAlwaysOnDisplay(),
        stayAwakePlugged = display.readStayAwakePlugged(),
        hdrAvailable = display.hdrForceSdrAvailable,
        hdrForceSdr = display.readHdrForceSdr(),
    )

    private fun ShizukuGrantGateway.Result.toMessage(app: Application): String = when (this) {
        ShizukuGrantGateway.Result.Success -> app.getString(R.string.pd_grant_shizuku_ok)
        ShizukuGrantGateway.Result.Unavailable -> app.getString(R.string.pd_grant_shizuku_unavailable)
        ShizukuGrantGateway.Result.PermissionDenied -> app.getString(R.string.pd_grant_shizuku_denied)
        is ShizukuGrantGateway.Result.Failed -> app.getString(R.string.pd_grant_shizuku_failed, reason)
    }
}
