package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.GeminiClient
import com.example.viewmodel.FrenchCompanionViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: FrenchCompanionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sourcePreference by viewModel.sourcePreference.collectAsState()
    val showPhonetics by viewModel.showPhonetics.collectAsState()
    val speechRate by viewModel.speechEngine.speechRate.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()

    var keyInputText by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    LaunchedEffect(customApiKey) {
        if (customApiKey.isNotBlank()) {
            keyInputText = customApiKey
        }
    }

    val isKeyConfigured = GeminiClient.isKeyConfigured()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Study Preferences",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Customize your AI French study companion to match your course requirements.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Gemini AI Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_config_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GEMINI AI CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isKeyConfigured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isKeyConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isKeyConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isKeyConfigured) "Configured & Active" else "API Key Missing",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isKeyConfigured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Gemini Model: ${GeminiClient.DEFAULT_MODEL}. Used for instant multimodal translation, OCR textbook extraction, and source-grounded notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = keyInputText,
                        onValueChange = { keyInputText = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Paste your AI Studio API key here...") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_key_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveCustomApiKey(keyInputText)
                                Toast.makeText(context, "API Key saved securely!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_api_key_btn")
                        ) {
                            Text("Save Key")
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingConnection = true
                                testResultStatus = null
                                coroutineScope.launch {
                                    try {
                                        val testKey = keyInputText.ifBlank { GeminiClient.getApiKey() }
                                        if (testKey.isBlank() || testKey == "MY_GEMINI_API_KEY") {
                                            testResultStatus = "Please enter an API key to test."
                                        } else {
                                            GeminiClient.setCustomApiKey(testKey)
                                            val response = GeminiClient.service.generateContent(
                                                apiKey = testKey,
                                                model = GeminiClient.DEFAULT_MODEL,
                                                request = com.example.network.GenerateContentRequest(
                                                    contents = listOf(
                                                        com.example.network.Content(
                                                            parts = listOf(com.example.network.Part(text = "Respond with 'OK'"))
                                                        )
                                                    )
                                                )
                                            )
                                            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                            testResultStatus = if (!text.isNullOrBlank()) "Connection successful to Gemini 3.5 Flash!" else "Connection received empty response"
                                        }
                                    } catch (e: Exception) {
                                        testResultStatus = "Connection error: ${e.localizedMessage ?: e.message}"
                                    } finally {
                                        isTestingConnection = false
                                    }
                                }
                            },
                            enabled = !isTestingConnection,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("test_gemini_connection_btn")
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Test Connection")
                        }
                    }

                    if (testResultStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = testResultStatus ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (testResultStatus?.contains("successful", ignoreCase = true) == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Source Grounding Preference
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "KNOWLEDGE SOURCE PREFERENCE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val preferences = listOf(
                        Triple("COURSE_FIRST", "Course Materials First (Default)", "Prioritize uploaded textbook & lecture notes, falling back to general French."),
                        Triple("COURSE_ONLY", "Course Materials Only", "Strictly restrict answers to uploaded materials. If not found, say so."),
                        Triple("GENERAL", "General French Knowledge", "Use standard AI French knowledge for everyday conversation.")
                    )

                    preferences.forEach { (key, label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sourcePreference == key,
                                onClick = { viewModel.setSourcePreference(key) },
                                modifier = Modifier.testTag("pref_$key")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Phonetic Pronunciation Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Phonetic Pronunciation",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Displays readable English syllables under French words (e.g. 'Zhuh vuh dew kah-FAY').",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showPhonetics,
                        onCheckedChange = { viewModel.setShowPhonetics(it) },
                        modifier = Modifier.testTag("toggle_phonetics_switch")
                    )
                }
            }
        }

        // Speech Rate Slider
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FRENCH AUDIO PLAYBACK SPEED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Current speed: ${String.format("%.2f", speechRate)}x (Slower is helpful for beginner listening comprehension)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.speechEngine.setSpeechRate(it) },
                        valueRange = 0.6f..1.2f,
                        steps = 5,
                        modifier = Modifier.testTag("audio_speed_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.6x (Very Slow)", style = MaterialTheme.typography.labelSmall)
                        Text("0.85x (Default)", style = MaterialTheme.typography.labelSmall)
                        Text("1.2x (Fast)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Privacy & Local Storage
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PRIVACY & COURSE DATA",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All uploaded textbook excerpts, quiz histories, and voice recordings remain private on your device in local Room database. No complete requests, audio recordings, or textbook photos are logged in production.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
