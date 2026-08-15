package cn.mdhyy.yinyanggate.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mdhyy.yinyanggate.channel.ChannelType
import cn.mdhyy.yinyanggate.channel.FreezeResult
import cn.mdhyy.yinyanggate.data.AppInfo
import cn.mdhyy.yinyanggate.data.AppRepository
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun YinYangMain(
    repo: AppRepository,
    apps: List<AppInfo>,
    loading: Boolean,
    isDark: Boolean,
    flipProgress: Float,
    swipeEnabled: Boolean,
    showSystem: Boolean,
    buttonBottom: Boolean,
    multiSelect: Boolean,
    onMultiSelectChange: (Boolean) -> Unit,
    onToggleDark: () -> Unit,
    onRefresh: (suspend () -> Unit) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChannels: () -> Unit,
    onToast: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    // 搜索框暂时隐藏，如需恢复显示，将此处改为 true。
    var showSearchBar by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmApp by remember { mutableStateOf<AppInfo?>(null) }
    var adbTask by remember { mutableStateOf<AdbTask?>(null) }
    var busy by remember { mutableStateOf(false) }
    var processed by remember { mutableStateOf(0) }
    var menuVisible by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var batchRules by remember { mutableStateOf<List<RuleEntry>?>(null) }
    var batchAction by remember { mutableStateOf<BatchAction?>(null) }
    var verifyResults by remember { mutableStateOf<List<Pair<AppInfo, Boolean>>?>(null) }
    val menuProgress by animateFloatAsState(
        targetValue = if (menuVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 120f, // 介于 StiffnessVeryLow(50) 和 StiffnessLow(200) 之间
        ),
        label = "menuPop",
    )
    var hideButton by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val appContext = LocalContext.current

    fun openLibrary() {
        runCatching {
            appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LIBRARY_URL)))
        }.onFailure { onToast("无法打开网页") }
    }

    val base = apps.filter {
        (showSystem || !it.isSystemApp) &&
            (searchQuery.isBlank() ||
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true))
    }
    val lightApps = base.filter { !it.isFrozen }
    val darkApps = base.filter { it.isFrozen }

    fun runFreeze(targets: List<AppInfo>, onDone: () -> Unit = {}) {
        scope.launch {
            busy = true
            processed = 0
            val start = System.currentTimeMillis()
            val channel = repo.channelManager.getPreferredChannel()
            var ok = 0
            val cmds = mutableListOf<String>()
            for (app in targets) {
                processed++
                val targetFrozen = !app.isFrozen
                val r = if (app.isFrozen) repo.unfreezeApp(app) else repo.freezeApp(app)
                when (r) {
                    is FreezeResult.Success -> {
                        ok++
                        // PackageManager 状态可能延迟生效，轮询等待确认，避免进度秒退、图标迟迟不消失。
                        withContext(Dispatchers.IO) {
                            repeat(25) {
                                if (repo.isFrozen(app.packageName) == targetFrozen) return@withContext
                                delay(200)
                            }
                        }
                    }
                    is FreezeResult.NeedManual -> cmds.add(r.command)
                    is FreezeResult.Error -> onToast("失败：${r.message}")
                }
            }
            if (cmds.isNotEmpty()) {
                adbTask = AdbTask(targets, cmds)
            }
            if (ok > 0) {
                // 列表刷新完成后再收进度并提示，保证图标状态与完成提示同步。
                onRefresh {
                    val remain = 600 - (System.currentTimeMillis() - start)
                    if (remain > 0) delay(remain)
                    busy = false
                    onToast("已完成 $ok 个 · ${channelDisplayName(channel)}")
                    onDone()
                }
            } else {
                busy = false
                onDone()
            }
        }
    }

    fun runBatch(action: BatchAction) {
        val rules = batchRules ?: return
        scope.launch {
            busy = true
            val targetFrozen = action == BatchAction.FREEZE
            val channel = repo.channelManager.getActiveChannel()
            if (channel == null) {
                busy = false
                onToast("无可用通道，请先开启 root / Shizuku / ADB 之一")
                batchAction = null
                batchRules = null
                return@launch
            }
            var ok = 0
            val cmds = mutableListOf<String>()
            for (entry in rules) {
                val app = AppInfo(
                    packageName = entry.packageName,
                    label = entry.name,
                    icon = null,
                    isSystemApp = false,
                    isFrozen = repo.isFrozen(entry.packageName),
                )
                val r = if (targetFrozen) repo.freezeApp(app) else repo.unfreezeApp(app)
                when (r) {
                    is FreezeResult.Success -> ok++
                    is FreezeResult.NeedManual -> cmds.add(r.command)
                    is FreezeResult.Error -> onToast("${entry.name} 失败：${r.message}")
                }
            }
            batchAction = null
            batchRules = null
            busy = false
            if (cmds.isNotEmpty()) {
                adbTask = AdbTask(
                    rules.map { AppInfo(it.packageName, it.name, null, false, false) },
                    cmds,
                )
            }
            onToast("已完成 $ok/${rules.size} 个 · ${channelDisplayName(channel.type)}")
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MainContent(
            loading = loading,
            lightApps = lightApps,
            darkApps = darkApps,
            flipProgress = flipProgress,
            swipeEnabled = swipeEnabled,
            isDark = isDark,
            buttonBottom = buttonBottom,
            multiSelect = multiSelect,
            selected = selected,
            searchQuery = searchQuery,
            showSearchBar = showSearchBar,
            busy = busy,
            onSearchQueryChange = { searchQuery = it },
            onToggleDark = onToggleDark,
            onOpenMenu = { menuVisible = true },
            onOpenHistory = onOpenHistory,
            onOpenChannels = onOpenChannels,
            onFacePositionChanged = { menuAnchor = it.center },
            onAppClick = { app ->
                if (multiSelect) {
                    selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                } else if (repo.channelManager.getPreferredChannel() == ChannelType.ADB) {
                    // 电脑 ADB 调试：点击应用立即出命令引导，不做二次确认。
                    runFreeze(listOf(app))
                } else {
                    confirmApp = app
                }
            },
            onAppLongPress = { app ->
                onMultiSelectChange(true)
                selected = selected + app.packageName
            },
            onButtonHideChanged = { hideButton = it },
        )

        FaceSwitchOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonBottom = buttonBottom,
            multiSelect = multiSelect,
            hideButton = hideButton,
            isDark = isDark,
            faceCount = if (isDark) darkApps.size else lightApps.size,
            onToggleDark = onToggleDark,
            onOpenMenu = { menuVisible = true },
            onFacePositionChanged = { menuAnchor = it.center },
        )

        if (multiSelect) {
            BatchActionBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                count = selected.size,
                targets = apps.filter { it.packageName in selected },
                busy = busy,
                processed = processed,
                channelName = channelDisplayName(repo.channelManager.getPreferredChannel()),
                faceApps = if (isDark) darkApps else lightApps,
                canSelectAll = isDark,
                onExecute = { freeze ->
                    runFreeze(freeze) {
                        onMultiSelectChange(false)
                        selected = emptySet()
                    }
                },
                onSelectAll = { selected = darkApps.map { it.packageName }.toSet() },
                onDeselectAll = { selected = emptySet() },
                onExportRule = {
                    val arr = JSONArray()
                    apps.filter { it.packageName in selected }.forEach { app ->
                        arr.put(JSONObject().apply {
                            put("package", app.packageName)
                            put("name", app.label)
                        })
                    }
                    val json = JSONObject().apply {
                        put("type", "yinyang_rules")
                        put("version", 1)
                        put("apps", arr)
                    }.toString()
                    clipboard.setText(AnnotatedString(json))
                    Log.d("YinYang", "export rules: $json")
                    onToast("已导出 ${selected.size} 条应用规则到剪贴板")
                },
            )
        }

        MainDialogs(
            confirmApp = confirmApp,
            channelName = channelDisplayName(repo.channelManager.getPreferredChannel()),
            onConfirmFreeze = { app ->
                // 点击确认立即关弹窗,冻结在后台执行,顶部进度条提示进度。
                confirmApp = null
                runFreeze(listOf(app))
            },
            onDismissConfirm = { confirmApp = null },
            batchRules = batchRules,
            batchAction = batchAction,
            onBatchFreeze = { batchAction = BatchAction.FREEZE },
            onBatchUnfreeze = { batchAction = BatchAction.UNFREEZE },
            onDismissBatchRules = { batchRules = null },
            onConfirmBatch = { batchAction?.let { runBatch(it) } },
            onDismissBatchConfirm = { batchAction = null },
            adbTask = adbTask,
            onVerifyAdb = { task ->
                adbTask = null
                scope.launch {
                    busy = true
                    // 检测一次;若状态尚未同步,最多再快速重试 2 次,总耗时约 0.6 秒。
                    val results = withContext(Dispatchers.IO) {
                        task.apps.map { app ->
                            val targetFrozen = !app.isFrozen
                            var success = false
                            for (attempt in 1..3) {
                                if (repo.isFrozen(app.packageName) == targetFrozen) {
                                    success = true
                                    break
                                }
                                if (attempt < 3) delay(300)
                            }
                            app to success
                        }
                    }
                    busy = false
                    verifyResults = results
                    onRefresh {}
                }
            },
            onDismissAdb = { adbTask = null },
            verifyResults = verifyResults,
            onDismissVerify = { verifyResults = null },
            onToast = onToast,
        )

        FeatureMenu(
            apps = apps,
            visible = menuVisible,
            anchor = menuAnchor,
            progress = menuProgress,
            onDismiss = { menuVisible = false },
            onSelectApp = { app ->
                confirmApp = app
                menuVisible = false
            },
            onRuleLibrary = {
                val text = clipboard.getText()?.text?.toString()
                val rules = text?.let { parseRuleList(it) }
                if (rules.isNullOrEmpty()) {
                    onToast("剪贴板中没有有效清单，请先在多选里导出")
                } else {
                    batchRules = rules
                }
                menuVisible = false
            },
            onRefresh = {
                menuVisible = false
                onRefresh {
                    onToast("刷新完成")
                }
            },
            onLibrary = {
                menuVisible = false
                openLibrary()
            },
        )

        // 多选模式下系统返回键先退出多选，而不是直接退出应用。
        BackHandler(enabled = multiSelect) {
            onMultiSelectChange(false)
            selected = emptySet()
        }
    }
}

/** 主界面主体：顶栏 + 进度条 + 搜索 + 阴阳网格。拆分出来缩小每个组合函数的 JIT 编译单元。 */
@Composable
private fun MainContent(
    loading: Boolean,
    lightApps: List<AppInfo>,
    darkApps: List<AppInfo>,
    flipProgress: Float,
    swipeEnabled: Boolean,
    isDark: Boolean,
    buttonBottom: Boolean,
    multiSelect: Boolean,
    selected: Set<String>,
    searchQuery: String,
    showSearchBar: Boolean,
    busy: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleDark: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChannels: () -> Unit,
    onFacePositionChanged: (Rect) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onButtonHideChanged: (Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val dragThresholdPx = with(LocalDensity.current) {
        (configuration.screenWidthDp.dp * 0.2f).toPx()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        YinYangTopBar(
            isDark = isDark,
            faceCount = if (isDark) darkApps.size else lightApps.size,
            buttonBottom = buttonBottom,
            onToggleDark = onToggleDark,
            onOpenMenu = onOpenMenu,
            onOpenHistory = onOpenHistory,
            onOpenChannels = onOpenChannels,
            onFacePositionChanged = onFacePositionChanged,
        )
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
        if (showSearchBar) {
            SearchBarRow(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(swipeEnabled, dragThresholdPx) {
                    if (!swipeEnabled) return@pointerInput
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                    ) { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                        if (abs(dragTotal) > dragThresholdPx) {
                            dragTotal = 0f
                            onToggleDark()
                        }
                    }
                }
        ) {
            Crossfade(
                targetState = loading,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(400),
                label = "loading",
            ) { isLoading ->
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("正在加载应用…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    FaceGrid(
                        lightApps = lightApps,
                        darkApps = darkApps,
                        flipProgress = flipProgress,
                        multiSelect = multiSelect,
                        selected = selected,
                        onAppClick = onAppClick,
                        onAppLongPress = onAppLongPress,
                        onButtonHideChanged = onButtonHideChanged,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 阴阳两面的翻转网格：根据 flipProgress 只挂载可见的一面。 */
@Composable
private fun FaceGrid(
    lightApps: List<AppInfo>,
    darkApps: List<AppInfo>,
    flipProgress: Float,
    multiSelect: Boolean,
    selected: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onButtonHideChanged: (Boolean) -> Unit,
) {
    val lightVisible = flipProgress <= 90f
    val darkVisible = flipProgress >= 90f
    val camDist = with(LocalDensity.current) { 12.dp.toPx() }
    if (lightVisible) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = flipProgress
                    cameraDistance = camDist
                }
        ) {
            AppGrid(
                apps = lightApps,
                multiSelect = multiSelect,
                selected = selected,
                onAppClick = onAppClick,
                onAppLongPress = onAppLongPress,
                onButtonHideChanged = onButtonHideChanged,
            )
        }
    }
    if (darkVisible) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = flipProgress - 180f
                    cameraDistance = camDist
                }
        ) {
            AppGrid(
                apps = darkApps,
                multiSelect = multiSelect,
                selected = selected,
                onAppClick = onAppClick,
                onAppLongPress = onAppLongPress,
                onButtonHideChanged = onButtonHideChanged,
            )
        }
    }
}

/** 底部明暗切换按钮（buttonBottom 模式），滚动隐藏由 hideButton 控制。 */
@Composable
private fun FaceSwitchOverlay(
    modifier: Modifier = Modifier,
    buttonBottom: Boolean,
    multiSelect: Boolean,
    hideButton: Boolean,
    isDark: Boolean,
    faceCount: Int,
    onToggleDark: () -> Unit,
    onOpenMenu: () -> Unit,
    onFacePositionChanged: (Rect) -> Unit,
) {
    AnimatedVisibility(
        visible = buttonBottom && !multiSelect && !hideButton,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it },
    ) {
        // 阳面用深色阴影、阴面用灰色阴影，保证两个面都能看清按钮。
        val shadowColor = if (isDark) Color(0xCC808080) else Color(0x40000000)
        Surface(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 100.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            FaceSwitchButton(
                isDark = isDark,
                faceCount = faceCount,
                onClick = onToggleDark,
                onLongClick = onOpenMenu,
                onPositionChanged = onFacePositionChanged,
            )
        }
    }
}

/** 确认/批处理/ADB 引导/验证等全部弹窗的挂载点，集中一处便于重组范围收敛。 */
@Composable
private fun MainDialogs(
    confirmApp: AppInfo?,
    channelName: String,
    onConfirmFreeze: (AppInfo) -> Unit,
    onDismissConfirm: () -> Unit,
    batchRules: List<RuleEntry>?,
    batchAction: BatchAction?,
    onBatchFreeze: () -> Unit,
    onBatchUnfreeze: () -> Unit,
    onDismissBatchRules: () -> Unit,
    onConfirmBatch: () -> Unit,
    onDismissBatchConfirm: () -> Unit,
    adbTask: AdbTask?,
    onVerifyAdb: (AdbTask) -> Unit,
    onDismissAdb: () -> Unit,
    verifyResults: List<Pair<AppInfo, Boolean>>?,
    onDismissVerify: () -> Unit,
    onToast: (String) -> Unit,
) {
    confirmApp?.let { app ->
        FreezeConfirmDialog(
            app = app,
            channelName = channelName,
            onConfirm = { onConfirmFreeze(app) },
            onDismiss = onDismissConfirm,
        )
    }
    batchRules?.let { rules ->
        BatchRuleDialog(
            rules = rules,
            channelName = channelName,
            onFreeze = onBatchFreeze,
            onUnfreeze = onBatchUnfreeze,
            onDismiss = onDismissBatchRules,
        )
    }
    batchAction?.let { action ->
        BatchConfirmDialog(
            action = action,
            count = batchRules?.size ?: 0,
            channelName = channelName,
            onConfirm = onConfirmBatch,
            onDismiss = onDismissBatchConfirm,
        )
    }
    adbTask?.let { task ->
        AdbGuideDialog(
            task = task,
            onToast = onToast,
            onVerify = { onVerifyAdb(task) },
            onDismiss = onDismissAdb,
        )
    }
    verifyResults?.let { results ->
        VerifyResultDialog(
            results = results,
            onDismiss = onDismissVerify,
        )
    }
}

@Composable
private fun YinYangTopBar(
    isDark: Boolean,
    faceCount: Int,
    buttonBottom: Boolean,
    onToggleDark: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChannels: () -> Unit,
    onFacePositionChanged: (Rect) -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "操作记录",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            if (buttonBottom) {
                Text(
                    text = "阴阳门",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                FaceSwitchButton(
                    isDark = isDark,
                    faceCount = faceCount,
                    onClick = onToggleDark,
                    onLongClick = onOpenMenu,
                    onPositionChanged = onFacePositionChanged,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenChannels) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FaceSwitchButton(
    isDark: Boolean,
    faceCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPositionChanged: (Rect) -> Unit = {},
) {
    Row(
        Modifier
            .onGloballyPositioned { onPositionChanged(it.boundsInWindow()) }
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = "${if (isDark) "阴面" else "阳面"} · $faceCount",
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(150))
            },
            label = "faceLabel",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索应用") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(56.dp),
    )
}

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    multiSelect: Boolean,
    selected: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onButtonHideChanged: (Boolean) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // 向下滚动超过两行时隐藏切换按钮，向上滚动时立即显示。
    LaunchedEffect(gridState) {
        var last = gridState.firstVisibleItemIndex
        snapshotFlow { gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val delta = index - last
                last = index
                when {
                    delta < 0 -> onButtonHideChanged(false)
                    delta >= 2 -> onButtonHideChanged(true)
                }
            }
    }
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("该面暂无应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            Box(Modifier.fillMaxWidth()) {
                AppCard(
                    app = app,
                    multiSelect = multiSelect,
                    selected = app.packageName in selected,
                    onClick = { onAppClick(app) },
                    onLongPress = { onAppLongPress(app) },
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    app: AppInfo,
    multiSelect: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        multiSelect -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(2.dp, borderColor, cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app.icon, 44.dp)
            if (app.isSystemApp) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD32F2F))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, size: androidx.compose.ui.unit.Dp) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun FreezeConfirmDialog(
    app: AppInfo,
    channelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val freezing = !app.isFrozen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (freezing) "冻结 ${app.label}" else "解冻 ${app.label}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.icon, 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "将通过 $channelName ${if (freezing) "冻结" else "解冻"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (freezing)
                        "冻结后应用将从桌面消失、无法运行，可随时在阴面解冻恢复。"
                    else
                        "解冻后应用将恢复正常可用。"
                )
                if (freezing && app.isSystemApp) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "警告：该应用为系统应用，冻结可能导致系统异常或其它应用无法工作！",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (freezing) "确认冻结" else "确认解冻")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BatchActionBar(
    modifier: Modifier = Modifier,
    count: Int,
    targets: List<AppInfo>,
    busy: Boolean,
    processed: Int,
    channelName: String,
    faceApps: List<AppInfo>,
    canSelectAll: Boolean,
    onExecute: (List<AppInfo>) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExportRule: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val frozenCount = targets.count { it.isFrozen }
    val label = when {
        frozenCount == 0 -> "冻结 $count 个"
        frozenCount == count -> "解冻 $count 个"
        else -> "切换状态 $count 个"
    }
    val targetPkgs = targets.map { it.packageName }.toSet()
    // 当前面已全部选中时，全选按钮变为反选（点击取消全选）。
    val allFaceSelected = faceApps.isNotEmpty() && faceApps.all { it.packageName in targetPkgs }
    Surface(shadowElevation = 8.dp, modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                Text("正在处理 $processed/$count", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("处理中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // 仅暗面可全选；全选后同一按钮变为反选，导出清单保留在菜单里。
                if (canSelectAll) {
                    IconButton(
                        onClick = { if (allFaceSelected) onDeselectAll() else onSelectAll() },
                    ) {
                        Icon(
                            imageVector = if (allFaceSelected) Icons.Outlined.ClearAll else Icons.Outlined.SelectAll,
                            contentDescription = if (allFaceSelected) "反选当前面" else "全选当前面",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出应用清单规则") },
                            onClick = {
                                menuExpanded = false
                                onExportRule()
                            },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { confirming = true }) { Text(label) }
            }
        }
    }
    if (confirming) {
        BatchExecuteConfirmDialog(
            label = label,
            channelName = channelName,
            targets = targets,
            freezing = targets.any { !it.isFrozen },
            containsSystem = targets.any { it.isSystemApp },
            onConfirm = {
                confirming = false
                onExecute(targets)
            },
            onDismiss = { confirming = false },
        )
    }
}

/** 多选批量执行前的二次确认弹窗，展示本次涉及的应用清单。 */
@Composable
private fun BatchExecuteConfirmDialog(
    label: String,
    channelName: String,
    targets: List<AppInfo>,
    freezing: Boolean,
    containsSystem: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认批量操作") },
        text = {
            Column {
                Text("将通过 $channelName $label。")
                Spacer(Modifier.height(8.dp))
                Text("执行后应用将移入对应面，可随时再次操作恢复。")
                if (freezing && containsSystem) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "警告：清单中包含系统应用，冻结可能导致系统异常或其它应用无法工作！",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(targets, key = { it.packageName }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.icon, 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = if (app.isFrozen) "解冻" else "冻结",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (app.isFrozen) Color(0xFF1E88E5) else Color(0xFF43A047),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun FeatureMenu(
    apps: List<AppInfo>,
    visible: Boolean,
    anchor: Offset,
    progress: Float,
    onDismiss: () -> Unit,
    onSelectApp: (AppInfo) -> Unit,
    onRuleLibrary: () -> Unit,
    onRefresh: () -> Unit,
    onLibrary: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter {
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(500)),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            // 弹窗从按钮中心(anchor)飞向屏幕中心,scale + 位移配合弹簧回弹,模拟 Dock 弹出。
            val center = with(LocalDensity.current) {
                Offset(maxWidth.toPx() / 2f, maxHeight.toPx() / 2f)
            }
            val fromAnchor = anchor != Offset.Zero
            val tx = if (fromAnchor) (anchor.x - center.x) * (1f - progress) else 0f
            val ty = if (fromAnchor) (anchor.y - center.y) * (1f - progress) else 0f
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 480.dp)
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        scaleX = 0.3f + 0.7f * progress
                        scaleY = 0.3f + 0.7f * progress
                        translationX = tx
                        translationY = ty
                    },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "功能菜单",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索应用") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            if (filtered.isEmpty()) {
                                item {
                                    Text("无匹配应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(filtered, key = { it.packageName }) { app ->
                                MenuAppRow(app = app, onClick = { onSelectApp(app) })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MenuGridItem(
                            icon = Icons.AutoMirrored.Outlined.Rule,
                            label = "清单批处理",
                            onClick = onRuleLibrary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        MenuGridItem(
                            icon = Icons.Outlined.MenuBook,
                            label = "清单库",
                            onClick = onLibrary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        MenuGridItem(
                            icon = Icons.Outlined.Refresh,
                            label = "手动刷新",
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun MenuGridItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MenuAppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (app.isFrozen) "已冻结" else "正常",
                style = MaterialTheme.typography.labelSmall,
                color = if (app.isFrozen) Color(0xFF1E88E5) else Color(0xFF43A047),
            )
        }
    }
}

/** 清单库网页地址（长按阴阳面按钮弹窗中的「清单库」入口）。 */
private const val LIBRARY_URL = "https://mdhyy.cn/tools/page/YinYangGate/lists.html"

private fun channelDisplayName(type: ChannelType): String = when (type) {
    ChannelType.SHIZUKU -> "Shizuku"
    ChannelType.ROOT -> "Root"
    ChannelType.DPM -> "设备所有者"
    ChannelType.ADB -> "ADB"
    ChannelType.PC -> "电脑客户端"
}

data class AdbTask(
    val apps: List<AppInfo>,
    val commands: List<String>,
)

data class RuleEntry(
    val packageName: String,
    val name: String,
)

private enum class BatchAction { FREEZE, UNFREEZE }

/** 解析剪贴板里的 yinyang_rules 清单;格式不符返回 null。 */
private fun parseRuleList(text: String): List<RuleEntry>? = runCatching {
    val obj = JSONObject(text)
    if (obj.optString("type") != "yinyang_rules") return@runCatching null
    val arr = obj.getJSONArray("apps")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        RuleEntry(o.getString("package"), o.optString("name", o.getString("package")))
    }
}.getOrNull()

@Composable
private fun AdbGuideDialog(
    task: AdbTask,
    onToast: (String) -> Unit,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADB 手动执行") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("请在电脑上执行以下命令（已开启 USB 调试）：")
                Spacer(Modifier.height(8.dp))
                for (cmd in task.commands) {
                    // 固定深色代码块背景，亮/暗主题下命令都清晰可见。
                    Surface(
                        color = Color(0xFF263238),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cmd,
                                fontSize = 12.sp,
                                color = Color(0xFFECEFF1),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(cmd))
                                onToast("已复制该命令")
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "复制该命令",
                                    tint = Color(0xFF90A4AE),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(task.commands.joinToString("\n")))
                        onToast("已复制全部命令")
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("复制全部") }
                Spacer(Modifier.height(8.dp))
                Text("执行完成后回到应用，点击「我已执行」验证。")
            }
        },
        confirmButton = {
            TextButton(onClick = onVerify) { Text("我已执行") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun BatchRuleDialog(
    rules: List<RuleEntry>,
    channelName: String,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清单批处理") },
        text = {
            Column {
                Text(
                    text = "当前模式：$channelName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("共 ${rules.size} 个应用：", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(rules) { entry ->
                        Text(
                            text = "${entry.name} · ${entry.packageName}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onFreeze,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("冻结") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onUnfreeze) { Text("解冻") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun VerifyResultDialog(
    results: List<Pair<AppInfo, Boolean>>,
    onDismiss: () -> Unit,
) {
    val okCount = results.count { it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("执行结果检测") },
        text = {
            Column {
                Text("成功 $okCount/${results.size} 个：")
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(results, key = { it.first.packageName }) { (app, success) ->
                        Text(
                            text = "${app.label} · ${if (success) "已被冻结" else "未冻结"}",
                            color = if (success) Color(0xFF1565C0) else Color(0xFFD32F2F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

@Composable
private fun BatchConfirmDialog(
    action: BatchAction,
    count: Int,
    channelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val freezing = action == BatchAction.FREEZE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (freezing) "确认冻结" else "确认解冻") },
        text = {
            Column {
                Text("将通过 $channelName ${if (freezing) "冻结" else "解冻"} $count 个应用。")
                Spacer(Modifier.height(8.dp))
                Text(
                    if (freezing)
                        "冻结后应用将从桌面消失、无法运行，可随时在阴面解冻恢复。"
                    else
                        "解冻后应用将恢复正常可用。"
                )
                if (freezing) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "警告：若清单中包含系统应用，冻结可能导致系统异常或其它应用无法工作！",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (freezing) "确认冻结" else "确认解冻") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
