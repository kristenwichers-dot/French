package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.ChapterEntity
import com.example.data.CourseDocumentEntity
import com.example.viewmodel.FrenchCompanionViewModel
import java.io.File

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
    val uploadErrorMessage by viewModel.uploadErrorMessage.collectAsState()

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedChapterFilter by remember { mutableStateOf("ALL") }
    var selectedSourceFilter by remember { mutableStateOf("ALL") }
    var targetUploadChapterId by remember(selectedChapterId) { mutableStateOf(selectedChapterId) }
    var viewingDoc by remember { mutableStateOf<CourseDocumentEntity?>(null) }
    var editingDoc by remember { mutableStateOf<CourseDocumentEntity?>(null) }
    var showTargetChapterPicker by remember { mutableStateOf(false) }
    var showLectureUploadDialog by remember { mutableStateOf(false) }
    var showPasteNotesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<CourseDocumentEntity?>(null) }

    // Camera capture state and launcher (Full high-resolution via FileProvider)
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = cameraTempUri
        val tempFile = cameraTempFile
        if (success && uri != null) {
            val bitmap = decodeBitmapFromUri(context, uri)
            if (bitmap != null) {
                viewModel.uploadTextbookImage(
                    bitmap = bitmap,
                    fileName = "camera_page_${System.currentTimeMillis()}.jpg",
                    targetChapterId = targetUploadChapterId,
                    onSuccess = {
                        // Discard the original temporary camera file only after extraction & save succeed!
                        try {
                            if (tempFile != null && tempFile.exists()) {
                                tempFile.delete()
                            }
                            context.contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            Log.w("DocumentsScreen", "Could not delete temp camera file", e)
                        }
                    }
                )
            } else {
                Toast.makeText(context, "Could not decode camera photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Photo picker launcher (Supports MULTIPLE textbook photos from gallery)
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val itemsToUpload = mutableListOf<Pair<Bitmap, String>>()
            for (uri in uris) {
                val bmp = decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    val name = getFileNameFromUri(context, uri, "textbook_page_")
                    itemsToUpload.add(Pair(bmp, name))
                }
            }
            if (itemsToUpload.isNotEmpty()) {
                viewModel.uploadMultipleTextbookImages(
                    items = itemsToUpload,
                    targetChapterId = targetUploadChapterId
                )
            } else {
                Toast.makeText(context, "Could not read selected images", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Audio file picker launcher (MP3, M4A, WAV, AAC)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "lecture_recording_${System.currentTimeMillis()}.mp3"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            val found = cursor.getString(nameIndex)
                            if (!found.isNullOrBlank()) fileName = found
                        }
                        if (sizeIndex != -1) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }

                val sizeMb = fileSize / (1024.0 * 1024.0)
                if (sizeMb > 20.0) {
                    Toast.makeText(
                        context,
                        "Audio file is %.1f MB. The maximum inline limit is 20 MB. Please select a shorter lecture clip.".format(sizeMb),
                        Toast.LENGTH_LONG
                    ).show()
                    return@rememberLauncherForActivityResult
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: ByteArray(0)
                val mimeType = context.contentResolver.getType(uri) ?: when {
                    fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                    fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                    fileName.endsWith(".aac", ignoreCase = true) -> "audio/aac"
                    else -> "audio/mp3"
                }

                if (bytes.isNotEmpty()) {
                    Toast.makeText(context, "Uploading & transcribing $fileName...", Toast.LENGTH_SHORT).show()
                    viewModel.uploadAndTranscribeAudio(
                        audioBytes = bytes,
                        mimeType = mimeType,
                        fileName = fileName,
                        targetChapterId = targetUploadChapterId
                    )
                } else {
                    Toast.makeText(context, "Audio file was empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DocumentsScreen", "Error reading audio file", e)
                Toast.makeText(context, "Error reading audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchCamera() {
        try {
            val imagesDir = File(context.cacheDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val tempFile = File(imagesDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            cameraTempUri = uri
            cameraTempFile = tempFile
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("DocumentsScreen", "Error launching camera with FileProvider", e)
            Toast.makeText(context, "Failed to start camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

        // Target Upload Chapter Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val curCh = chapters.find { it.id == targetUploadChapterId }
                    Text(
                        text = "Upload to: ${curCh?.let { "Ch. ${it.number} (${it.title.take(18)}...)" } ?: "Select Chapter"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = { showTargetChapterPicker = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Change", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Rows: Clean study ingestion buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { launchCamera() },
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
                onClick = {
                    multiPhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("upload_photos_btn")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Photos", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { audioPickerLauncher.launch("audio/*") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("upload_mp3_btn")
            ) {
                Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Audio/MP3", fontSize = 12.sp)
            }

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
        }

        // Upload in-progress indicator
        if (isUploading) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_progress_indicator")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uploadStatusMessage ?: "Processing with Gemini...",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Upload success message banner
        if (!isUploading && uploadStatusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uploadStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearUploadStatus() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Upload error banner
        if (uploadErrorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uploadErrorMessage ?: "An error occurred",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearUploadStatus() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                    }
                }
            }
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
                label = {
                    Text(
                        if (documents.isNotEmpty()) "Source-Grounded" else "No Materials Yet",
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (documents.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (documents.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chapter Organization Filter Chips
        ScrollableTabRow(
            selectedTabIndex = if (selectedChapterFilter == "ALL") 0 else (chapters.indexOfFirst { it.id == selectedChapterFilter } + 1).coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedChapterFilter == "ALL",
                onClick = { selectedChapterFilter = "ALL" },
                text = { Text("All (${documents.size})", fontSize = 11.sp, fontWeight = if (selectedChapterFilter == "ALL") FontWeight.Bold else FontWeight.Normal) }
            )
            chapters.forEach { ch ->
                val count = documents.count { it.chapterId == ch.id }
                Tab(
                    selected = selectedChapterFilter == ch.id,
                    onClick = {
                        selectedChapterFilter = ch.id
                        targetUploadChapterId = ch.id
                    },
                    text = { Text("Ch. ${ch.number} ($count)", fontSize = 11.sp, fontWeight = if (selectedChapterFilter == ch.id) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Source Type Filter Chips
        val sources = listOf("ALL", "Textbook Photo", "Textbook PDF", "Lecture Audio", "Pasted Notes")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(sources) { src ->
                val isSelected = selectedSourceFilter == src
                val label = if (src == "ALL") "All Sources" else src
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedSourceFilter = src },
                    label = { Text(label, fontSize = 11.sp) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filter documents list by search, chapter, and source
        val filteredDocs = documents.filter { doc ->
            val matchesQuery = (searchQuery.isBlank() || doc.title.contains(searchQuery, ignoreCase = true) || doc.contentText.contains(searchQuery, ignoreCase = true))
            val matchesChapter = (selectedChapterFilter == "ALL" || doc.chapterId == selectedChapterFilter)
            val matchesSource = (selectedSourceFilter == "ALL" || doc.sourceTypeName.equals(selectedSourceFilter, ignoreCase = true))
            matchesQuery && matchesChapter && matchesSource
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (filteredDocs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching documents found." else "No documents in this view.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scan textbook pages, upload lecture MP3s, or paste notes using the buttons above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    DocumentCard(
                        document = doc,
                        onViewClick = { viewingDoc = doc },
                        onEditClick = { editingDoc = doc },
                        onListenClick = {
                            val firstFrench = doc.contentText.lines().find { it.contains(":") || it.contains("-") } ?: doc.title
                            viewModel.speakFrench(firstFrench.take(100))
                        },
                        onDeleteClick = {
                            showDeleteConfirmDialog = doc
                        }
                    )
                }
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Course Document", doc.contentText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied content to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    val toEdit = doc
                                    viewingDoc = null
                                    editingDoc = toEdit
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Mistakes", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    viewModel.speakFrench(doc.contentText.take(250))
                                }
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pronounce")
                            }
                            Button(onClick = { viewingDoc = null }) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirmDialog?.let { docToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to remove '${docToDelete.title}' from your course library?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(docToDelete)
                        showDeleteConfirmDialog = null
                        Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
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
                                    viewModel.savePastedCourseNotes(
                                        title = pastedTitle,
                                        content = pastedBody,
                                        targetChapterId = targetUploadChapterId
                                    )
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

    // Editing Document Dialog for correcting mistakes
    editingDoc?.let { docToEdit ->
        EditDocumentDialog(
            document = docToEdit,
            chapters = chapters,
            onDismiss = { editingDoc = null },
            onSave = { updated ->
                viewModel.updateCourseDocument(updated)
                editingDoc = null
                Toast.makeText(context, "Corrections saved & knowledge base updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Target Chapter Picker Dialog
    if (showTargetChapterPicker) {
        AlertDialog(
            onDismissRequest = { showTargetChapterPicker = false },
            title = { Text("Select Target Chapter for Uploads") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "New textbook scans, audio recordings, or pasted notes will be indexed into the selected chapter:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    chapters.forEach { ch ->
                        OutlinedCard(
                            onClick = {
                                targetUploadChapterId = ch.id
                                showTargetChapterPicker = false
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (targetUploadChapterId == ch.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ch. ${ch.number}: ${ch.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (targetUploadChapterId == ch.id) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetChapterPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

fun decodeBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1920): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        val maxSide = maxOf(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (maxSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Exception) {
        Log.e("DocumentsScreen", "Error decoding bitmap from URI", e)
        null
    }
}

fun getFileNameFromUri(context: Context, uri: Uri, defaultPrefix: String = "textbook_page_"): String {
    var name = "$defaultPrefix${System.currentTimeMillis()}.jpg"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val found = cursor.getString(nameIndex)
                if (!found.isNullOrBlank()) name = found
            }
        }
    } catch (e: Exception) {
        Log.e("DocumentsScreen", "Error getting file name", e)
    }
    return name
}

@Composable
fun DocumentCard(
    document: CourseDocumentEntity,
    onViewClick: () -> Unit,
    onEditClick: () -> Unit,
    onListenClick: () -> Unit,
    onDeleteClick: () -> Unit,
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = document.pageOrTimestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    }
                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit or correct extraction",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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

@Composable
fun EditDocumentDialog(
    document: CourseDocumentEntity,
    chapters: List<ChapterEntity>,
    onDismiss: () -> Unit,
    onSave: (CourseDocumentEntity) -> Unit
) {
    var title by remember { mutableStateOf(document.title) }
    var chapterId by remember { mutableStateOf(document.chapterId) }
    var pageOrTimestamp by remember { mutableStateOf(document.pageOrTimestamp) }
    var contentText by remember { mutableStateOf(document.contentText) }
    var isProfessorEmphasis by remember { mutableStateOf(document.isProfessorEmphasis) }
    var professorGuidance by remember { mutableStateOf(document.professorGuidance) }
    var expandedChapterDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
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
                            text = "Correct Extraction Mistakes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Edit French accents, misread text, or reassign chapter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Document Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val curChap = chapters.find { it.id == chapterId }
                            OutlinedCard(
                                onClick = { expandedChapterDropdown = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Assigned Chapter",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = curChap?.let { "Ch. ${it.number}: ${it.title}" } ?: "Select Chapter",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedChapterDropdown,
                                onDismissRequest = { expandedChapterDropdown = false }
                            ) {
                                chapters.forEach { ch ->
                                    DropdownMenuItem(
                                        text = { Text("Ch. ${ch.number}: ${ch.title}") },
                                        onClick = {
                                            chapterId = ch.id
                                            expandedChapterDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = pageOrTimestamp,
                            onValueChange = { pageOrTimestamp = it },
                            label = { Text("Page or Timestamp (e.g. Page 29 or 00:14:30)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isProfessorEmphasis = !isProfessorEmphasis }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isProfessorEmphasis,
                                onCheckedChange = { isProfessorEmphasis = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "⭐ Professor Test Warning / Guidance Included",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }

                    if (isProfessorEmphasis) {
                        item {
                            OutlinedTextField(
                                value = professorGuidance,
                                onValueChange = { professorGuidance = it },
                                label = { Text("Professor's Explicit Test Advice") },
                                placeholder = { Text("e.g. Exam warning: Know être conjugations") },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Extracted Course Text (Preserve accents & tables):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = contentText,
                            onValueChange = { contentText = it },
                            placeholder = { Text("Extracted text with French accents...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 320.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = document.copy(
                                title = title.trim().ifEmpty { document.title },
                                chapterId = chapterId,
                                pageOrTimestamp = pageOrTimestamp.trim().ifEmpty { document.pageOrTimestamp },
                                contentText = contentText,
                                isProfessorEmphasis = isProfessorEmphasis,
                                professorGuidance = if (isProfessorEmphasis) professorGuidance.trim() else ""
                            )
                            onSave(updated)
                        }
                    ) {
                        Text("Save Corrections")
                    }
                }
            }
        }
    }
}
