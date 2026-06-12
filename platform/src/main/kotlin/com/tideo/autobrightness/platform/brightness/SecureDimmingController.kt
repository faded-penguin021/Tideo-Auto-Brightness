package com.tideo.autobrightness.platform.brightness

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier

// Tasker: task650 _ApplyDimmingPrivileged writes reduce_bright_colors_level/activated
// via Settings.Secure (requires WRITE_SECURE_SETTINGS → ELEVATED tier).
interface SecureDimmingController {
    /** Write reduce_bright_colors_level (0–1000). Fails if tier < ELEVATED. */
    fun setLevel(level: Int): Result<Unit>
    /** Write reduce_bright_colors_activated (0/1). Fails if tier < ELEVATED. */
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
        Settings.Secure.putInt(resolver, "reduce_bright_colors_level", level)
        return Result.success(Unit)
    }

    override fun setActivated(on: Boolean): Result<Unit> {
        if (privilegeManager.currentTier() < Tier.ELEVATED) {
            return Result.failure(SecurityException("WRITE_SECURE_SETTINGS not granted"))
        }
        Settings.Secure.putInt(resolver, "reduce_bright_colors_activated", if (on) 1 else 0)
        return Result.success(Unit)
    }
}
