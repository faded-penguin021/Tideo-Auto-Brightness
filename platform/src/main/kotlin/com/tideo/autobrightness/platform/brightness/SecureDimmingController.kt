package com.tideo.autobrightness.platform.brightness

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier

/** SecureDimming controller (task650 _ApplyDimmingPrivileged): requires WRITE_SECURE_SETTINGS (ELEVATED tier). */
interface SecureDimmingController {
    fun setLevel(level: Int): Result<Unit>
    fun setActivated(on: Boolean): Result<Unit>
}

class AndroidSecureDimmingController(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
) : SecureDimmingController {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun setLevel(level: Int): Result<Unit> {
        if (privilegeManager.currentTier() < Tier.ELEVATED) {
            return Result.failure(SecurityException("WRITE_SECURE_SETTINGS not granted"))
        }
        return runCatching {
            Settings.Secure.putInt(resolver, "reduce_bright_colors_level", level.coerceIn(0, 1000))
        }
    }

    override fun setActivated(on: Boolean): Result<Unit> {
        if (privilegeManager.currentTier() < Tier.ELEVATED) {
            return Result.failure(SecurityException("WRITE_SECURE_SETTINGS not granted"))
        }
        return runCatching {
            Settings.Secure.putInt(resolver, "reduce_bright_colors_activated", if (on) 1 else 0)
        }
    }
}
