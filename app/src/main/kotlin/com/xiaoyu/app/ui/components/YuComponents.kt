package com.xiaoyu.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.xiaoyu.app.ui.illustrations.YuEmptyIllustration
import com.xiaoyu.app.ui.illustrations.YuIllustrationKind
import com.xiaoyu.app.ui.theme.LocalYuType
import com.xiaoyu.app.ui.theme.Tokens
import com.xiaoyu.app.ui.theme.yuSpring

enum class YuButtonVariant { PRIMARY, SECONDARY, TERTIARY }

@Composable
fun YuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: YuButtonVariant = YuButtonVariant.PRIMARY,
    content: @Composable RowScope.() -> Unit,
) {
    val height = when (variant) {
        YuButtonVariant.PRIMARY -> Tokens.SizeButtonPrimary
        YuButtonVariant.SECONDARY -> Tokens.SizeButtonSecondary
        YuButtonVariant.TERTIARY -> Tokens.SizeButtonTertiary
    }
    val buttonModifier = modifier.heightIn(min = Tokens.SizeMinTouch).height(height)
    when (variant) {
        YuButtonVariant.PRIMARY -> Button(onClick, buttonModifier, enabled, content = content)
        YuButtonVariant.SECONDARY -> OutlinedButton(onClick, buttonModifier, enabled, content = content)
        YuButtonVariant.TERTIARY -> TextButton(onClick, buttonModifier, enabled, content = content)
    }
}

@Composable
fun YuCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.RadiusCardLarge)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, tonalElevation = Tokens.ElevationCard, content = content)
    } else {
        Surface(modifier = modifier, shape = shape, tonalElevation = Tokens.ElevationCard, onClick = onClick, content = content)
    }
}

@Composable
fun YuDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.RadiusCardLarge),
    )
}

@Composable
fun YuSnackbar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Snackbar(
        modifier = modifier.heightIn(min = Tokens.SizeSnackbar),
        shape = RoundedCornerShape(Tokens.RadiusMd),
        content = content,
    )
}

@Composable
fun YuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = Tokens.SizeMinTouch),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
    )
}

data class YuNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun YuNavBar(items: List<YuNavItem>, modifier: Modifier = Modifier) {
    // 不写死总高：NavigationBar 会把 navigationBars inset 作为内层 padding，
    // 固定 64dp 在 3 键导航（48dp inset）下只剩十几 dp，图标文字会被裁掉。
    NavigationBar(
        modifier = modifier.heightIn(min = Tokens.SizeNavBar),
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = LocalYuType.current.meta, maxLines = 1) },
            )
        }
    }
}

@Composable
fun YuTimeDivider(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(text, style = LocalYuType.current.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun YuEmptyView(
    kind: YuIllustrationKind,
    message: String,
    modifier: Modifier = Modifier,
    contentDescription: String = message,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Tokens.SpacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.SpacingLg),
    ) {
        YuEmptyIllustration(kind = kind, contentDescription = contentDescription)
        Text(
            text = message,
            style = LocalYuType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun YuSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val trackColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        checked -> colors.primary
        else -> colors.surfaceVariant
    }
    val thumbColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        checked -> colors.onPrimary
        else -> colors.outline
    }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) Tokens.SpacingPage else Tokens.SpacingXs,
        animationSpec = yuSpring<Dp>(),
        label = "yuSwitchThumb",
    )
    Box(
        modifier = modifier
            .size(Tokens.SizeMinTouch)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Tokens.SwitchTrackWidth, Tokens.SwitchTrackHeight)
                .background(trackColor, CircleShape),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(Tokens.SwitchThumb)
                    .background(thumbColor, CircleShape),
            )
        }
    }
}
