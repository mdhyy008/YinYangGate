package cn.mdhyy.yinyanggate.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean,
    val isFrozen: Boolean,
)

/** 把应用图标 Drawable 渲染成封顶尺寸位图，在后台线程一次性转换，避免列表滑动时逐格在主线程创建位图。 */
fun drawableToBitmap(d: Drawable, maxSize: Int = 128): ImageBitmap {
    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else maxSize
    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else maxSize
    val scale = min(1f, maxSize.toFloat() / max(w, h))
    val bw = max(1, (w * scale).toInt())
    val bh = max(1, (h * scale).toInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, bw, bh)
    d.draw(canvas)
    return bmp.asImageBitmap()
}
