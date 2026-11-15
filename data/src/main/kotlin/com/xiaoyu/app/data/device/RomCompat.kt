package com.xiaoyu.app.data.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** 国产 ROM 品牌（执行书 8.2 六品牌白名单）。 */
enum class RomBrand(val label: String) {
    XIAOMI("小米 / Redmi"),
    HUAWEI("华为 / 荣耀"),
    OPPO("OPPO / realme"),
    VIVO("vivo / iQOO"),
    SAMSUNG("三星"),
    ONEPLUS("一加"),
    OTHER("其他机型"),
}

/**
 * ROM 兼容工具（执行书 8.2）：判断厂商，提供「自启动管理 / 电池优化」页的候选 Intent。
 * 各厂商页面名不稳定，调用方必须按候选顺序尝试并 fallback 到应用详情页。
 */
object RomCompat {

    /** 按厂商 / 品牌字符串判断 ROM（默认取 Build 信息）。 */
    fun brand(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): RomBrand {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        return when {
            m.contains("oneplus") || b.contains("oneplus") -> RomBrand.ONEPLUS
            m.contains("xiaomi") || b.contains("xiaomi") ||
                m.contains("redmi") || b.contains("redmi") || b.contains("poco") -> RomBrand.XIAOMI
            m.contains("huawei") || b.contains("huawei") ||
                m.contains("honor") || b.contains("honor") -> RomBrand.HUAWEI
            m.contains("vivo") || b.contains("vivo") ||
                m.contains("iqoo") || b.contains("iqoo") -> RomBrand.VIVO
            m.contains("oppo") || b.contains("oppo") ||
                m.contains("realme") || b.contains("realme") -> RomBrand.OPPO
            m.contains("samsung") || b.contains("samsung") -> RomBrand.SAMSUNG
            else -> RomBrand.OTHER
        }
    }

    /** 自启动管理页候选 Intent（从强到弱）；不保证都存在，需配合 [openFirstAvailable]。 */
    fun autoStartIntents(context: Context, brand: RomBrand = RomCompat.brand()): List<Intent> {
        val pkg = context.packageName
        return when (brand) {
            RomBrand.XIAOMI -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
                component("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
            )
            RomBrand.HUAWEI -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            )
            RomBrand.OPPO -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            RomBrand.VIVO -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            RomBrand.SAMSUNG -> listOf(
                component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )
            RomBrand.ONEPLUS -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )
            RomBrand.OTHER -> emptyList()
        }
    }

    /** 当前机型上真实存在的自启动页（系统未安装对应面板时为空）。 */
    fun availableAutoStartIntents(context: Context, brand: RomBrand = RomCompat.brand()): List<Intent> =
        autoStartIntents(context, brand).filter { resolvable(context, it) }

    /** 是否已在电池优化白名单（Doze 豁免）。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 电池优化页候选：优先直接申请豁免，其次打开系统白名单列表，最后应用详情页。 */
    fun batteryOptimizationIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetailsIntent(context),
    )

    /** 应用详情页（最终兜底）。 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 打开自启动管理页；全部候选都不可用时返回文字指引，成功返回 null。 */
    fun openAutoStart(context: Context, brand: RomBrand = RomCompat.brand()): String? =
        openFirstAvailable(context, autoStartIntents(context, brand), brand)

    /** 打开电池优化页；全部候选都不可用时返回文字指引，成功返回 null。 */
    fun openBatteryOptimization(context: Context, brand: RomBrand = RomCompat.brand()): String? =
        openFirstAvailable(context, batteryOptimizationIntents(context), brand)

    /**
     * 按顺序尝试打开候选页；都失败则退到应用详情页，并始终返回该品牌的文字指引
     * （成功打开候选页时返回 null）。调用方可用返回值直接 Toast 给用户。
     */
    fun openFirstAvailable(
        context: Context,
        intents: List<Intent>,
        brand: RomBrand = RomCompat.brand(),
    ): String? {
        intents.forEach { intent ->
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (opened) return null
        }
        runCatching { context.startActivity(appDetailsIntent(context)) }
        return guideText(brand)
    }

    /** 找不到对应页面时的文字指引（步骤级）。 */
    fun guideText(brand: RomBrand = RomCompat.brand()): String = when (brand) {
        RomBrand.XIAOMI ->
            "小米 / Redmi：设置 → 应用设置 → 应用管理 → 小鱼 → 打开「自启动」；省电策略选「无限制」，并允许「后台弹出界面」。"
        RomBrand.HUAWEI ->
            "华为 / 荣耀：设置 → 应用 → 应用启动管理 → 小鱼 → 关闭自动管理；允许自启动、关联启动、后台活动，电池优化选「不允许」。"
        RomBrand.OPPO ->
            "OPPO / realme：设置 → 电池 → 应用耗电管理 → 小鱼 → 允许自启动、允许后台活动、允许关联启动。"
        RomBrand.VIVO ->
            "vivo / iQOO：设置 → 电池 → 后台高耗电 → 打开小鱼；再到 i管家 → 应用管理 → 自启动 → 允许小鱼。"
        RomBrand.SAMSUNG ->
            "三星：设置 → 电池 → 后台使用限制 → 把小鱼加入「从不休眠的应用」。"
        RomBrand.ONEPLUS ->
            "一加：设置 → 电池 → 应用耗电管理 → 小鱼 → 允许自启动与后台活动。"
        RomBrand.OTHER ->
            "请在系统设置的电池 / 应用管理里，允许小鱼自启动与后台运行，并加入电池优化白名单。"
    }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun resolvable(context: Context, intent: Intent): Boolean =
        runCatching { context.packageManager.resolveActivity(intent, 0) != null }.getOrDefault(false)
}
