package cn.mdhyy.yinyanggate.channel

import android.content.Context

class ChannelManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 列表顺序即展示顺序
    private val channels: List<FreezeChannel> = listOf(
        ShizukuChannel(),
        RootChannel(),
        DpmChannel(appContext),
        AdbChannel(appContext),
        PcChannel(),
    )

    fun detect(): List<ChannelStatus> =
        channels.map { ChannelStatus(it.type, it.isAvailable()) }

    fun getPreferredChannel(): ChannelType {
        prefs.getString(KEY_PREFERRED, null)?.let { name ->
            return runCatching { ChannelType.valueOf(name) }.getOrDefault(defaultChannel())
        }
        val def = defaultChannel()
        setPreferredChannel(def)
        return def
    }

    fun setPreferredChannel(type: ChannelType) {
        prefs.edit().putString(KEY_PREFERRED, type.name).apply()
    }

    /** 默认权限：已安装 Shizuku 则优先 Shizuku，否则 ADB。 */
    private fun defaultChannel(): ChannelType =
        if (listOf("moe.shizuku.privileged.api", "moe.shizuku.xyz").any { isPackageInstalled(it) })
            ChannelType.SHIZUKU
        else ChannelType.ADB

    private fun isPackageInstalled(pkg: String): Boolean = try {
        appContext.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** 当前生效通道：直接用用户选择的通道，不做可用性回退。 */
    fun getActiveChannel(): FreezeChannel? =
        channels.find { it.type == getPreferredChannel() }

    companion object {
        const val PREFS_NAME = "yinyang_gate"
        const val KEY_PREFERRED = "preferred_channel"
    }
}

data class ChannelStatus(
    val type: ChannelType,
    val available: Boolean,
)
