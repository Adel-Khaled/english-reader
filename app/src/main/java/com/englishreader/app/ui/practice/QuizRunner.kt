package com.englishreader.app.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.englishreader.app.R
import com.englishreader.app.practice.Question
import com.englishreader.app.practice.QuestionType

val CorrectGreen = Color(0xFF2E7D32)
val WrongRed = Color(0xFFC62828)
val CloseOrange = Color(0xFFE08A00)

/** Shows multiple-choice questions one by one with instant feedback, then a score. */
@Composable
fun QuizRunner(
    questions: List<Question>,
    onAnswered: (index: Int, chosen: Int, correct: Boolean) -> Unit,
    onFinished: (correct: Int, total: Int) -> Unit,
    finishedContent: @Composable (correct: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableIntStateOf(-1) }
    var correctCount by rememberSaveable { mutableIntStateOf(0) }
    var done by rememberSaveable { mutableStateOf(false) }

    if (done || questions.isEmpty()) {
        ScoreCard(correctCount, questions.size)
        finishedContent(correctCount, questions.size)
        return
    }
    val q = questions[index]
    val ltr = TextDirection.Ltr
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(progress = { index.toFloat() / questions.size }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.practice_question_of, index + 1, questions.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(when (q.type) {
                    QuestionType.COMPREHENSION -> R.string.practice_type_comprehension
                    QuestionType.VOCABULARY -> R.string.practice_type_vocabulary
                    QuestionType.FILL_BLANK -> R.string.practice_type_blank
                }),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(q.prompt, style = MaterialTheme.typography.titleLarge.copy(textDirection = ltr))
        q.options.forEachIndexed { i, option ->
            val answered = chosen >= 0
            val isCorrect = i == q.correctIndex
            val border = when {
                answered && isCorrect -> BorderStroke(2.dp, CorrectGreen)
                answered && i == chosen -> BorderStroke(2.dp, WrongRed)
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            }
            Card(
                border = border,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
                    chosen = i
                    val ok = i == q.correctIndex
                    if (ok) correctCount++
                    onAnswered(index, i, ok)
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), modifier = Modifier.weight(1f))
                    if (answered && isCorrect) Icon(Icons.Filled.CheckCircle, null, tint = CorrectGreen)
                    else if (answered && i == chosen) Icon(Icons.Filled.Cancel, null, tint = WrongRed)
                }
            }
        }
        if (chosen >= 0) {
            val ok = chosen == q.correctIndex
            Text(
                if (ok) stringResource(R.string.practice_correct) else stringResource(R.string.practice_wrong, q.options[q.correctIndex]),
                color = if (ok) CorrectGreen else WrongRed, fontWeight = FontWeight.SemiBold,
            )
            if (q.explanation.isNotBlank()) Text(q.explanation, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                if (index + 1 >= questions.size) { done = true; onFinished(correctCount, questions.size) }
                else { index++; chosen = -1 }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (index + 1 >= questions.size) R.string.practice_finish else R.string.practice_next))
            }
        }
    }
}

@Composable
fun ScoreCard(correct: Int, total: Int) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.EmojiEvents, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.practice_done_all), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.practice_score, correct, total), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun FinishButtons(onAgain: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.practice_back_hub)) }
        Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.practice_again)) }
    }
    Spacer(Modifier.width(1.dp))
}
