package com.dodotechhk.video2gif

/**
 * 一条文字贴字(P13)。支持多条([EditState.texts]),每条独立内容/颜色/描边/加粗/缩放/位置。
 *
 * - [scale]:相对基准布局的等比缩放(断点/框比例不变),夹在 [TEXT_SCALE_MIN, TEXT_SCALE_MAX];
 * - [posX]/[posY]:文字中心在**裁切后输出窗口**的相对位置(0..1,x 右 y 下),整框夹紧不溢出;
 * - [strokeColor]:描边色,**独立可配**(新增时默认 [TextOverlayRenderer.strokeColorFor] 反差色)。
 */
data class TextItem(
    val id: Long,
    val content: String,
    val fillColor: Int = android.graphics.Color.WHITE,
    val strokeColor: Int = android.graphics.Color.BLACK,
    val bold: Boolean = false,
    val scale: Float = 1f,
    val posX: Float = 0.5f,
    val posY: Float = 0.85f,
    /** 旋转角(度,顺时针,绕文字中心);夹紧用旋转后 AABB。 */
    val rotation: Float = 0f,
)

/** 半宽/半高经旋转后的 AABB 半径(夹紧用):|w·cosθ|+|h·sinθ| / |w·sinθ|+|h·cosθ|。 */
fun rotatedHalfExtents(halfW: Float, halfH: Float, degrees: Float): Pair<Float, Float> {
    val r = Math.toRadians(degrees.toDouble())
    val c = kotlin.math.abs(kotlin.math.cos(r)).toFloat()
    val s = kotlin.math.abs(kotlin.math.sin(r)).toFloat()
    return halfW * c + halfH * s to halfW * s + halfH * c
}
