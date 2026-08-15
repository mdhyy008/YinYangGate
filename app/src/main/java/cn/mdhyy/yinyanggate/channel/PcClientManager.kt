package cn.mdhyy.yinyanggate.channel

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import cn.mdhyy.yinyanggate.data.AppInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * 电脑客户端 UDP 通信核心。
 *
 * 手机绑定 45678 端口，Win 端监听 45679。手机广播 DISCOVER 查找电脑，连接成功后
 * 自动推送分片应用列表；收到 REQUEST_LIST 重新推送；收到 FREEZE_RESULT 唤醒等待方。
 */
object PcClientManager {

    private const val TAG = "PcClient"

    const val PHONE_PORT = 45678
    const val PC_PORT = 45679

    private const val FRAG_MAX_BYTES = 1200
    private const val DISCOVER_TIMEOUT_MS = 3_000L
    private const val FREEZE_TIMEOUT_MS = 15_000L

    enum class Status { IDLE, SEARCHING, CONNECTED, FAILED }

    data class PcState(
        val status: Status = Status.IDLE,
        val pcName: String? = null,
        val pcAddress: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(PcState())
    val state: StateFlow<PcState> = _state.asStateFlow()

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var pcHost: InetAddress? = null
    @Volatile private var pcPort = PC_PORT
    private var receiveThread: Thread? = null
    @Volatile private var running = false

    private val pendingFreeze = ConcurrentHashMap<String, CompletableDeferred<FreezeResult>>()

    /** 应用列表提供者，由 MainActivity 注入，用于连接成功后 / 收到 REQUEST_LIST 时自动推送。 */
    @Volatile var listProvider: (suspend () -> List<AppInfo>)? = null

    private var contentResolver: ContentResolver? = null

    @Volatile var initialized = false
        private set

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        contentResolver = context.applicationContext.contentResolver
        start()
    }

    @Synchronized
    fun start() {
        if (running) return
        try {
            socket = DatagramSocket(PHONE_PORT).apply { broadcast = true }
        } catch (e: IOException) {
            socket = null
            _state.value = PcState(Status.FAILED, message = "UDP 端口 $PHONE_PORT 被占用")
            return
        }
        running = true
        receiveThread = Thread({ receiveLoop() }, "pc-udp-receive").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    val isConnected: Boolean get() = _state.value.status == Status.CONNECTED

    // ---------- 查找电脑 ----------

    /** 广播查找电脑；manualIp 非空时改为单播直连该 IP（兜底）。 */
    fun findPc(manualIp: String? = null) {
        if (!initialized) return
        val s = socket ?: return
        _state.value = PcState(
            Status.SEARCHING,
            message = if (manualIp != null) "正在连接 $manualIp…" else "正在查找电脑…",
        )

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val msg = JSONObject().put("type", "DISCOVER").put("phone", phoneName())
                val data = msg.toString().toByteArray(Charsets.UTF_8)
                val target = if (manualIp != null) {
                    InetAddress.getByName(manualIp)
                } else {
                    InetAddress.getByName("255.255.255.255")
                }
                s.send(DatagramPacket(data, data.size, target, PC_PORT))
                Log.d(TAG, "DISCOVER sent to $target:$PC_PORT")
            } catch (e: Exception) {
                Log.w(TAG, "DISCOVER send fail", e)
                _state.value = PcState(Status.FAILED, message = "发送查找失败: ${e.message}")
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            delay(DISCOVER_TIMEOUT_MS)
            if (_state.value.status == Status.SEARCHING) {
                _state.value = PcState(
                    Status.FAILED,
                    message = if (manualIp != null) "连接 $manualIp 失败" else "未找到电脑，请确认电脑端已开启且在同一网络",
                )
            }
        }
    }

    // ---------- 应用列表推送 ----------

    suspend fun pushAppList(load: suspend () -> List<AppInfo>) {
        if (!isConnected) {
            Log.w(TAG, "pushAppList skip: not connected")
            return
        }
        val apps = load()
        if (apps.isEmpty()) {
            Log.w(TAG, "pushAppList skip: empty list")
            return
        }
        Log.d(TAG, "pushAppList start, apps=${apps.size}")

        sendToPc(
            JSONObject()
                .put("type", "APP_LIST_START")
                .put("total", apps.size)
                .put("frozen", apps.count { it.isFrozen })
                .toString(),
        )

        var index = 0
        var items = JSONArray()
        var size = 0
        for (app in apps) {
            val item = JSONObject()
                .put("p", app.packageName)
                .put("n", app.label)
                .put("s", app.isSystemApp)
                .put("f", app.isFrozen)
                .put("c", colorForLabel(app.label))
            val add = item.toString().toByteArray(Charsets.UTF_8).size + 1
            if (items.length() > 0 && size + add > FRAG_MAX_BYTES) {
                sendFrag(index, items)
                index++
                items = JSONArray()
                size = 0
            }
            items.put(item)
            size += add
        }
        if (items.length() > 0) sendFrag(index, items)

        sendToPc(JSONObject().put("type", "APP_LIST_END").toString())
        Log.d(TAG, "pushAppList end, frags=$index")
    }

    private fun sendFrag(index: Int, items: JSONArray) {
        Log.d(TAG, "send frag $index items=${items.length()}")
        sendToPc(
            JSONObject()
                .put("type", "APP_LIST_FRAG")
                .put("index", index)
                .put("items", items)
                .toString(),
        )
    }

    // ---------- 冻结指令（发送并等待电脑回传） ----------

    suspend fun freeze(packageName: String, action: String, label: String? = null): FreezeResult {
        if (!isConnected) return FreezeResult.Error("未连接电脑")
        val key = "$packageName|$action"
        val deferred = CompletableDeferred<FreezeResult>()
        pendingFreeze[key] = deferred
        val msg = JSONObject()
            .put("type", "FREEZE")
            .put("pkg", packageName)
            .put("action", action)
        if (label != null) msg.put("label", label)
        androidId()?.let { msg.put("androidId", it) }
        sendToPc(msg.toString())
        val result = withTimeoutOrNull(FREEZE_TIMEOUT_MS) { deferred.await() }
        pendingFreeze.remove(key)
        return result ?: FreezeResult.Error("等待电脑响应超时")
    }

    // ---------- 接收 ----------

    private fun receiveLoop() {
        val s = socket ?: return
        val buf = ByteArray(4096)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)
                val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val msg = runCatching { JSONObject(json) }.getOrNull() ?: continue
                Log.d(TAG, "recv from ${packet.address}:${packet.port} ${msg.optString("type")}")
                when (msg.optString("type")) {
                    "DISCOVER_REPLY" -> onDiscoverReply(msg, packet.address, packet.port)
                    "REQUEST_LIST" -> onRequestList()
                    "FREEZE_RESULT" -> onFreezeResult(msg)
                }
            } catch (e: IOException) {
                if (!running) break
            }
        }
    }

    private fun onDiscoverReply(msg: JSONObject, from: InetAddress, fromPort: Int) {
        Log.d(TAG, "DISCOVER_REPLY from $from:$fromPort win=${msg.optString("win")}")
        pcHost = from
        pcPort = fromPort
        _state.value = PcState(
            Status.CONNECTED,
            pcName = msg.optString("win", "电脑"),
            pcAddress = from.hostAddress,
        )
        val provider = listProvider
        if (provider != null) {
            GlobalScope.launch(Dispatchers.IO) { pushAppList(provider) }
        } else {
            Log.w(TAG, "connected but listProvider is null")
        }
    }

    private fun onRequestList() {
        val provider = listProvider
        if (provider != null) {
            GlobalScope.launch(Dispatchers.IO) { pushAppList(provider) }
        }
    }

    private fun onFreezeResult(msg: JSONObject) {
        val pkg = msg.optString("pkg")
        val action = msg.optString("action")
        val ok = msg.optBoolean("ok", false)
        val m = msg.optString("msg")
        val deferred = pendingFreeze.remove("$pkg|$action") ?: return
        deferred.complete(if (ok) FreezeResult.Success else FreezeResult.Error(m.ifBlank { "电脑执行失败" }))
    }

    // ---------- 发送 ----------

    private fun sendToPc(json: String) {
        val host = pcHost ?: run {
            Log.w(TAG, "sendToPc skip: pcHost null")
            return
        }
        val s = socket ?: run {
            Log.w(TAG, "sendToPc skip: socket null")
            return
        }
        try {
            val data = json.toByteArray(Charsets.UTF_8)
            s.send(DatagramPacket(data, data.size, host, pcPort))
            Log.d(TAG, "sent ${json.take(60)} -> $host:$pcPort")
        } catch (e: Exception) {
            Log.w(TAG, "sendToPc fail", e)
            _state.value = PcState(Status.FAILED, message = "发送失败: ${e.message}")
        }
    }

    // ---------- 工具 ----------

    private val PALETTE = listOf(
        "#E57373", "#F06292", "#BA68C8", "#9575CD", "#7986CB",
        "#64B5F6", "#4FC3F7", "#4DB6AC", "#81C784", "#AED581",
        "#FFB74D", "#FF8A65", "#A1887F", "#90A4AE",
    )

    /** 按名称首字生成稳定的色块颜色，随列表推送给电脑端。 */
    fun colorForLabel(label: String): String {
        val code = label.firstOrNull()?.code ?: 0
        val idx = ((code % PALETTE.size) + PALETTE.size) % PALETTE.size
        return PALETTE[idx]
    }

    private fun phoneName(): String = Build.MODEL ?: "Android"

    private fun androidId(): String? {
        val cr = contentResolver ?: return null
        return runCatching {
            Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID)
        }.getOrNull()
    }
}
