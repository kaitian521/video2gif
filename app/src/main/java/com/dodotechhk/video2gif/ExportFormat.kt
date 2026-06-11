package com.dodotechhk.video2gif

/**
 * 导出格式(P9)。默认 **GIF**(app 的核心产物);mp4 为 Transformer 直出,
 * GIF/WebP 由 [FormatConverter] 对中间 mp4 跑 ffmpeg-kit 二次转码(技术方案 §5.2:
 * mp4 像素已最终化,FFmpeg 不 scale、只做 fps + 调色板/编码)。
 *
 * [isVideo] 决定相册落库位置:mp4 → Movies(Video 集合);GIF/WebP 是**动图**,
 * 归 Pictures(Images 集合)。
 */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String, val isVideo: Boolean) {
    Gif("GIF", "gif", "image/gif", isVideo = false),
    Mp4("mp4", "mp4", "video/mp4", isVideo = true),
    WebP("WebP", "webp", "image/webp", isVideo = false),
}
