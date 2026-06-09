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
 * - API 29+:`MediaStore` + `RELATIVE_PATH` + `IS_PENDING` 流程,**无需任何存储权限**;
 * - API <29:写入 `Movies/[DIR]` 真实路径并插入 `MediaStore`,需 `WRITE_EXTERNAL_STORAGE`。
 *
 * 当前由最小导出 harness 调用,验证产物相册可见;P10 正式接交付时复用。
 */
object MediaStoreSaver {

    /** 相册子目录(Movies 下)。 */
    private const val DIR = "Video2gif"

    /**
     * 把 [srcFile] 复制进系统相册,返回其 `content://` Uri;失败返回 null。
     * 在 IO 线程执行,可从主线程的协程直接 `await`。
     */
    suspend fun saveVideo(
        context: Context,
        srcFile: File,
        displayName: String = "video2gif_${System.currentTimeMillis()}.mp4",
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$DIR")
                // 日期元数据:部分图库据此把视频排进主时间线(否则只在「相册」里能找到)。
                put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
                put(MediaStore.Video.Media.DATE_ADDED, nowSec)
                put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection =
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { it.copyTo(out) }
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            // 旧版:写真实路径 + 插入(需 WRITE_EXTERNAL_STORAGE,见 manifest maxSdkVersion=28)。
            try {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    DIR,
                )
                moviesDir.mkdirs()
                val dest = File(moviesDir, displayName)
                srcFile.copyTo(dest, overwrite = true)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
                    put(MediaStore.Video.Media.DATE_ADDED, nowSec)
                    put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
                    @Suppress("DEPRECATION")
                    put(MediaStore.Video.Media.DATA, dest.absolutePath)
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            } catch (e: Exception) {
                null
            }
        }
    }
}
