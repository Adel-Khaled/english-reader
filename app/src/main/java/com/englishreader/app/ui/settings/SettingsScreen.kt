package com.englishreader.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishreader.app.R
import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.prefs.AppSettings
import com.englishreader.app.data.prefs.AppTheme
import com.englishreader.app.data.prefs.ReaderTheme
import com.englishreader.app.data.translate.OfflinePackState
import com.englishreader.app.ui.components.SectionTitle
import com.englishreader.app.ui.components.appContainer
import com.englishreader.app.ui.components.labelRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = appContainer()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val pack by c.offlineTranslator.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isArabic = AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("ar")
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0" }

    LaunchedEffect(Unit) { c.offlineTranslator.refreshState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsCard(stringResource(R.string.settings_appearance)) {
                    Label(stringResource(R.string.settings_theme))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AppTheme.SYSTEM to R.string.theme_system, AppTheme.LIGHT to R.string.theme_light, AppTheme.DARK to R.string.theme_dark).forEach { (t, l) ->
                            FilterChip(selected = settings.appTheme == t, onClick = { scope.launch { c.settings.setAppTheme(t) } }, label = { Text(stringResource(l)) })
                        }
                    }
                    Label(stringResource(R.string.settings_language))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isArabic, onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }, label = { Text(stringResource(R.string.lang_english)) })
                        FilterChip(selected = isArabic, onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }, label = { Text(stringResource(R.string.lang_arabic)) })
                    }
                }

                SettingsCard(stringResource(R.string.settings_reading)) {
                    Label(stringResource(R.string.reader_font_size) + " · ${settings.readerFontSize.toInt()}")
                    Slider(value = settings.readerFontSize, onValueChange = { v -> scope.launch { c.settings.setFontSize(Math.round(v).toFloat()) } }, valueRange = 12f..34f)
                    Label(stringResource(R.string.reader_theme))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ReaderTheme.LIGHT to R.string.reader_theme_light, ReaderTheme.SEPIA to R.string.reader_theme_sepia, ReaderTheme.DARK to R.string.reader_theme_dark).forEach { (t, l) ->
                            FilterChip(selected = settings.readerTheme == t, onClick = { scope.launch { c.settings.setReaderTheme(t) } }, label = { Text(stringResource(l)) })
                        }
                    }
                    Label(stringResource(R.string.settings_level))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.ADVANCED).forEach { d ->
                            FilterChip(selected = settings.userLevel == d, onClick = { scope.launch { c.settings.setUserLevel(d) } }, label = { Text(stringResource(d.labelRes())) })
                        }
                    }
                }

                SettingsCard(stringResource(R.string.settings_translation)) {
                    Text(stringResource(R.string.settings_offline_pack), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.settings_offline_pack_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (pack) {
                        OfflinePackState.READY -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_offline_ready), modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { c.offlineTranslator.remove() } }) { Text(stringResource(R.string.settings_offline_remove)) }
                        }
                        OfflinePackState.DOWNLOADING, OfflinePackState.UNKNOWN -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(if (pack == OfflinePackState.DOWNLOADING) R.string.settings_offline_downloading else R.string.loading))
                        }
                        OfflinePackState.NOT_INSTALLED, OfflinePackState.FAILED -> Column {
                            if (pack == OfflinePackState.FAILED) Text(stringResource(R.string.settings_offline_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            FilledTonalButton(onClick = { scope.launch { c.offlineTranslator.download() } }) {
                                Icon(Icons.Outlined.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_offline_download))
                            }
                        }
                    }
                }

                SettingsCard(stringResource(R.string.settings_data)) {
                    val cleared = stringResource(R.string.settings_cache_cleared)
                    OutlinedButton(onClick = { scope.launch { c.lookup.clearCache(); snackbar.showSnackbar(cleared) } }) {
                        Text(stringResource(R.string.settings_clear_cache))
                    }
                }

                SettingsCard(stringResource(R.string.settings_about)) {
                    Text(stringResource(R.string.settings_about_desc), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title, Modifier.padding(bottom = 2.dp))
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
