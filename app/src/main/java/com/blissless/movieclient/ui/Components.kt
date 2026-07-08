package com.blissless.movieclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = ChizukiOnBgMuted,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) ChizukiAccent else ChizukiSurfaceVariant
    val fg = if (selected) Color.White else ChizukiOnBg
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = ChizukiOnBgFaint) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = ChizukiOnBgFaint) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(10.dp),
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ExtensionRow(
    label: String,
    packageName: String,
    versionName: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ChizukiSurfaceVariant else ChizukiSurface)
            .border(
                width = 1.dp,
                color = if (selected) ChizukiAccent else ChizukiOutline,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Extension,
            contentDescription = null,
            tint = if (selected) ChizukiAccent else ChizukiOnBgMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ChizukiOnBg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(packageName)
                    versionName?.let { append("  ·  v").append(it) }
                },
                color = ChizukiOnBgMuted,
                fontSize = 11.sp,
                fontFamily = MonoFamily,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = ChizukiAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SearchSuggestionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ChizukiSurfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = ChizukiOnBgMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) ChizukiAccent else ChizukiOnBg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = ChizukiOnBgMuted,
                fontSize = 11.sp,
                fontFamily = MonoFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LoadingInline(modifier: Modifier = Modifier, label: String = "Working…") {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp),
            color = ChizukiAccent,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = ChizukiOnBgMuted, fontSize = 12.sp)
    }
}

@Composable
fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = ChizukiOnBgMuted,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(32.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Bolt,
) {
    val bg = if (enabled) ChizukiAccent else ChizukiOutline
    val fg = if (enabled) Color.White else ChizukiOnBgFaint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = Color.White)
        } else {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun EmptyExtensionsHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChizukiSurfaceVariant)
            .border(1.dp, ChizukiOutline, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Error, contentDescription = null, tint = ChizukiWarn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("No extensions installed", color = ChizukiOnBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Install a Chizuki extension APK (e.g. vidlink-extension, 67movies-extension). " +
                "Its app label must start with \"Chizuki: \" and it must declare a receiver for " +
                "com.blissless.movieclient.EXTENSION_BEACON. Then tap the refresh icon in the toolbar.",
            color = ChizukiOnBgMuted,
            fontSize = 12.sp,
        )
    }
}
