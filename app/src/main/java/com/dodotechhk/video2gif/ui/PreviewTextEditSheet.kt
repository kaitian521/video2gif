package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.R
import com.dodotechhk.video2gif.TextItem
import com.dodotechhk.video2gif.TextOverlayRenderer

/** P13 文字调色板(圆点,横向可滑);描边自动反差色。 */
private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50,
    0xFF00BCD4, 0xFF2196F3, 0xFF3F51B5, 0xFF9C27B0, 0xFFE91E63, 0xFF795548, 0xFF9E9E9E,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewTextEditSheet(
    state: EditState,
    editingTextId: Long?,
    selectedTextId: Long?,
    onSelectedTextIdChange: (Long?) -> Unit,
    onStateChange: (EditState) -> Unit,
    onDismiss: () -> Unit,
    onRequestDelete: (Long) -> Unit,
) {
    val editing = state.texts.find { it.id == editingTextId }
    if (editing == null) {
        // 条目已不存在(被删),直接收起。
        LaunchedEffect(editingTextId) { onDismiss() }
        return
    }

    // 编辑目标字段实时写回 state。
    val updateEditing: ((TextItem) -> TextItem) -> Unit = { f ->
        onStateChange(
            state.copy(texts = state.texts.map { if (it.id == editing.id) f(it) else it })
        )
    }
    // 收起:空内容条目直接移除(新增后没输入也不留垃圾)。
    val dismissSheet = {
        onDismiss()
        if (editing.content.isBlank()) {
            if (selectedTextId == editing.id) onSelectedTextIdChange(null)
            onStateChange(state.copy(texts = state.texts.filterNot { it.id == editing.id }))
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissSheet() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 透明遮罩:上方预览保持原亮度,实时看编辑效果。
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = editing.content,
                onValueChange = { v ->
                    updateEditing { it.copy(content = v.take(TextOverlayRenderer.MAX_CHARS)) }
                },
                placeholder = { Text(stringResource(R.string.text_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoldToggleButton(
                    selected = editing.bold,
                    onClick = { updateEditing { it.copy(bold = !it.bold) } },
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { onRequestDelete(editing.id) }) {
                    Text(stringResource(R.string.text_remove))
                }
                Button(onClick = { dismissSheet() }) { Text(stringResource(R.string.ok)) }
            }
            Text(stringResource(R.string.text_color), style = MaterialTheme.typography.labelMedium)
            ColorDots(editing.fillColor) { c ->
                updateEditing {
                    // 描边未单独改过(仍是旧填充的反差色)时,跟随新反差色。
                    val follows = it.strokeColor == TextOverlayRenderer.strokeColorFor(it.fillColor)
                    it.copy(
                        fillColor = c,
                        strokeColor = if (follows) {
                            TextOverlayRenderer.strokeColorFor(c)
                        } else {
                            it.strokeColor
                        },
                    )
                }
            }
            Text(stringResource(R.string.text_outline), style = MaterialTheme.typography.labelMedium)
            ColorDots(editing.strokeColor) { c -> updateEditing { it.copy(strokeColor = c) } }
        }
    }
}

@Composable
private fun BoldToggleButton(selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.text_bold)
    val shape = RoundedCornerShape(10.dp)
    val background =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val foreground =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(background)
            .border(1.5.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "B",
            color = foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ColorDots(selected: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TEXT_COLORS.forEach { c ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (selected == c) 3.dp else 1.dp,
                        color = if (selected == c) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        },
                        shape = CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}
