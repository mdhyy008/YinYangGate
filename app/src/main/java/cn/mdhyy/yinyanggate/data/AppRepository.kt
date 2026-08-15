package cn.mdhyy.yinyanggate.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cn.mdhyy.yinyanggate.channel.ChannelManager
import cn.mdhyy.yinyanggate.channel.ChannelType
import cn.mdhyy.yinyanggate.channel.FreezeResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences(ChannelManager.PREFS_NAME, Context.MODE_PRIVATE)
    val channelManager = ChannelManager(context)

    private val snapshotRoot: File get() = File(context.filesDir, "snapshot")
    private val snapshotJson: File get() = File(snapshotRoot, "snapshot.json")
    private val snapshotIcons: File get() = File(snapshotRoot, "icons")

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val self = context.packageName
        val installed = pm.getInstalledApplications(0)
            .filter { it.packageName != self }
        val t1 = System.currentTimeMillis()
        val apps = installed
            .map { ai ->
                val enabled = pm.getApplicationEnabledSetting(ai.packageName)
                AppInfo(
                    packageName = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    icon = ai.loadIcon(pm)?.let { drawableToBitmap(it) },
                    isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isFrozen = enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                        enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                )
            }
            .sortedBy { it.label.lowercase() }
        val t2 = System.currentTimeMillis()
        saveSnapshot(apps)
        Log.d(TAG, "loadApps apps=${apps.size} query=${t1 - t0}ms build=${t2 - t1}ms save=${System.currentTimeMillis() - t2}ms total=${System.currentTimeMillis() - t0}ms")
        apps
    }

    fun isFrozen(packageName: String): Boolean {
        val enabled = pm.getApplicationEnabledSetting(packageName)
        return enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
            enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    // ---------- 应用列表快照缓存 ----------

    /** 从本地快照构建列表;无快照 / 损坏 / 版本不符返回 null。图标缺失的应用 icon 为 null。 */
    suspend fun loadSnapshot(): List<AppInfo>? = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val result = runCatching {
            val o = JSONObject(snapshotJson.readText())
            if (o.optInt("version") != SNAPSHOT_VERSION) return@runCatching null
            val arr = o.getJSONArray("apps")
            (0 until arr.length()).map { i ->
                val a = arr.getJSONObject(i)
                AppInfo(
                    packageName = a.getString("packageName"),
                    label = a.getString("label"),
                    icon = readIconFile(a.getString("packageName")),
                    isSystemApp = a.optBoolean("isSystemApp"),
                    isFrozen = a.optBoolean("isFrozen"),
                )
            }
        }.getOrNull()
        Log.d(TAG, "loadSnapshot ${System.currentTimeMillis() - t0}ms size=${result?.size}")
        result
    }

    /** 当前已安装包名集合与快照集合不同则为 stale。与 loadApps 共用 getInstalledApplications(0),口径一致。 */
    suspend fun isSnapshotStale(snapshot: List<AppInfo>): Boolean = withContext(Dispatchers.IO) {
        val self = context.packageName
        val installed = pm.getInstalledApplications(0)
            .map { it.packageName }
            .filter { it != self }
            .toSet()
        installed != snapshot.map { it.packageName }.toSet()
    }

    /** 轻量校验每个应用的冻结状态,不重载 label/icon,保持原顺序。 */
    suspend fun refreshFrozen(apps: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val result = apps.map { app ->
            val frozen = isFrozen(app.packageName)
            if (frozen == app.isFrozen) app else app.copy(isFrozen = frozen)
        }
        Log.d(TAG, "refreshFrozen ${System.currentTimeMillis() - t0}ms size=${apps.size}")
        result
    }

    /** 全量加载后调用:补写缺失图标、清理已卸载应用的孤儿图标、写元数据。 */
    suspend fun saveSnapshot(apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        runCatching {
            snapshotRoot.mkdirs()
            snapshotIcons.mkdirs()
            val pkgSet = apps.map { it.packageName }.toSet()

            for (app in apps) {
                val icon = app.icon ?: continue
                val f = iconFile(app.packageName)
                if (!f.exists()) {
                    FileOutputStream(f).use { out ->
                        icon.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }

            snapshotIcons.listFiles()
                ?.filter { it.isFile && it.extension == "png" && it.nameWithoutExtension !in pkgSet }
                ?.forEach { it.delete() }

            writeSnapshotJson(apps)
        }
        Log.d(TAG, "saveSnapshot ${System.currentTimeMillis() - t0}ms icons=${apps.count { it.icon != null }}")
    }

    /** 仅重写元数据,不触碰图标。refreshFrozen 校正外部冻结状态变化后调用。 */
    suspend fun saveSnapshotMetadata(apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        runCatching { writeSnapshotJson(apps) }
    }

    private fun writeSnapshotJson(apps: List<AppInfo>) {
        snapshotRoot.mkdirs()
        val body = JSONObject().apply {
            put("version", SNAPSHOT_VERSION)
            put("timestamp", System.currentTimeMillis())
            put("apps", JSONArray().apply {
                apps.forEach { app ->
                    put(JSONObject().apply {
                        put("packageName", app.packageName)
                        put("label", app.label)
                        put("isSystemApp", app.isSystemApp)
                        put("isFrozen", app.isFrozen)
                    })
                }
            })
        }.toString()
        val tmp = File(snapshotRoot, "snapshot.json.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(snapshotJson)) {
            snapshotJson.delete()
            tmp.renameTo(snapshotJson)
        }
    }

    private fun iconFile(packageName: String): File = File(snapshotIcons, "$packageName.png")

    private fun readIconFile(packageName: String): ImageBitmap? =
        BitmapFactory.decodeFile(iconFile(packageName).absolutePath)?.asImageBitmap()

    suspend fun freezeApp(app: AppInfo): FreezeResult = withContext(Dispatchers.IO) {
        val channel = channelManager.getActiveChannel()
            ?: return@withContext FreezeResult.Error("无可用通道，请先开启 root / Shizuku / ADB 之一")
        val result = channel.freeze(app.packageName, app.label)
        if (result.isSuccess) {
            addRecord(FreezeRecord(app.packageName, app.label, System.currentTimeMillis(), channel.type.name, "冻结"))
        }
        result
    }

    suspend fun unfreezeApp(app: AppInfo): FreezeResult = withContext(Dispatchers.IO) {
        val channel = channelManager.getActiveChannel()
            ?: return@withContext FreezeResult.Error("无可用通道，请先开启 root / Shizuku / ADB 之一")
        val result = channel.unfreeze(app.packageName, app.label)
        if (result.isSuccess) {
            addRecord(FreezeRecord(app.packageName, app.label, System.currentTimeMillis(), channel.type.name, "解冻"))
        }
        result
    }

    /** 全部操作日志，按时间倒序。 */
    fun getRecords(): List<FreezeRecord> {
        val arr = recordsJson()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FreezeRecord(
                packageName = o.getString("packageName"),
                label = o.getString("label"),
                timestamp = o.getLong("timestamp"),
                channel = o.getString("channel"),
                action = o.optString("action", "冻结"),
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun recordsJson(): JSONArray =
        runCatching { JSONArray(prefs.getString(KEY_RECORDS, null)) }.getOrElse { JSONArray() }

    private fun addRecord(r: FreezeRecord) {
        val arr = recordsJson()
        arr.put(JSONObject().apply {
            put("packageName", r.packageName)
            put("label", r.label)
            put("timestamp", r.timestamp)
            put("channel", r.channel)
            put("action", r.action)
        })
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    companion object {
        private const val TAG = "YinYangRepo"
        private const val KEY_RECORDS = "freeze_history"
        private const val SNAPSHOT_VERSION = 1
    }
}
