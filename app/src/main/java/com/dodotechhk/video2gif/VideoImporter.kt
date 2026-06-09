package com.dodotechhk.video2gif

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 视频导入器(实施计划 P1 / 技术方案 §2、§5.1)。
 *
 * 流程:
 * 1. `MediaMetadataRetriever` 取总时长,**≤500ms 直接拒绝**;
 * 2. best-effort 解析可读绝对路径(`_data`),并**验证** `exists() && canRead()`;
 * 3. 拿不到 / 验证不过 / 只有 `content://` → `openInputStream` **复制到 `cacheDir`** 兜底;
 * 4. 产出**确认可读**的 `sourceLocalPath`,后续 ffmpeg/导出只认它。
 *
 * 关键规则:**绝不把 `content://` 直接拼进 ffmpeg**(§5.1)。
 */
object VideoImporter {

    /** 源视频最小时长(ms),需 **>** 此值才接受。 */
    const val MIN_DURATION_MS = 500L

    sealed interface Result {
        /**
         * 导入成功:[localPath] 已确认 `exists() && canRead()`。
         * [displayWidth]/[displayHeight] 为**已应用旋转**的显示尺寸(90/270° 已交换宽高);
         * 读不到时回退 0,UI 用默认比例兜底。
         */
        data class Success(
            val uri: Uri,
            val localPath: String,
            val durationMs: Long,
            val displayWidth: Int,
            val displayHeight: Int,
        ) : Result
        /** 时长 ≤ [MIN_DURATION_MS],拒绝。 */
        data class TooShort(val durationMs: Long) : Result
        /** 其它失败(读不到时长 / 复制失败等)。 */
        data class Error(val message: String) : Result
    }

    private data class Meta(val durationMs: Long, val width: Int, val height: Int)

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val meta = readMeta(context, uri)
            ?: return@withContext Result.Error("无法读取视频时长")
        if (meta.durationMs <= MIN_DURATION_MS) {
            return@withContext Result.TooShort(meta.durationMs)
        }
        val localPath = try {
            resolveReadablePath(context, uri) ?: copyToCache(context, uri)
        } catch (e: Exception) {
            return@withContext Result.Error("复制视频到本地失败:${e.message}")
        }
        Result.Success(uri, localPath, meta.durationMs, meta.width, meta.height)
    }

    /** 一次性读时长 + 显示尺寸(旋转 90/270° 时交换宽高);读不到时长返回 null。 */
    private fun readMeta(context: Context, uri: Uri): Meta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            val w = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            // 90/270°:编码宽高与显示宽高互换。
            if (rotation == 90 || rotation == 270) Meta(duration, h, w) else Meta(duration, w, h)
        } catch (e: Exception) {
            null
        } finally {
            // minSdk 24:close()(AutoCloseable)自 API 29 才有,统一用 release()。
            retriever.release()
        }
    }

    /**
     * best-effort:尝试从 `_data` 列拿绝对路径,并**验证当前进程真能读**。
     * scoped storage / 照片选择器 URI 下通常拿不到 → 返回 null,交由 [copyToCache] 兜底。
     */
    private fun resolveReadablePath(context: Context, uri: Uri): String? {
        val path = try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val file = File(path)
        return if (file.exists() && file.canRead()) path else null
    }

    private fun copyToCache(context: Context, uri: Uri): String {
        val outFile = File(context.cacheDir, "imported_source.${extensionFor(context, uri)}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开输入流")
        input.use { source ->
            outFile.outputStream().use { sink -> source.copyTo(sink) }
        }
        return outFile.absolutePath
    }

    private fun extensionFor(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
    }
}
