package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AccentKeyboardRow
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.FrenchCompanionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAnythingScreen(
    viewModel: FrenchCompanionViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val translationMode by viewModel.translationMode.collectAsState()
    val showPhonetics by viewModel.showPhonetics.collectAsState()
    val isListening by viewModel.speechEngine.isListening.collectAsState()
    val liveTranscript by viewModel.speechEngine.liveTranscript.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var isRecordingForChat by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(liveTranscript) {
        if (isRecordingForChat && liveTranscript.isNotBlank()) {
            inputPrompt = liveTranscript
        }
    }

    LaunchedEffect(isListening) {
        if (!isListening && isRecordingForChat) {
            isRecordingForChat = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("ask_anything_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Mode Toggle Pills: Auto (default), Quick Answer, Tutor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = translationMode == "AUTO",
                onClick = { viewModel.setTranslationMode("AUTO") },
                label = { Text("Auto", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tutor_mode_auto")
            )
            FilterChip(
                selected = translationMode == "QUICK",
                onClick = { viewModel.setTranslationMode("QUICK") },
                label = { Text("Quick Answer", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tutor_mode_quick")
            )
            FilterChip(
                selected = translationMode == "TUTOR",
                onClick = { viewModel.setTranslationMode("TUTOR") },
                label = { Text("Tutor Mode", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tutor_mode_tutor")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    showPhonetics = showPhonetics,
                    onSpeak = { french -> viewModel.speakFrench(french) }
                )
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tutor is consulting course knowledge...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Voice to Text Active Listening Indicator
        if (isRecordingForChat && isListening) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Listening... Speak your question in English or French",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // French Accent Helper Keyboard
        AccentKeyboardRow(
            onAccentClick = { char -> inputPrompt += char }
        )

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = { Text("Ask for a translation, rule, or homework...") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Voice to Text Microphone Button
            IconButton(
                onClick = {
                    if (isListening && isRecordingForChat) {
                        viewModel.speechEngine.stopListening()
                        isRecordingForChat = false
                    } else {
                        isRecordingForChat = true
                        viewModel.speechEngine.startListening("auto") { spoken ->
                            inputPrompt = spoken
                        }
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isRecordingForChat && isListening) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                    .testTag("voice_to_text_question_btn")
            ) {
                Icon(
                    imageVector = if (isRecordingForChat && isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Voice to text question",
                    tint = if (isRecordingForChat && isListening) Color.White else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (inputPrompt.isNotBlank()) {
                        viewModel.sendChatMessage(inputPrompt)
                        inputPrompt = ""
                    }
                },
                enabled = inputPrompt.isNotBlank() && !isLoading,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (inputPrompt.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("chat_send_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (inputPrompt.isNotBlank()) Color.White else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    showPhonetics: Boolean,
    onSpeak: (String) -> Unit
) {
    val isUser = message.sender == "USER"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = "Tutor",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 4.dp, end = 6.dp)
            )
        }

        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (isUser) {
                    Text(
                        text = message.englishText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                } else {
                    if (message.frenchText.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.frenchText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { onSpeak(message.frenchText) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (showPhonetics && message.phoneticText.isNotBlank()) {
                            Text(
                                text = "[ ${message.phoneticText} ]",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (message.englishText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.englishText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (message.explanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message.explanation,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (message.citation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "📖 Grounded in: ${message.citation}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
