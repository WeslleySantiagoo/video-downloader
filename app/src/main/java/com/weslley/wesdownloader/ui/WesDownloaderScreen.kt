package com.weslley.wesdownloader.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.weslley.wesdownloader.data.DownloadEntity
import com.weslley.wesdownloader.domain.DownloadStatus
import com.weslley.wesdownloader.domain.MediaMode
import com.weslley.wesdownloader.domain.QualityOption
import com.weslley.wesdownloader.ui.theme.Forest
import com.weslley.wesdownloader.ui.theme.Lime
import com.weslley.wesdownloader.ui.theme.Night
import com.weslley.wesdownloader.ui.theme.SurfaceHigh
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WesDownloaderScreen(
    viewModel: AppViewModel,
    onOpen: (Uri, MediaMode) -> Unit,
    onShare: (Uri, MediaMode) -> Unit,
) {
    val url by viewModel.url.collectAsStateWithLifecycle()
    val media by viewModel.inspection.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val selected by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val inspecting by viewModel.isInspecting.collectAsStateWithLifecycle()
    val updating by viewModel.isUpdating.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Night,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Header(
                        updating = updating,
                        onReset = viewModel::reset,
                        onUpdate = viewModel::updateEngine,
                    )
                }
                item { Hero() }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Link do YouTube", fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = url,
                                onValueChange = viewModel::setUrl,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !inspecting,
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.setUrl(clipboard.getText()?.text.orEmpty())
                                    }) { Icon(Icons.Rounded.ContentPaste, "Colar link") }
                                },
                                placeholder = { Text("youtube.com/watch?v=...") },
                                shape = RoundedCornerShape(18.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { viewModel.inspect() }),
                            )
                            Button(
                                onClick = viewModel::inspect,
                                enabled = url.isNotBlank() && !inspecting,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                if (inspecting) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Analisando...")
                                } else {
                                    Text(if (media == null) "Analisar video" else "Analisar novamente")
                                }
                            }

                            AnimatedVisibility(media != null) {
                                media?.let { inspection ->
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        HorizontalDivider(color = Color.White.copy(alpha = .08f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            AsyncImage(
                                                model = inspection.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(width = 128.dp, height = 76.dp).clip(RoundedCornerShape(14.dp)),
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(inspection.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Spacer(Modifier.height(6.dp))
                                                Text(formatDuration(inspection.durationSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                            }
                                        }
                                        ModeSelector(mode, viewModel::setMode)
                                        val options = if (mode == MediaMode.VIDEO) inspection.videoOptions else inspection.audioOptions
                                        QualitySelector(options, selected, viewModel::selectQuality)
                                        Button(
                                            onClick = viewModel::startDownload,
                                            enabled = selected != null,
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            shape = RoundedCornerShape(17.dp),
                                        ) {
                                            Icon(Icons.Rounded.Download, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Baixar no aparelho", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (downloads.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, null, tint = Lime)
                            Spacer(Modifier.width(8.dp))
                            Text("Historico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(downloads, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            onCancel = { viewModel.cancel(item) },
                            onResume = { viewModel.resume(item) },
                            onRemove = { viewModel.remove(item) },
                            onOpen = { item.outputUri?.let { onOpen(Uri.parse(it), item.mode) } },
                            onShare = { item.outputUri?.let { onShare(Uri.parse(it), item.mode) } },
                        )
                    }
                }
                item {
                    Text(
                        "Os arquivos sao processados neste aparelho e salvos em Downloads/WesDownloader.",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(updating: Boolean, onReset: () -> Unit, onUpdate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable(onClick = onReset), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Lime, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Download, null, tint = Night)
            }
            Spacer(Modifier.width(10.dp))
            Text("WesDownloader", fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onUpdate, enabled = !updating) {
            if (updating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Update, "Atualizar mecanismo", tint = Forest)
        }
    }
}

@Composable
private fun Hero() {
    Column {
        Surface(color = Lime, contentColor = Night, shape = CircleShape) {
            Text("LOCAL, PRIVADO E DIRETO", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        Text("Sua midia,\nno seu aparelho.", fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text("Escolha video ou audio e continue usando o celular enquanto o download acontece.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ModeSelector(mode: MediaMode, onMode: (MediaMode) -> Unit) {
    Row(Modifier.fillMaxWidth().background(SurfaceHigh, RoundedCornerShape(16.dp)).padding(4.dp)) {
        ModeButton("Video", Icons.Rounded.VideoLibrary, mode == MediaMode.VIDEO, Modifier.weight(1f)) { onMode(MediaMode.VIDEO) }
        ModeButton("Audio", Icons.Rounded.AudioFile, mode == MediaMode.AUDIO, Modifier.weight(1f)) { onMode(MediaMode.AUDIO) }
    }
}

@Composable
private fun ModeButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Lime else Color.Transparent,
        contentColor = if (selected) Night else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(13.dp),
    ) {
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualitySelector(options: List<QualityOption>, selected: QualityOption?, onSelect: (QualityOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Qualidade", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val active = selected?.id == option.id
                Surface(
                    modifier = Modifier.clickable { onSelect(option) },
                    color = if (active) Lime.copy(alpha = .16f) else SurfaceHigh,
                    contentColor = if (active) Lime else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Lime) else null,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(option.label, fontWeight = FontWeight.Bold)
                        Text("${option.container.uppercase()}${formatBytes(option.estimatedBytes)?.let { " · $it" }.orEmpty()}", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                StatusIcon(item.status)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${if (item.mode == MediaMode.VIDEO) "Video" else "Audio"} · ${item.qualityLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (item.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING)) {
                LinearProgressIndicator({ item.progress / 100f }, Modifier.fillMaxWidth().clip(CircleShape), color = Lime)
                Row(Modifier.fillMaxWidth()) {
                    Text(item.stage, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${item.progress}%", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text(item.errorMessage ?: item.stage, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (item.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING -> ActionButton("Cancelar", Icons.Rounded.Cancel, onCancel)
                    DownloadStatus.INTERRUPTED, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> ActionButton("Retomar", Icons.Rounded.Refresh, onResume)
                    DownloadStatus.COMPLETED -> {
                        ActionButton("Abrir", Icons.Rounded.OpenInNew, onOpen)
                        ActionButton("Compartilhar", Icons.Rounded.Share, onShare)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (item.status !in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING)) {
                    IconButton(onClick = onRemove) { Icon(Icons.Rounded.DeleteOutline, "Remover registro", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = Lime)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label)
    }
}

@Composable
private fun StatusIcon(status: DownloadStatus) {
    val (icon, color) = when (status) {
        DownloadStatus.COMPLETED -> Icons.Rounded.CheckCircle to Lime
        DownloadStatus.FAILED -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> Icons.Rounded.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.INTERRUPTED -> Icons.Rounded.Refresh to Forest
        else -> Icons.Rounded.PlayArrow to Lime
    }
    Box(Modifier.size(40.dp).background(color.copy(alpha = .14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color)
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, remaining)
    else "%d:%02d".format(Locale.ROOT, minutes, remaining)
}

private fun formatBytes(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format(Locale.ROOT, "%.1f GB", mb / 1024) else String.format(Locale.ROOT, "%.0f MB", mb)
}
