package com.xiaoyu.app.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 锁屏快捷入口（执行书 M7-08）。
 *
 * 方案 A（推荐，零 Manifest 改动）：在聊天 Activity 里调用 [enableLockScreenEntry]，
 * 让页面可以出现在锁屏之上并点亮屏幕，配合通知/快捷方式直接进入聊天。
 * 方案 B：长按应用图标 → 快捷方式，Manifest 增加 `<meta-data>` 指向 shortcuts.xml，
 * 由主代理按需接入（本轮不修改中央 Manifest）。
 */
object LockScreenEntry {

    const val EXTRA_FROM_LOCK_SCREEN = "from_lock_screen"

    /** 让 Activity 可显示在锁屏之上并点亮屏幕（API 27+ 使用官方 API）。 */
    fun enableLockScreenEntry(activity: Activity, enabled: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(enabled)
            activity.setTurnScreenOn(enabled)
        } else {
            @Suppress("DEPRECATION")
            if (enabled) {
                activity.window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    /** 从通知 / 快捷方式拉起聊天页时带上来源标记，UI 可据此关闭侧滑返回等手势。 */
    fun chatIntent(context: Context, activityClass: Class<*>): Intent =
        Intent(context, activityClass).apply {
            putExtra(EXTRA_FROM_LOCK_SCREEN, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
