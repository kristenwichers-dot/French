package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QuizItemEntity
import com.example.viewmodel.FrenchCompanionViewModel
import com.example.viewmodel.Screen
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: FrenchCompanionViewModel,
    modifier: Modifier = Modifier
) {
    val quizQuestions by viewModel.quizQuestions.collectAsState()
    val currentIndex by viewModel.currentQuestionIndex.collectAsState()
    val selectedAnswer by viewModel.selectedAnswer.collectAsState()
    val isSubmitted by viewModel.isAnswerSubmitted.collectAsState()
    val score by viewModel.quizScore.collectAsState()
    val isCompleted by viewModel.isQuizCompleted.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val selectedChapterId by viewModel.selectedChapterId.collectAsState()

    // If quiz is not in progress, show Quiz Selector / Setup
    if (quizQuestions.isEmpty()) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Quiz Me — Adaptive Practice",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Grounded in your textbook chapters and professor's lecture guidance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    text = "Select Practice Mode",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            item {
                QuizPresetCard(
                    title = "Quick Practice",
                    subtitle = "5 high-yield questions for a fast check",
                    badge = "5 Questions",
                    icon = Icons.Default.Bolt,
                    tag = "quiz_preset_quick_5"
                ) {
                    viewModel.startQuiz("QUICK_5")
                }
            }

            item {
                QuizPresetCard(
                    title = "Standard Practice",
                    subtitle = "10 questions covering vocabulary, grammar & listening",
                    badge = "10 Questions",
                    icon = Icons.Default.Quiz,
                    tag = "quiz_preset_standard_10"
                ) {
                    viewModel.startQuiz("STANDARD_10")
                }
            }

            item {
                QuizPresetCard(
                    title = "Practice What My Professor Emphasized",
                    subtitle = "Focus strictly on explicit test warnings and highlighted topics",
                    badge = "Exam Priority",
                    icon = Icons.Default.Star,
                    tag = "quiz_preset_professor_emphasis"
                ) {
                    viewModel.startQuiz("PROFESSOR_EMPHASIS")
                }
            }

            item {
                QuizPresetCard(
                    title = "Review My Weak Spots",
                    subtitle = "Target recurring errors (conjugations, articles, spelling)",
                    badge = "Adaptive",
                    icon = Icons.Default.FitnessCenter,
                    tag = "quiz_preset_weak_spots"
                ) {
                    viewModel.startQuiz("WEAK_SPOTS")
                }
            }

            item {
                QuizPresetCard(
                    title = "Comprehensive Exam Preparation",
                    subtitle = "25 in-depth questions preparing for your upcoming exam",
                    badge = "25 Questions",
                    icon = Icons.Default.School,
                    tag = "quiz_preset_exam_25"
                ) {
                    viewModel.startQuiz("EXAM_25")
                }
            }
        }
        return
    }

    // Quiz Completed View
    if (isCompleted) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Quiz Completed!",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You scored $score out of ${quizQuestions.size}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            val percentage = (score.toFloat() / quizQuestions.size * 100).toInt()
            Text(
                text = "$percentage% accuracy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Missed concepts have been automatically saved to your 'Needs More Practice' dashboard for spaced repetition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.startQuiz("QUICK_5") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quiz_retake_btn")
                ) {
                    Text("Practice Again")
                }
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.TRANSLATE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quiz_back_home_btn")
                ) {
                    Text("Translate")
                }
            }
        }
        return
    }

    // Active Quiz View
    val currentQuestion = quizQuestions.getOrNull(currentIndex) ?: return
    val options = remember(currentQuestion) {
        try {
            val jsonArray = JSONArray(currentQuestion.optionsJson)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            listOf("Option A", "Option B", "Option C", "Option D")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("active_quiz_screen")
    ) {
        // Progress & Score Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Question ${currentIndex + 1} of ${quizQuestions.size}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / quizQuestions.size },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Topic & Professor Alert
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = { Text(currentQuestion.topic, fontSize = 11.sp) }
            )
            if (currentQuestion.isProfessorEmphasis) {
                AssistChip(
                    onClick = {},
                    label = { Text("Professor's Exam Pick", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Question Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = currentQuestion.questionText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                IconButton(
                    onClick = { viewModel.speakFrench(currentQuestion.questionText) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options List
        options.forEachIndexed { idx, opt ->
            val isSelected = selectedAnswer == opt
            val isCorrectOption = opt.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)
            val backgroundColor = when {
                isSubmitted && isCorrectOption -> Color(0xFFE8F5E9)
                isSubmitted && isSelected && !isCorrectOption -> Color(0xFFFFEBEE)
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                isSubmitted && isCorrectOption -> Color(0xFF4CAF50)
                isSubmitted && isSelected && !isCorrectOption -> Color(0xFFF44336)
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable(enabled = !isSubmitted) {
                        viewModel.selectQuizAnswer(opt)
                    }
                    .testTag("quiz_option_$idx")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${('A' + idx)}. $opt",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSubmitted) {
                        if (isCorrectOption) {
                            Icon(Icons.Default.Check, contentDescription = "Correct", tint = Color(0xFF2E7D32))
                        } else if (isSelected) {
                            Icon(Icons.Default.Close, contentDescription = "Incorrect", tint = Color(0xFFC62828))
                        }
                    }
                }
            }
        }

        // Feedback Explanation
        if (isSubmitted) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (selectedAnswer?.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)) "✓ Correct !" else "✕ Not quite",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedAnswer?.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentQuestion.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Action Button
        if (!isSubmitted) {
            Button(
                onClick = { viewModel.submitQuizAnswer() },
                enabled = selectedAnswer != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_answer_btn")
            ) {
                Text("Check Answer", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { viewModel.nextQuizQuestion() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("next_question_btn")
            ) {
                Text(
                    text = if (currentIndex + 1 < quizQuestions.size) "Next Question →" else "View Results",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuizPresetCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
