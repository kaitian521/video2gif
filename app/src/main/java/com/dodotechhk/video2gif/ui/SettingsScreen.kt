package com.dodotechhk.video2gif.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.R

/** 隐私协议地址。 */
private const val PRIVACY_URL = "https://dodotechhk.com/gif_privacy"

/** 用户协议地址。 */
private const val TOS_URL = "https://dodotechhk.com/gif_tos"

/** 联系邮箱(暂定)。 */
private const val CONTACT_EMAIL = "ios@dodotechhk.com"

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
    // 版本号从 PackageInfo 读,免开 buildConfig。
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

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
    }
}
