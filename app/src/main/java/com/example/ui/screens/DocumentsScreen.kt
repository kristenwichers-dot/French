package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CourseDocumentEntity
import com.example.viewmodel.FrenchCompanionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    viewModel: FrenchCompanionViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val selectedChapterId by viewModel.selectedChapterId.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadStatusMessage by viewModel.uploadStatusMessage.collectAsState()

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterChapter by remember { mutableStateOf(selectedChapterId) }
    var viewingDoc by remember { mutableStateOf<CourseDocumentEntity?>(null) }
    var showLectureUploadDialog by remember { mutableStateOf(false) }
    var showPasteNotesDialog by remember { mutableStateOf(false) }

    // Camera launcher to photograph textbook pages
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.uploadTextbookImage(bitmap, "textbook_camera_${System.currentTimeMillis()}.jpg")
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val sampleBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sampleBitmap)
            val paint = Paint().apply { color = android.graphics.Color.WHITE }
            canvas.drawRect(0f, 0f, 400f, 400f, paint)
            viewModel.uploadTextbookImage(sampleBitmap, "textbook_scan_${System.currentTimeMillis()}.jpg")
        }
    }

    // Audio / MP3 file launcher to upload and transcribe lectures
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                var fileName = "lecture_recording_${System.currentTimeMillis()}.mp3"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
                if (bytes.isNotEmpty()) {
                    Toast.makeText(context, "Uploading & transcribing $fileName...", Toast.LENGTH_SHORT).show()
                    viewModel.uploadAndTranscribeAudio(bytes, mimeType, fileName)
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsScreen", "Error reading audio file", e)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search textbook pages, audio, transcripts...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("doc_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Action Rows: 2 rows of clean study ingestion buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { cameraLauncher.launch(null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("camera_capture_btn")
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Page", fontSize = 13.sp)
            }

            Button(
                onClick = { audioPickerLauncher.launch("audio/*") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("upload_mp3_btn")
            ) {
                Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload MP3", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showPasteNotesDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("paste_notes_btn")
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paste Text/Notes", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { showLectureUploadDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_transcript_btn")
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Transcript", fontSize = 12.sp)
            }
        }

        if (isUploading) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_progress_indicator")
            )
            Text(
                text = uploadStatusMessage ?: "Extracting text with Gemini & preserving accents...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Document count and grounding badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Course Library (${documents.size} sources)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            AssistChip(
                onClick = { /* Grounding status */ },
                label = { Text("Source-Grounded", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter documents list
        val filteredDocs = documents.filter { doc ->
            (searchQuery.isBlank() || doc.title.contains(searchQuery, ignoreCase = true) || doc.contentText.contains(searchQuery, ignoreCase = true))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredDocs, key = { it.id }) { doc ->
                DocumentCard(
                    document = doc,
                    onViewClick = { viewingDoc = doc },
                    onListenClick = {
                        val firstFrench = doc.contentText.lines().find { it.contains(":") || it.contains("-") } ?: doc.title
                        viewModel.speakFrench(firstFrench.take(100))
                    }
                )
            }
        }
    }

    // Modal to View Full Extracted Document and Citations
    viewingDoc?.let { doc ->
        Dialog(onDismissRequest = { viewingDoc = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Source: ${doc.originalFileName} • ${doc.pageOrTimestamp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewingDoc = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (doc.isProfessorEmphasis) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⭐ Professor's Guidance: ${doc.professorGuidance.ifEmpty { "High test priority" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Extracted Course Content (Accents & Formatting Preserved):",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        item {
                            Text(
                                text = doc.contentText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.speakFrench(doc.contentText.take(250))
                            }
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hear Pronunciation")
                        }
                        Button(onClick = { viewingDoc = null }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    // Lecture Audio / Notes input dialog
    if (showLectureUploadDialog) {
        var lectureTitle by remember { mutableStateOf("Oct 14 Class Lecture") }
        var lectureNotes by remember {
            mutableStateOf(
                "[00:08:15] PROFESSOR: Remember this for the exam! When conjugating -er verbs like parler and aimer, the endings -e, -es, -ent are completely silent! Don't pronounce the 'ent'."
            )
        }

        Dialog(onDismissRequest = { showLectureUploadDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Upload Lecture Recording / Notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI will transcribe, detect explicit professor test warnings, and index timestamps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = lectureTitle,
                        onValueChange = { lectureTitle = it },
                        label = { Text("Lecture Title / Recording Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = lectureNotes,
                        onValueChange = { lectureNotes = it },
                        label = { Text("Transcript / Audio Notes / Timestamped Audio") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showLectureUploadDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.uploadLectureTranscript(lectureNotes, "$lectureTitle.m4a")
                                showLectureUploadDialog = false
                            },
                            modifier = Modifier.testTag("submit_lecture_btn")
                        ) {
                            Text("Process & Index")
                        }
                    }
                }
            }
        }
    }

    // Paste Text / Handout / Notes Dialog
    if (showPasteNotesDialog) {
        var pastedTitle by remember { mutableStateOf("Professor Lecture Handout") }
        var pastedBody by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showPasteNotesDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Paste Text into Course Study Sources",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste syllabus excerpts, handout text, assignment notes, or copied text. It will be immediately indexed and combined into your study guide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    pastedBody = text
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("paste_from_clipboard_doc_btn")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pastedTitle,
                        onValueChange = { pastedTitle = it },
                        label = { Text("Document / Handout Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pastedBody,
                        onValueChange = { pastedBody = it },
                        label = { Text("Pasted Content / French Text") },
                        placeholder = { Text("Type or paste study text, vocabulary, or rules here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPasteNotesDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pastedBody.isNotBlank()) {
                                    viewModel.savePastedCourseNotes(pastedTitle, pastedBody)
                                    showPasteNotesDialog = false
                                    Toast.makeText(context, "Notes added to study sources!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = pastedBody.isNotBlank(),
                            modifier = Modifier.testTag("save_pasted_doc_btn")
                        ) {
                            Text("Save & Index")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentCard(
    document: CourseDocumentEntity,
    onViewClick: () -> Unit,
    onListenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onViewClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${document.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (document.sourceTypeName) {
                        "Lecture Audio" -> Icons.Default.Audiotrack
                        "Textbook Photo" -> Icons.Default.PhotoCamera
                        else -> Icons.Default.MenuBook
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = document.sourceTypeName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = document.pageOrTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            if (document.isProfessorEmphasis) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Professor Exam Warning / Guidance Included",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = document.contentText.take(120).replace("\n", " ") + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ref: ${document.originalFileName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )
                Row {
                    IconButton(
                        onClick = onListenClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak sample",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(onClick = onViewClick) {
                        Text("View Source", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
