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

/** Everything the Privileged Display screen renders besides the draft fields (D-149/D-152). */
data class PrivilegedDisplayUiState(
    val tier: Tier = Tier.NONE,
    /** Device Night Light schedule mode — non-MANUAL shows the "system may re-flip this" caveat. */
    val nightLightAutoMode: NightLightAutoMode = NightLightAutoMode.MANUAL,
    /** HDR force-SDR needs Android 14+; the Experimental section is hidden when false. */
    val hdrAvailable: Boolean = false,
    /** Grant-card affordances (mirrors Onboarding's ELEVATED step, shown below ELEVATED). */
    val adbCommand: String = "",
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
    /** Already-resolved Shizuku/root grant feedback (built from string resources in the VM). */
    val grantMessage: String? = null,
    /** The last direct apply had a failed write (revoked/stale grant) — error banner. */
    val writeFailed: Boolean = false,
)

/**
 * Drives the Privileged Display screen (D-149; reworked by D-152). The toggles themselves are
 * PROFILE fields edited through the shared [DraftSettingsViewModel] — this VM only supplies the
 * grant card, the tier, the device's Night Light auto-mode caveat, the HDR availability gate, and
 * [applyNow]: the direct device write used when the auto-brightness service is NOT running (with
 * it running, an Apply flows through the runtime `DisplayTogglesCoordinator` instead).
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

    /** Serializes device access. [io] is a thread POOL: a refresh racing an [applyNow] would
     *  otherwise interleave (the D-143 stale-completion class). */
    private val deviceLock = Mutex()

    init {
        // Live tier, not a one-shot probe: an in-app Shizuku/root grant (which refreshes the shared
        // manager) flips the screen from the grant card to the toggles without leaving it.
        viewModelScope.launch {
            privilegeManager.tierFlow().collect { tier -> _state.update { it.copy(tier = tier) } }
        }
        refresh()
    }

    /** Re-probe the tier + the device facts the screen still reads (auto-mode caveat, HDR gate).
     *  Also clears a lingering write-failure banner — stale news once the user left and returned. */
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
            }
        }
    }

    /**
     * Direct device write of [settings]' display-toggle fields (D-152) — the service-OFF path:
     * with no runtime coordinator alive, an Apply on the profile section would otherwise change
     * nothing until the service next starts (whose seed deliberately adopts without writing).
     * Writes every field unconditionally (idempotent); a null temperature and an unavailable HDR
     * stay untouched, matching the coordinator's semantics.
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

    private fun ShizukuGrantGateway.Result.toMessage(app: Application): String = when (this) {
        ShizukuGrantGateway.Result.Success -> app.getString(R.string.pd_grant_shizuku_ok)
        ShizukuGrantGateway.Result.Unavailable -> app.getString(R.string.pd_grant_shizuku_unavailable)
        ShizukuGrantGateway.Result.PermissionDenied -> app.getString(R.string.pd_grant_shizuku_denied)
        is ShizukuGrantGateway.Result.Failed -> app.getString(R.string.pd_grant_shizuku_failed, reason)
    }
}
