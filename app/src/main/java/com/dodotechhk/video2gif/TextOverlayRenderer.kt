package com.dodotechhk.video2gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/**
 * 文字贴字渲染器(P13)。**预览与导出共用此函数生成的 bitmap** —— 字体渲染 100% 同源,
 * WYSIWYG 真值唯一。
 *
 * - **紧贴矩形框**:bitmap 按实际行宽/行高裁紧(仅留描边 padding),文字占满自身矩形;
 * - **不溢出**:超出 [maxWidthPx] 自动换行;整体超出 [maxHeightPx] 时自动降字号直到放得下;
 * - meme 风格:可选加粗 + 描边**自动反差色**(浅色字黑边、深色字白边),保证任何画面可读。
 */
object TextOverlayRenderer {

    /** 字数上限(防超长文本撑爆 bitmap)。 */
    const val MAX_CHARS = 100

    /** 基准字号 = 窗口高 × 此比例(scale = 1)。 */
    const val BASE_FONT_FRAC = 0.10f

    /** 基准换行宽 = 窗口宽 × 此比例(scale = 1)。 */
    const val BASE_WRAP_FRAC = 0.95f

    /** 字号下限(px),降字号自适应的终点。 */
    private const val MIN_TEXT_SIZE_PX = 8f

    /** 描边自动反差色:浅色填充配黑边,深色填充配白边。 */
    fun strokeColorFor(fillColor: Int): Int {
        val luminance = (0.299f * Color.red(fillColor) +
            0.587f * Color.green(fillColor) +
            0.114f * Color.blue(fillColor)) / 255f
        return if (luminance > 0.5f) Color.BLACK else Color.WHITE
    }

    /**
     * 等比渲染(P13 矩形框比例恒定的关键):
     * 1. 先求 **基准字号**(scale=1):从 `BASE_FONT_FRAC×winH` 起,放不进窗口
     *    (`BASE_WRAP_FRAC×winW` 宽 / 0.9×winH 高)就逐步降——只在内容/窗口变化时发生;
     * 2. 实际渲染用 `字号 = 基准×scale`、`换行宽 = 基准换行宽×scale`:**断点不随 scale 变**,
     *    框宽高严格 ∝ scale(比例恒定),字形按目标尺寸排版(放大不糊)。
     *
     * 空白文本或非法尺寸返回 null。缩放上限由调用方按窗口夹紧(不溢出)。
     */
    fun renderScaled(
        content: String,
        fillColor: Int,
        strokeColor: Int,
        bold: Boolean,
        winWPx: Int,
        winHPx: Int,
        scale: Float,
    ): Bitmap? {
        val text = content.trim().take(MAX_CHARS)
        if (text.isEmpty() || winWPx <= 0 || winHPx <= 0 || scale <= 0f) return null

        val baseWrap = (winWPx * BASE_WRAP_FRAC).toInt().coerceAtLeast(8)
        val baseFont = fitBaseFontPx(text, bold, baseWrap, (winHPx * 0.9f).toInt())
        return renderAt(text, fillColor, strokeColor, bold, baseFont * scale, ceil(baseWrap * scale).toInt())
    }

    /** scale=1 的基准字号:布局级测量,高度放不进 [maxHeightPx] 就降(纯函数、确定性)。 */
    private fun fitBaseFontPx(text: String, bold: Boolean, wrapPx: Int, maxHeightPx: Int): Float {
        // 初值按窗口高比例(maxHeight = 0.9×winH → winH = maxHeight/0.9)。
        var font = (maxHeightPx / 0.9f) * BASE_FONT_FRAC
        while (font > MIN_TEXT_SIZE_PX) {
            val pad = ceil(font * 0.08f).toInt()
            val layout = makeLayout(text, paintFor(bold, font), (wrapPx - 2 * pad).coerceAtLeast(1))
            if (layout.height + 2 * pad <= maxHeightPx) return font
            font *= 0.9f
        }
        return MIN_TEXT_SIZE_PX
    }

    /** 按给定字号/换行宽渲染紧贴 bitmap(无上限检查,夹紧由调用方负责)。 */
    private fun renderAt(
        text: String,
        fillColor: Int,
        strokeColor: Int,
        bold: Boolean,
        textSize: Float,
        wrapTotalPx: Int,
    ): Bitmap? {
        val strokeWidth = textSize * 0.08f
        val pad = ceil(strokeWidth).toInt()
        val wrapWidth = (wrapTotalPx - 2 * pad).coerceAtLeast(1)

        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
            color = fillColor
        }
        val fillLayout = makeLayout(text, fillPaint, wrapWidth)

        // 紧贴宽度 = 实际最宽行(layout 宽是换行上限,不是实际占宽)。
        val tightWidth = ceil(
            (0 until fillLayout.lineCount).maxOf { fillLayout.getLineWidth(it) }
        ).toInt().coerceAtLeast(1)
        val bmpW = tightWidth + 2 * pad
        val bmpH = fillLayout.height + 2 * pad

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 行在 wrapWidth 内居中,平移到紧贴坐标系。
        canvas.translate(pad - (wrapWidth - tightWidth) / 2f, pad.toFloat())

        val strokePaint = TextPaint(fillPaint).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            color = strokeColor
        }
        makeLayout(text, strokePaint, wrapWidth).draw(canvas)
        fillLayout.draw(canvas)
        return bitmap
    }

    private fun paintFor(bold: Boolean, size: Float): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun makeLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
}
