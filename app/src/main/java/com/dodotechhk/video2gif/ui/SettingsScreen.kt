package com.dodotechhk.video2gif.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 隐私协议地址。 */
private const val PRIVACY_URL = "https://dodotechhk.com/gif_privacy"

/** 用户协议地址。 */
private const val TOS_URL = "https://dodotechhk.com/gif_tos"

/** 联系邮箱(暂定)。 */
private const val CONTACT_EMAIL = "ios@dodotechhk.com"

/** 可清理的缓存目录(导入副本/导出中间产物/ffmpeg 日志等都在这两处)。 */
private fun cacheDirs(context: Context): List<File> =
    listOfNotNull(context.cacheDir, context.externalCacheDir)

/** 目录递归总大小(byte)。 */
private fun dirSize(dir: File): Long =
    dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/** 清空目录内容(保留目录本身);失败的单个文件忽略。 */
private fun clearDir(dir: File) {
    dir.listFiles()?.forEach { it.deleteRecursively() }
}

/**
 * 设置页:隐私协议/用户协议(外链浏览器)、版本号(PackageInfo 读取,展示用)、
 * 联系我们(mailto)。从首页右上角设置按钮进入。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 版本号从 PackageInfo 读,免开 buildConfig。
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    // 当前缓存大小(进入页面时算一次,清理后刷新)。
    var cacheBytes by remember { mutableStateOf(cacheDirs(context).sumOf(::dirSize)) }
    var clearing by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏:返回 + 标题。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }

        // 隐私协议 → 浏览器打开。
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_privacy)) },
            supportingContent = { Text(PRIVACY_URL) },
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                }
            },
        )
        HorizontalDivider()

        // 用户协议 → 浏览器打开。
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_terms)) },
            supportingContent = { Text(TOS_URL) },
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TOS_URL)))
                }
            },
        )
        HorizontalDivider()

        // 版本号(纯展示)。
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(versionName) },
        )
        HorizontalDivider()

        // 联系我们 → 邮件客户端(无邮件 app 时静默忽略)。
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_contact)) },
            supportingContent = { Text(CONTACT_EMAIL) },
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
                    )
                }
            },
        )
        HorizontalDivider()

        // 清除缓存:cacheDir + externalCacheDir(导入副本/导出中间产物/ffmpeg 日志);
        // 设置页只能从首页进入(无进行中的编辑/导出会话),整目录清空安全。
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
            supportingContent = { Text(Formatter.formatFileSize(context, cacheBytes)) },
            modifier = Modifier.clickable(enabled = !clearing && cacheBytes > 0) {
                clearing = true
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        val before = cacheDirs(context).sumOf(::dirSize)
                        cacheDirs(context).forEach(::clearDir)
                        before - cacheDirs(context).sumOf(::dirSize)
                    }
                    cacheBytes = cacheDirs(context).sumOf(::dirSize)
                    clearing = false
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.toast_cache_cleared,
                            Formatter.formatFileSize(context, freed),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        HorizontalDivider()
    }
}
