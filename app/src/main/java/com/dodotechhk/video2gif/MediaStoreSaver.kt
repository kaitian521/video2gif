package com.dodotechhk.video2gif

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把产物发布到系统相册(实施计划 P10 雏形 / 技术方案 §5.1 存储)。
 *
 * - mp4 → Video 集合(`Movies/Video2gif`);GIF/WebP 动图 → Images 集合(`Pictures/Video2gif`)。
 * - API 29+:`MediaStore` + `RELATIVE_PATH` + `IS_PENDING` 流程,**无需任何存储权限**;
 * - API <29:写公共目录真实路径并插入 `MediaStore`,需 `WRITE_EXTERNAL_STORAGE`。
 */
object MediaStoreSaver {

    /** 相册子目录(Movies/Pictures 下)。 */
    private const val DIR = "Video2gif"

    /**
     * 把 [srcFile] 按 [format] 复制进系统相册,返回其 `content://` Uri;失败返回 null。
     * 在 IO 线程执行,可从主线程的协程直接调用。
     */
    suspend fun save(
        context: Context,
        srcFile: File,
        format: ExportFormat,
        displayName: String = "video2gif_${System.currentTimeMillis()}.${format.extension}",
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000
        // 视频走 Video/Movies,动图走 Images/Pictures。
        val topDir = if (format.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (format.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            // 列名常量 Video/Images 同源(MediaColumns),统一用 MediaColumns。
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$topDir/$DIR")
                // 日期元数据:部分图库据此把产物排进主时间线(否则只在「相册」里能找到)。
                put(MediaStore.MediaColumns.DATE_TAKEN, nowMs)
                put(MediaStore.MediaColumns.DATE_ADDED, nowSec)
                put(MediaStore.MediaColumns.DATE_MODIFIED, nowSec)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { it.copyTo(out) }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            // 旧版:写真实路径 + 插入(需 WRITE_EXTERNAL_STORAGE,见 manifest maxSdkVersion=28)。
            try {
                val destDir = File(Environment.getExternalStoragePublicDirectory(topDir), DIR)
                destDir.mkdirs()
                val dest = File(destDir, displayName)
                srcFile.copyTo(dest, overwrite = true)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    // DATE_TAKEN 在 MediaColumns 上是 API 29+ 常量;<29 用各集合自己的(同名列)。
                    if (format.isVideo) {
                        put(MediaStore.Video.VideoColumns.DATE_TAKEN, nowMs)
                    } else {
                        put(MediaStore.Images.ImageColumns.DATE_TAKEN, nowMs)
                    }
                    put(MediaStore.MediaColumns.DATE_ADDED, nowSec)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, nowSec)
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                }
                val collection = if (format.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                resolver.insert(collection, values)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 兼容旧调用:存 mp4。 */
    suspend fun saveVideo(
        context: Context,
        srcFile: File,
        displayName: String = "video2gif_${System.currentTimeMillis()}.mp4",
    ): Uri? = save(context, srcFile, ExportFormat.Mp4, displayName)
}
