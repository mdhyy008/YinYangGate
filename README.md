# 阴阳门 (YinYangGate)

Android 应用冻结 / 解冻管理工具。

## 简介

「阴阳门」是一款管理 Android 应用**冻结 / 解冻**状态的应用管理工具。设计灵感取自「阴阳」二元思想：未冻结的应用在**阳面**（明亮主题），已冻结的应用在**阴面**（暗色主题），通过 180° 翻转动画在两面之间切换，像翻动硬币一样管理应用。

冻结走系统「停用应用」机制（`pm disable-user`），不删除任何应用数据，可随时解冻恢复。适合隐藏应用、省电、防止孩子乱玩等场景。

## 功能特性

- 应用网格按「阳面 / 阴面」分组展示，180° 翻转动画切换，主题随面渐变
- 支持多种冻结通道：Root、Shizuku、DPM 设备管理员、ADB、PC 局域网中继
- 单个 / 批量冻结、解冻
- 操作记录页面，回溯每次冻结 / 解冻历史
- 设置页：权限模式、通道选择与偏好记忆、关于

## 技术栈

- Kotlin 2.2 + Jetpack Compose（Material3）
- Shizuku（`dev.rikka.shizuku`）
- kotlinx.coroutines
- Android Gradle Plugin + Gradle

## 系统要求

- Android 7.0（API 24）及以上
- 执行冻结操作需 Root / Shizuku / ADB / 设备管理员之一授权

## 构建

用 Android Studio 打开项目，或命令行构建：

```bash
./gradlew assembleDebug
```

## 相关文档

- [阴阳门软件说明书.md](阴阳门软件说明书.md) —— 界面与功能完整说明
- [项目总结.md](项目总结.md) —— 技术架构总结
