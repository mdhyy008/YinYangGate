package cn.mdhyy.yinyanggate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.mdhyy.yinyanggate.channel.ChannelManager
import cn.mdhyy.yinyanggate.channel.ChannelStatus
import cn.mdhyy.yinyanggate.channel.ChannelType
import cn.mdhyy.yinyanggate.channel.PcClientManager
import cn.mdhyy.yinyanggate.data.AppRepository
import rikka.shizuku.Shizuku

@Composable
fun ChannelScreen(
    repo: AppRepository,
    swipeEnabled: Boolean,
    onSwipeEnabledChange: (Boolean) -> Unit,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    buttonBottom: Boolean,
    onButtonBottomChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val manager = repo.channelManager
    var statuses by remember { mutableStateOf<List<ChannelStatus>>(emptyList()) }
    var preferred by remember { mutableStateOf(manager.getPreferredChannel()) }
    var shizukuRunning by remember { mutableStateOf(Shizuku.pingBinder()) }
    var shizukuGranted by remember {
        mutableStateOf(shizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    }
    var shizukuInstalled by remember { mutableStateOf(isShizukuInstalled(context)) }
    var requestingType by remember { mutableStateOf<ChannelType?>(null) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }

    fun refresh() {
        statuses = manager.detect()
        preferred = manager.getPreferredChannel()
        shizukuInstalled = isShizukuInstalled(context)
        shizukuRunning = Shizuku.pingBinder()
        shizukuGranted = shizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    val pcState by PcClientManager.state.collectAsState()

    // 选中电脑客户端时自动查找电脑，结果写入通道描述。
    LaunchedEffect(preferred) {
        if (preferred == ChannelType.PC) PcClientManager.findPc()
    }

    fun requestPermission(status: ChannelStatus) {
        if (needsPermissionRequest(status)) {
            requestingType = status.type
        }
        requestChannelPermission(status, context, shizukuRunning, shizukuGranted, shizukuInstalled)
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            Handler(Looper.getMainLooper()).post {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    // 授权成功即自动选中 Shizuku，避免用户以为授权了就用它，实际还是旧通道。
                    manager.setPreferredChannel(ChannelType.SHIZUKU)
                    onToast("Shizuku 授权成功")
                } else {
                    onToast("Shizuku 授权被拒绝")
                }
                requestingType = null
                refresh()
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        refresh()
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    // 跳转到系统设置/Shizuku 应用等外部页面后返回时，重新检测权限状态并结束"申请中"。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && requestingType != null) {
                requestingType = null
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box {
        Scaffold(
            topBar = {
                SimpleTopBar(
                    title = "设置",
                    onBack = onBack,
                )
            },
        ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection("通用") {
                SettingCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("左右滑动切换面", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "在应用列表上左右滑动即可切换阳面/阴面",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = swipeEnabled, onCheckedChange = onSwipeEnabledChange)
                    }
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("显示系统应用", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "在应用列表上显示系统应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = showSystem, onCheckedChange = onShowSystemChange)
                    }
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("切换按钮显示在屏幕下方", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "开启后切换按钮移到屏幕底部，顶栏只显示标题",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = buttonBottom, onCheckedChange = onButtonBottomChange)
                    }
                }
            }

            SettingsSection("权限模式") {
                SettingCard {
                    statuses.forEach { status ->
                        if (status != statuses.first()) {
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                        }
                        ChannelRow(
                            status = status,
                            preferred = preferred == status.type,
                            requesting = requestingType == status.type,
                            pcState = pcState,
                            shizukuRunning = shizukuRunning,
                            shizukuGranted = shizukuGranted,
                            shizukuInstalled = shizukuInstalled,
                            onTogglePreferred = {
                                manager.setPreferredChannel(status.type)
                                refresh()
                                requestPermission(status)
                            },
                            onRequestPermission = { requestPermission(status) },
                            onToast = onToast,
                        )
                    }
                }
            }

            SettingsSection("关于") {
                SettingCard {
                    InfoRow(
                        icon = Icons.Outlined.Public,
                        title = "官网",
                        subtitle = "阴阳门官网 · 了解功能与下载更新",
                        onClick = { openUrl(context, OFFICIAL_URL) },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    InfoRow(
                        icon = Icons.Outlined.SmartToy,
                        title = "帮助机器人",
                        subtitle = "遇到问题？点击打开在线帮助",
                        onClick = {
                            if (HELP_URL.isBlank()) {
                                onToast("帮助链接待配置")
                            } else {
                                openUrl(context, HELP_URL)
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    InfoRow(
                        icon = Icons.Outlined.Info,
                        title = "关于阴阳门",
                        subtitle = "阴阳门 v$versionName · 应用冻结/解冻管理工具",
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ChannelRow(
    status: ChannelStatus,
    preferred: Boolean,
    requesting: Boolean,
    pcState: PcClientManager.PcState,
    shizukuRunning: Boolean,
    shizukuGranted: Boolean,
    shizukuInstalled: Boolean,
    onTogglePreferred: () -> Unit,
    onRequestPermission: () -> Unit,
    onToast: (String) -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTogglePreferred)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = channelIcon(status.type),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channelName(status.type),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Help,
                    contentDescription = "模式说明",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showHelp = true },
                )
            }
            val description = channelDescription(status, pcState, shizukuRunning, shizukuGranted, shizukuInstalled)
            val descText = when {
                requesting -> "申请中"
                preferred -> description
                else -> null
            }
            if (descText != null) {
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (preferred) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已指定",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        ChannelAction(
            status = status,
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            shizukuInstalled = shizukuInstalled,
            onRequestPermission = onRequestPermission,
        )
    }
    if (showHelp) {
        ChannelHelpDialog(
            type = status.type,
            onDismiss = { showHelp = false },
            onToast = onToast,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private const val OFFICIAL_URL = "https://mdhyy.cn/tools/page/YinYangGate/"
private const val HELP_URL = "https://help.web.mdhyy.cn/?doc=yinyang_help"
private const val DPM_COMMAND = "adb shell dpm set-device-owner cn.mdhyy.yinyanggate/.admin.AdminReceiver"

@Composable
private fun ChannelAction(
    status: ChannelStatus,
    shizukuRunning: Boolean,
    shizukuGranted: Boolean,
    shizukuInstalled: Boolean,
    onRequestPermission: () -> Unit,
) {
    val buttonText = when {
        status.type == ChannelType.SHIZUKU && shizukuRunning && !shizukuGranted -> "授权"
        status.type == ChannelType.SHIZUKU && !shizukuRunning && shizukuInstalled -> "打开 Shizuku"
        status.type == ChannelType.SHIZUKU && !shizukuInstalled -> "安装 Shizuku"
        status.type == ChannelType.ADB && !status.available -> "开启调试"
        else -> null
    }
    if (buttonText != null) {
        TextButton(onClick = onRequestPermission) { Text(buttonText) }
    }
}

private fun requestChannelPermission(
    status: ChannelStatus,
    context: Context,
    shizukuRunning: Boolean,
    shizukuGranted: Boolean,
    shizukuInstalled: Boolean,
) {
    when {
        status.available -> Unit
        status.type == ChannelType.SHIZUKU && shizukuRunning && !shizukuGranted ->
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        status.type == ChannelType.SHIZUKU && !shizukuRunning && shizukuInstalled ->
            openShizuku(context)
        status.type == ChannelType.SHIZUKU && !shizukuInstalled ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.xyz"))
                )
            }
        status.type == ChannelType.ADB && !status.available ->
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }
}

private fun channelIcon(type: ChannelType): ImageVector = when (type) {
    ChannelType.ROOT -> Icons.Outlined.LockOpen
    ChannelType.SHIZUKU -> Icons.Outlined.Security
    ChannelType.DPM -> Icons.Outlined.AdminPanelSettings
    ChannelType.ADB -> Icons.Outlined.PhoneAndroid
    ChannelType.PC -> Icons.Outlined.Computer
}

private fun channelName(type: ChannelType): String = when (type) {
    ChannelType.ROOT -> "Root 权限"
    ChannelType.SHIZUKU -> "Shizuku"
    ChannelType.DPM -> "设备所有者"
    ChannelType.ADB -> "手动 ADB 模式"
    ChannelType.PC -> "电脑客户端"
}

private fun channelDescription(
    status: ChannelStatus,
    pcState: PcClientManager.PcState,
    shizukuRunning: Boolean,
    shizukuGranted: Boolean,
    shizukuInstalled: Boolean,
): String = when (status.type) {
    ChannelType.ROOT -> if (status.available) "已就绪" else "未检测到 Root"
    ChannelType.SHIZUKU -> when {
        !shizukuInstalled -> "未安装"
        !shizukuRunning -> "未运行"
        !shizukuGranted -> "未授权"
        else -> "已授权"
    }
    ChannelType.DPM -> if (status.available) "已启用" else "未启用"
    ChannelType.ADB -> if (status.available) "已开启调试" else "未开启调试"
    ChannelType.PC -> when (pcState.status) {
        PcClientManager.Status.IDLE -> "未连接电脑"
        PcClientManager.Status.SEARCHING -> "正在查找电脑…"
        PcClientManager.Status.CONNECTED -> "已连接 ${pcState.pcName ?: ""}${pcState.pcAddress?.let { " ($it)" } ?: ""}".trim()
        PcClientManager.Status.FAILED -> pcState.message ?: "未找到电脑"
    }
}

/** 只有会实际发起权限请求的通道才需要"申请中"状态。 */
private fun needsPermissionRequest(status: ChannelStatus): Boolean =
    !status.available && when (status.type) {
        ChannelType.SHIZUKU, ChannelType.ADB -> true
        else -> false
    }

@Composable
private fun ChannelHelpDialog(
    type: ChannelType,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val (principle, usage) = channelHelp(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channelName(type)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "运行原理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(principle, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "使用方法",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(usage, style = MaterialTheme.typography.bodyMedium)
                if (type == ChannelType.DPM) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("DPM 命令", DPM_COMMAND))
                        onToast("命令已复制")
                    }) { Text("复制命令") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

private fun channelHelp(type: ChannelType): Pair<String, String> = when (type) {
    ChannelType.SHIZUKU ->
        "通过 Shizuku 服务借用 adb/root 权限，应用内直接执行 pm 命令冻结/解冻，无需 root。" to
            "1. 安装 Shizuku 并在其中启动服务\n2. 返回本应用点击 Shizuku 的「授权」\n3. 授权成功即自动选中本模式"
    ChannelType.ROOT ->
        "直接调用 su 以 root 身份执行 pm disable-user 冻结应用，权限最高。" to
            "1. 设备需已 root\n2. 弹出授权框时选择允许\n3. 选中本模式后即可操作"
    ChannelType.DPM ->
        "通过设备所有者（Device Owner）权限调用系统 API 隐藏应用，可静默冻结/解冻，无需每次授权。" to
            "1. 连接电脑，执行 $DPM_COMMAND（可点下方按钮复制）\n" +
            "2. 注意：设置过程会清空设备数据，门槛高，通常不建议\n" +
            "3. 设置成功后选中本模式即可静默操作，冻结/解冻不弹任何提示"
    ChannelType.ADB ->
        "手动模式：由用户自己到电脑上执行 adb 命令行完成冻结/解冻，本应用只负责记录与展示操作。" to
            "1. 手机开启 USB 调试并连接电脑\n" +
            "2. 在电脑上自行执行命令行，本应用不代执行：\n" +
            "   冻结：adb shell pm disable-user --user 0 <包名>\n" +
            "   解冻：adb shell pm enable <包名>\n" +
            "3. 执行完成后回到本应用点击「我已执行」，自动核对是否成功"
    ChannelType.PC ->
        "通过局域网与电脑端「阴阳门电脑端」通信，由电脑执行 adb 命令冻结/解冻，无需 root。" to
            "1. 手机开启 USB 调试，并用数据线（或无线调试）连接电脑\n" +
            "2. 电脑上运行「阴阳门电脑端」，确认显示「adb：已就绪」\n" +
            "3. 手机与电脑连接同一网络\n" +
            "4. 选中本模式，自动查找并连接电脑\n" +
            "5. 连接成功后，冻结/解冻由电脑端 adb 执行"
}

private fun isPackageInstalled(context: Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0)
    true
} catch (e: Exception) {
    false
}

// Shizuku 各版本/渠道的包名不同：官方为 moe.shizuku.xyz，部分分发为 moe.shizuku.privileged.api。
private fun isShizukuInstalled(context: Context): Boolean =
    listOf("moe.shizuku.privileged.api", "moe.shizuku.xyz").any { isPackageInstalled(context, it) }

private fun openShizuku(context: Context) {
    val pkg = if (isPackageInstalled(context, "moe.shizuku.privileged.api")) {
        "moe.shizuku.privileged.api"
    } else {
        "moe.shizuku.xyz"
    }
    runCatching {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            context.startActivity(it)
        }
    }
}

private const val SHIZUKU_REQUEST_CODE = 10086
