package cn.mdhyy.yinyanggate

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.mdhyy.yinyanggate.channel.ChannelManager
import cn.mdhyy.yinyanggate.channel.PcClientManager
import cn.mdhyy.yinyanggate.data.AppInfo
import cn.mdhyy.yinyanggate.data.AppRepository
import cn.mdhyy.yinyanggate.ui.ChannelScreen
import cn.mdhyy.yinyanggate.ui.HistoryScreen
import cn.mdhyy.yinyanggate.ui.YinYangMain
import cn.mdhyy.yinyanggate.ui.theme.YinYangGateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

private enum class Screen { MAIN, HISTORY, CHANNELS }

@Composable
fun App() {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val prefs = remember { context.getSharedPreferences(ChannelManager.PREFS_NAME, Context.MODE_PRIVATE) }

    var isDark by remember { mutableStateOf(prefs.getBoolean(KEY_FACE, false)) }
    var swipeEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_SWIPE, false)) }
    var showSystem by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_SYSTEM, false)) }
    var buttonBottom by remember { mutableStateOf(prefs.getBoolean(KEY_BUTTON_BOTTOM, false)) }
    var multiSelect by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var showExit by remember { mutableStateOf(false) }
    val activity = remember { context.findActivity() }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastSeq by remember { mutableStateOf(0) }
    val toast: (String) -> Unit = { msg ->
        toastMsg = msg
        toastSeq++
    }
    // 显示 1.8 秒后自动消失；toastSeq 递增保证连续相同文案也会重新计时。
    LaunchedEffect(toastSeq) {
        if (toastSeq > 0) {
            delay(1800)
            toastMsg = null
        }
    }

    fun refresh(after: suspend () -> Unit = {}) {
        scope.launch {
            apps = repo.loadApps()
            after()
        }
    }

    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()
        PcClientManager.init(context.applicationContext)
        PcClientManager.listProvider = { repo.loadApps() }
        Log.d(TAG, "init=${System.currentTimeMillis() - t0}ms")

        // 快照优先:先展示本地快照(图标来自缓存)快速上屏,再判断是否全量刷新。
        val t1 = System.currentTimeMillis()
        val snapshot = repo.loadSnapshot()
        Log.d(TAG, "snapshot=${System.currentTimeMillis() - t1}ms size=${snapshot?.size}")

        if (snapshot == null) {
            val t2 = System.currentTimeMillis()
            apps = repo.loadApps()
            Log.d(TAG, "loadApps=${System.currentTimeMillis() - t2}ms")
            loading = false
        } else {
            loading = false
            apps = snapshot
            if (repo.isSnapshotStale(snapshot)) {
                // 已安装集合变了:全量刷新并更新快照。
                val t2 = System.currentTimeMillis()
                apps = repo.loadApps()
                Log.d(TAG, "loadApps(stale)=${System.currentTimeMillis() - t2}ms")
            } else {
                // 集合没变:轻量校正冻结状态,不重载 label/icon。
                val t2 = System.currentTimeMillis()
                apps = repo.refreshFrozen(snapshot)
                Log.d(TAG, "refreshFrozen=${System.currentTimeMillis() - t2}ms")
                repo.saveSnapshotMetadata(apps)
            }
        }
        Log.d(TAG, "total=${System.currentTimeMillis() - t0}ms")
    }

    // 首次启动若系统权限弹窗出现较早，加载可能发生在授权前；回到前台且列表为空时自动重载。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && apps.isEmpty() && !loading) {
                loading = true
                scope.launch {
                    apps = repo.loadApps()
                    loading = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val flipProgress by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "flip",
    )

    YinYangGateTheme(flipProgress = flipProgress) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    // 历史页从左侧滑入、设置页从右侧滑入，返回主界面时反向。
                    val enterFrom = when (targetState) {
                        Screen.HISTORY -> -1
                        Screen.CHANNELS -> 1
                        Screen.MAIN -> if (initialState == Screen.HISTORY) 1 else -1
                    }
                    slideInHorizontally(tween(300)) { it * enterFrom } togetherWith
                        slideOutHorizontally(tween(300)) { -it * enterFrom }
                },
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.MAIN -> YinYangMain(
                        repo = repo,
                        apps = apps,
                        loading = loading,
                        isDark = isDark,
                        flipProgress = flipProgress,
                        swipeEnabled = swipeEnabled,
                        showSystem = showSystem,
                        buttonBottom = buttonBottom,
                        multiSelect = multiSelect,
                        onMultiSelectChange = { multiSelect = it },
                        onToggleDark = {
                            isDark = !isDark
                            prefs.edit().putBoolean(KEY_FACE, isDark).apply()
                        },
                        onRefresh = { after -> refresh(after) },
                        onOpenHistory = { screen = Screen.HISTORY },
                        onOpenChannels = { screen = Screen.CHANNELS },
                        onToast = toast,
                    )
                    Screen.HISTORY -> HistoryScreen(
                        repo = repo,
                        onBack = { screen = Screen.MAIN },
                        onToast = toast,
                    )
                    Screen.CHANNELS -> ChannelScreen(
                        repo = repo,
                        swipeEnabled = swipeEnabled,
                        onSwipeEnabledChange = { enabled ->
                            swipeEnabled = enabled
                            prefs.edit().putBoolean(KEY_SWIPE, enabled).apply()
                        },
                        showSystem = showSystem,
                        onShowSystemChange = {
                            showSystem = it
                            prefs.edit().putBoolean(KEY_SHOW_SYSTEM, it).apply()
                        },
                        buttonBottom = buttonBottom,
                        onButtonBottomChange = {
                            buttonBottom = it
                            prefs.edit().putBoolean(KEY_BUTTON_BOTTOM, it).apply()
                        },
                        onBack = { screen = Screen.MAIN },
                        onToast = toast,
                    )
                }
            }
            // 自绘顶部提示：显示在状态栏下方、水平居中，不遮挡底部按钮，且不受系统 Toast 位置限制。
            AnimatedVisibility(
                visible = toastMsg != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF000000),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = toastMsg ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
            if (showExit) {
                ExitConfirmDialog(
                    onExit = { activity?.finish() },
                    onDismiss = { showExit = false },
                )
            }
        }
        // 设置页/历史页按系统返回键回到主界面。
        BackHandler(enabled = screen != Screen.MAIN) {
            screen = Screen.MAIN
        }
        // 主界面按返回键 → 弹退出确认（多选时交给应用网格的返回键处理，避免冲突）。
        BackHandler(enabled = screen == Screen.MAIN && !multiSelect) {
            if (!showExit) showExit = true
        }
    }
}

private const val TAG = "YinYangPerf"
private const val KEY_FACE = "current_face_dark"
private const val KEY_SWIPE = "swipe_to_switch"
private const val KEY_SHOW_SYSTEM = "show_system_apps"
private const val KEY_BUTTON_BOTTOM = "switch_button_bottom"

@Composable
private fun ExitConfirmDialog(
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出应用") },
        text = { Text("确定要退出阴阳门吗？") },
        confirmButton = {
            TextButton(onClick = onExit) { Text("退出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
