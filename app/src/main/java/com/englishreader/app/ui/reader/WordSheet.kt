package com.englishreader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.englishreader.app.R
import com.englishreader.app.data.vocab.TranslationSource
import com.englishreader.app.ui.components.SectionTitle

/** Bottom sheet shown when a word is tapped — the reader stays visible behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(
    selection: WordSelection,
    lookup: LookupState,
    isSaved: Boolean,
    sentenceTranslation: String?,
    sentenceLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onPronounce: (String, String?) -> Unit,
    onTranslateSentence: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val loaded = (lookup as? LookupState.Loaded)?.lookup
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selection.word, style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Ltr))
                    val sub = listOfNotNull(loaded?.phonetic?.takeIf { it.isNotBlank() }, loaded?.partOfSpeech?.takeIf { it.isNotBlank() }).joinToString("  ·  ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { onPronounce(selection.word, loaded?.audioUrl) }) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.word_pronounce))
                }
            }

            if (loaded == null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.loading))
                }
            } else {
                HorizontalDivider()
                SectionTitle(stringResource(R.string.word_translation))
                if (loaded.translationAr.isNotBlank()) {
                    Text(
                        loaded.translationAr,
                        style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (loaded.translationSource) {
                        TranslationSource.OFFLINE -> SourceNote(stringResource(R.string.word_offline_source))
                        TranslationSource.CACHE -> SourceNote(stringResource(R.string.word_cached_source))
                        else -> {}
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.word_no_translation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SectionTitle(stringResource(R.string.word_definition))
                Text(
                    loaded.definition.ifBlank { stringResource(R.string.word_no_definition) },
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    color = if (loaded.definition.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )

                if (loaded.examples.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.word_examples))
                    loaded.examples.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr, fontStyle = FontStyle.Italic))
                    }
                }
            }

            if (selection.sentence.isNotBlank()) {
                SectionTitle(stringResource(R.string.word_from_book))
                Text(
                    "“${selection.sentence}”",
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    sentenceLoading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    sentenceTranslation != null -> Text(
                        sentenceTranslation.ifBlank { stringResource(R.string.word_no_translation) },
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> TextButton(onClick = onTranslateSentence) {
                        Icon(Icons.Outlined.Translate, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.word_translate_sentence))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            if (isSaved) {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Bookmark, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.word_saved), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = lookup is LookupState.Loaded) {
                    Icon(Icons.Outlined.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.word_save), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SourceNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
}
