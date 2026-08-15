package cn.mdhyy.yinyanggate.data

/** 一条操作日志：冻结或解冻某个应用。 */
data class FreezeRecord(
    val packageName: String,
    val label: String,
    val timestamp: Long,
    val channel: String,
    val action: String, // "冻结" / "解冻"
)
