package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AccentKeyboardRow
import com.example.viewmodel.FrenchCompanionViewModel

@Composable
fun TranslateScreen(
    viewModel: FrenchCompanionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isListening by viewModel.speechEngine.isListening.collectAsState()
    val liveTranscript by viewModel.speechEngine.liveTranscript.collectAsState()
    val livePhrases by viewModel.liveTranscripts.collectAsState()

    // Type to translate state
    var inputText by remember { mutableStateOf("") }
    var addExplanation by remember { mutableStateOf(false) }
    var selectedDirection by remember { mutableStateOf("Auto-Detect (English ⇄ French)") }
    var directionMenuExpanded by remember { mutableStateOf(false) }

    // Displayed Translation & Language Detection State
    var detectedLanguage by remember { mutableStateOf("French") }
    var targetLanguage by remember { mutableStateOf("English") }
    var currentSpoken by remember { mutableStateOf("Bonjour, comment allez-vous ?") }
    var currentTranslated by remember { mutableStateOf("Hello, how are you?") }
    var currentPhonetics by remember { mutableStateOf("Koh-mahn tah-lay VOO") }
    var currentExplanation by remember { mutableStateOf("") }
    var currentAlternatives by remember { mutableStateOf(listOf("Comment vas-tu ? (Informal to friend)", "Ça va ? (Casual: How's it going?)")) }

    // Sync live transcript changes into display
    LaunchedEffect(liveTranscript) {
        if (liveTranscript.isNotBlank()) {
            currentSpoken = liveTranscript
            viewModel.handleLiveSpeech(liveTranscript)
        }
    }

    LaunchedEffect(livePhrases) {
        val latest = livePhrases.firstOrNull()
        if (latest != null) {
            detectedLanguage = latest.spokenLang
            targetLanguage = latest.targetLang
            currentSpoken = latest.spokenText.ifEmpty { latest.french }
            currentTranslated = latest.translatedText.ifEmpty { latest.english }
            currentPhonetics = latest.phonetics
            currentAlternatives = latest.alternatives
            currentExplanation = latest.explanation
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("translate_screen_container")
    ) {
        // Centered Main Header
        Text(
            text = "Instant translation",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Auto-detects whether you speak English or French and gives the accurate translation with phonetic guide and alternative options.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: LIVE LISTENING
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_listening_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE LISTENING",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (isListening) "Listening..." else "Microphone off",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Big Purple Microphone Button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isListening) 1.12f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )

                    IconButton(
                        onClick = {
                            if (isListening) {
                                viewModel.stopLiveListening()
                            } else {
                                val dirMode = when (selectedDirection) {
                                    "English ➔ French" -> "ENGLISH_TO_FRENCH"
                                    "French ➔ English" -> "FRENCH_TO_ENGLISH"
                                    else -> "AUTO"
                                }
                                viewModel.startLiveListening(dirMode)
                            }
                        },
                        modifier = Modifier
                            .size(76.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("tap_to_listen_btn")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Tap to listen",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isListening) "LISTENING... SPEAK ENGLISH OR FRENCH" else "TAP TO LISTEN",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Speaks or hears both English and French with automatic language detection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Direction Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Box {
                        OutlinedButton(
                            onClick = { directionMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("direction_dropdown_btn")
                        ) {
                            Text(selectedDirection, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = directionMenuExpanded,
                            onDismissRequest = { directionMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auto-Detect (English ⇄ French)") },
                                onClick = {
                                    selectedDirection = "Auto-Detect (English ⇄ French)"
                                    directionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("English ➔ French") },
                                onClick = {
                                    selectedDirection = "English ➔ French"
                                    directionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("French ➔ English") },
                                onClick = {
                                    selectedDirection = "French ➔ English"
                                    directionMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons: Pause, Stop, Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.stopLiveListening() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pause_listening_btn")
                    ) {
                        Text("Pause", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.stopLiveListening() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stop_listening_btn")
                    ) {
                        Text("Stop", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.clearLiveTranscripts()
                            currentSpoken = ""
                            currentTranslated = ""
                            currentPhonetics = ""
                            currentAlternatives = emptyList()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_listening_btn")
                    ) {
                        Text("Clear", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Phrases translate automatically. Auto-detect identifies English vs French and outputs the matching translation.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: TRANSLATION RESULTS (DYNAMIC SPOKEN & TRANSLATION WITH ALTERNATIVES)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("translation_result_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Language Detection Pill Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🌐 Detected: $detectedLanguage ➔ Translating to: $targetLanguage",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: WHAT WAS SPOKEN / HEARD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WHAT I HEARD ($detectedLanguage)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row {
                        IconButton(
                            onClick = {
                                val loc = if (detectedLanguage == "English") Locale.ENGLISH else Locale.FRENCH
                                viewModel.speechEngine.speak(currentSpoken, locale = loc)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Hear input", modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Spoken Text", currentSpoken))
                                Toast.makeText(context, "Copied spoken text", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("copy_spoken_btn")
                        ) {
                            Text("Copy", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentSpoken.ifEmpty { "Waiting for speech..." },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: WHAT IT MEANS / HOW TO SAY IT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRANSLATION ($targetLanguage)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        IconButton(
                            onClick = {
                                val loc = if (targetLanguage == "French") Locale.FRENCH else Locale.ENGLISH
                                viewModel.speechEngine.speak(currentTranslated, locale = loc)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Hear translation", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Translation", currentTranslated))
                                Toast.makeText(context, "Copied translation", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("copy_translated_btn")
                        ) {
                            Text("Copy", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentTranslated.ifEmpty { "Translation will appear here..." },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                if (currentPhonetics.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Phonetic: $currentPhonetics",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (currentExplanation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Section 3: ALTERNATIVE OPTIONS & OTHER WAYS TO SAY THIS
                if (currentAlternatives.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "OPTIONS & OTHER WAYS TO SAY THIS:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentAlternatives.forEach { alt ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = alt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val cleanPhrase = alt.substringBefore("(").trim()
                                            viewModel.speakFrench(cleanPhrase)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VolumeUp,
                                            contentDescription = "Hear option",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Hear & Save phrase
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val targetWord = if (targetLanguage == "French") currentTranslated else currentSpoken
                            viewModel.speakFrench(targetWord)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hear_french_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hear French", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val f = if (targetLanguage == "French") currentTranslated else currentSpoken
                            val e = if (targetLanguage == "English") currentTranslated else currentSpoken
                            viewModel.saveBookmark(
                                title = "Saved Translation",
                                french = f,
                                english = e,
                                category = "Translation"
                            )
                            Toast.makeText(context, "Phrase saved to Study list!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_phrase_btn")
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save phrase", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: TYPE TO TRANSLATE
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("type_to_translate_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TYPE TO TRANSLATE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.outline
                    )
                    AssistChip(
                        onClick = { /* Toggle */ },
                        label = { Text("Auto-detect ⇄", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type something in English or French...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("translate_input_text"),
                    shape = RoundedCornerShape(10.dp)
                )

                // French Accent Helper Keyboard
                AccentKeyboardRow(
                    onAccentClick = { char -> inputText += char }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { addExplanation = !addExplanation }
                    ) {
                        Checkbox(
                            checked = addExplanation,
                            onCheckedChange = { addExplanation = it },
                            modifier = Modifier.testTag("add_explanation_checkbox")
                        )
                        Text("Add explanation", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.translateText(inputText, addExplanation) { result ->
                                    detectedLanguage = result.detectedLanguage
                                    targetLanguage = result.targetLanguage
                                    currentSpoken = if (result.detectedLanguage == "English") result.englishText else result.frenchText
                                    currentTranslated = if (result.detectedLanguage == "English") result.frenchText else result.englishText
                                    currentPhonetics = result.phoneticText
                                    currentAlternatives = result.alternatives
                                    currentExplanation = result.explanation
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("translate_submit_btn")
                    ) {
                        Text("Translate →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 4: LIVE TRANSCRIPT HISTORY
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_transcript_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE TRANSCRIPT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedButton(
                        onClick = {
                            val fullText = livePhrases.joinToString("\n\n") {
                                "[${it.spokenLang} ➔ ${it.targetLang}] ${it.spokenText.ifEmpty { it.french }} = ${it.translatedText.ifEmpty { it.english }}"
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", fullText))
                            Toast.makeText(context, "Copied all transcript phrases", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("copy_all_transcript_btn")
                    ) {
                        Text("Copy all", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (livePhrases.isEmpty()) {
                    Text(
                        text = "Live phrases appear here as you speak or listen. Language is automatically detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    livePhrases.forEach { phrase ->
                        val spoken = phrase.spokenText.ifEmpty { phrase.french }
                        val trans = phrase.translatedText.ifEmpty { phrase.english }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${phrase.spokenLang} ➔ ${phrase.targetLang}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = spoken,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Text(
                                    text = trans,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val toSpeak = if (phrase.targetLang == "French") trans else spoken
                                    viewModel.speakFrench(toSpeak)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Hear", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
