package cn.mdhyy.yinyanggate.channel

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import cn.mdhyy.yinyanggate.admin.AdminReceiver
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku

enum class ChannelType { ROOT, SHIZUKU, DPM, ADB, PC }

sealed class FreezeResult {
    data object Success : FreezeResult()
    data class Error(val message: String) : FreezeResult()
    data class NeedManual(val command: String) : FreezeResult()

    val isSuccess: Boolean get() = this is Success
}

interface FreezeChannel {
    val type: ChannelType
    fun isAvailable(): Boolean
    fun freeze(packageName: String, label: String? = null): FreezeResult
    fun unfreeze(packageName: String, label: String? = null): FreezeResult
}

class RootChannel : FreezeChannel {
    override val type = ChannelType.ROOT

    override fun isAvailable(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    } catch (e: Exception) {
        false
    }

    override fun freeze(packageName: String, label: String?): FreezeResult =
        run(arrayOf("su", "-c", "pm disable-user --user 0 $packageName"))

    override fun unfreeze(packageName: String, label: String?): FreezeResult =
        run(arrayOf("su", "-c", "pm enable $packageName"))

    private fun run(cmd: Array<String>): FreezeResult = try {
        val p = Runtime.getRuntime().exec(cmd)
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code == 0) FreezeResult.Success else FreezeResult.Error(err.ifBlank { out }.trim())
    } catch (e: Exception) {
        FreezeResult.Error(e.message ?: "root 执行失败")
    }
}

class ShizukuChannel : FreezeChannel {
    override val type = ChannelType.SHIZUKU

    override fun isAvailable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    override fun freeze(packageName: String, label: String?): FreezeResult =
        runCommand("pm", "disable-user", "--user", "0", packageName)

    override fun unfreeze(packageName: String, label: String?): FreezeResult =
        runCommand("pm", "enable", packageName)

    private fun runCommand(vararg cmd: String): FreezeResult = try {
        val process = Shizuku.newProcess(cmd, null, null)
            ?: return FreezeResult.Error("无法启动 Shizuku 进程")
        process.waitFor()
        if (process.exitValue() == 0) FreezeResult.Success
        else FreezeResult.Error("命令失败 (${process.exitValue()})")
    } catch (e: Exception) {
        FreezeResult.Error(e.message ?: "Shizuku 执行失败")
    }
}

class DpmChannel(context: Context) : FreezeChannel {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(appContext, AdminReceiver::class.java)

    override val type = ChannelType.DPM

    override fun isAvailable(): Boolean = try {
        dpm.isDeviceOwnerApp(appContext.packageName)
    } catch (e: Exception) {
        false
    }

    override fun freeze(packageName: String, label: String?): FreezeResult = try {
        dpm.setApplicationHidden(admin, packageName, true)
        FreezeResult.Success
    } catch (e: Exception) {
        FreezeResult.Error(e.message ?: "DPM 冻结失败")
    }

    override fun unfreeze(packageName: String, label: String?): FreezeResult = try {
        dpm.setApplicationHidden(admin, packageName, false)
        FreezeResult.Success
    } catch (e: Exception) {
        FreezeResult.Error(e.message ?: "DPM 解冻失败")
    }
}

class AdbChannel(context: Context) : FreezeChannel {
    private val appContext = context.applicationContext

    override val type = ChannelType.ADB

    override fun isAvailable(): Boolean = try {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (e: Exception) {
        false
    }

    override fun freeze(packageName: String, label: String?): FreezeResult =
        FreezeResult.NeedManual("adb shell pm disable-user --user 0 $packageName")

    override fun unfreeze(packageName: String, label: String?): FreezeResult =
        FreezeResult.NeedManual("adb shell pm enable $packageName")
}

/** 电脑客户端：冻结指令通过局域网 UDP 发给 Win 端，由电脑执行 adb 并回传结果。 */
class PcChannel : FreezeChannel {
    override val type = ChannelType.PC

    override fun isAvailable(): Boolean = PcClientManager.isConnected

    override fun freeze(packageName: String, label: String?): FreezeResult = runBlocking {
        PcClientManager.freeze(packageName, "disable", label)
    }

    override fun unfreeze(packageName: String, label: String?): FreezeResult = runBlocking {
        PcClientManager.freeze(packageName, "enable", label)
    }
}
