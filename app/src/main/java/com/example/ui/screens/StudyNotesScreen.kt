package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.viewmodel.FrenchCompanionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyNotesScreen(
    viewModel: FrenchCompanionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentNote by viewModel.currentStudyNote.collectAsState()
    val noteLength by viewModel.noteLength.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val selectedChapterId by viewModel.selectedChapterId.collectAsState()
    val allDocuments by viewModel.documents.collectAsState()
    val classDocuments = remember(allDocuments, selectedChapterId) {
        allDocuments.filter { it.chapterId == selectedChapterId }
    }

    var explainConceptTitle by remember { mutableStateOf<String?>(null) }
    var explainConceptBody by remember { mutableStateOf<String?>(null) }
    var showPasteToGuideDialog by remember { mutableStateOf(false) }
    var pasteSectionTarget by remember { mutableStateOf("GRAMMAR") }
    var textToPaste by remember { mutableStateOf("") }
    var previewDoc by remember { mutableStateOf<com.example.data.CourseDocumentEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Length Toggle Pills (Super Short [Default], Standard, Detailed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = noteLength == "SUPER_SHORT",
                onClick = { viewModel.setNoteLength("SUPER_SHORT") },
                label = { Text("Super Short", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("note_length_super_short")
            )
            FilterChip(
                selected = noteLength == "STANDARD",
                onClick = { viewModel.setNoteLength("STANDARD") },
                label = { Text("Standard", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("note_length_standard")
            )
            FilterChip(
                selected = noteLength == "DETAILED",
                onClick = { viewModel.setNoteLength("DETAILED") },
                label = { Text("Detailed", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("note_length_detailed")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Paste / Add into Study Guide Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("paste_into_guide_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.PostAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Add to Study Guide",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Paste text, rules, or lecture notes directly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        // Pre-populate from clipboard if available
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val clipText = clip.getItemAt(0).text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                textToPaste = clipText
                            }
                        }
                        showPasteToGuideDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("paste_into_guide_btn")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste Text", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Toolbar: Copy, Save Bookmark, Regenerate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentNote?.chapterTitle ?: "Smart Study Notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )

            Row {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("French Notes", currentNote?.let {
                            "${it.chapterTitle}\n\n${it.summaryShort}\n\nVOCABULARY:\n${it.keyVocab}\n\nGRAMMAR:\n${it.grammarRules}\n\nPROFESSOR EMPHASIS:\n${it.professorEmphasis}"
                        } ?: "")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "All notes copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("copy_notes_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy notes")
                }

                IconButton(
                    onClick = {
                        currentNote?.let {
                            viewModel.saveBookmark(it.chapterTitle, it.summaryShort, it.grammarRules, "Study Note")
                            Toast.makeText(context, "Saved to Bookmarks", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_note_bookmark_btn")
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Save bookmark")
                }

                IconButton(
                    onClick = { viewModel.regenerateNotes() },
                    modifier = Modifier.testTag("regenerate_notes_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate notes with AI")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Grounded in My Class Uploads Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_materials_grounding_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "GROUNDED IN MY CLASS (${classDocuments.size} SOURCES)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.regenerateNotes()
                                    Toast.makeText(context, "Study guide re-grounded with all class uploads!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "Sync with class uploads",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (classDocuments.isNotEmpty())
                                "These notes are actively synthesized from your textbook chapters, lectures, and notes uploaded under 'My Class'."
                            else
                                "No materials uploaded for this chapter yet. Upload textbook pages or audio under 'My Class' to auto-generate custom notes.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (classDocuments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                classDocuments.forEach { doc ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = doc.title,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "${doc.sourceTypeName} • ${doc.pageOrTimestamp}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            TextButton(
                                                onClick = { previewDoc = doc },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("View", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary Card depending on length
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (noteLength) {
                                    "SUPER_SHORT" -> "Super Short Overview (Fast Scan)"
                                    "STANDARD" -> "Standard Study Summary"
                                    else -> "Detailed Conceptual Breakdown"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (noteLength) {
                                "SUPER_SHORT" -> currentNote?.summaryShort ?: "Loading..."
                                "STANDARD" -> currentNote?.summaryStandard ?: "Loading..."
                                else -> currentNote?.summaryDetailed ?: "Loading..."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
                        )
                    }
                }
            }

            // Professor's Emphasis Section (High Priority near the top!)
            currentNote?.let { note ->
                if (note.professorEmphasis.isNotBlank()) {
                    item {
                        NoteSectionCard(
                            title = "Professor's Emphasis & Test Announcements",
                            content = note.professorEmphasis,
                            icon = Icons.Default.Star,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onExplainClick = {
                                explainConceptTitle = "Professor's Emphasis"
                                explainConceptBody = note.professorEmphasis
                            },
                            onListenClick = { viewModel.speakFrench(note.professorEmphasis.take(150)) }
                        )
                    }
                }

                // Key Vocabulary
                item {
                    NoteSectionCard(
                        title = "Key Vocabulary",
                        content = note.keyVocab,
                        icon = Icons.Default.Translate,
                        containerColor = MaterialTheme.colorScheme.surface,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onExplainClick = {
                            explainConceptTitle = "Vocabulary Details"
                            explainConceptBody = note.keyVocab
                        },
                        onListenClick = { viewModel.speakFrench(note.keyVocab.take(150)) }
                    )
                }

                // Grammar Rules
                item {
                    NoteSectionCard(
                        title = "Grammar Rules",
                        content = note.grammarRules,
                        icon = Icons.Default.Rule,
                        containerColor = MaterialTheme.colorScheme.surface,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onExplainClick = {
                            explainConceptTitle = "Grammar Rules"
                            explainConceptBody = note.grammarRules
                        },
                        onListenClick = { viewModel.speakFrench(note.grammarRules.take(150)) }
                    )
                }

                // Pronunciation
                item {
                    NoteSectionCard(
                        title = "Pronunciation & Phonetics",
                        content = note.pronunciationTips,
                        icon = Icons.Default.RecordVoiceOver,
                        containerColor = MaterialTheme.colorScheme.surface,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onExplainClick = {
                            explainConceptTitle = "Pronunciation Breakdown"
                            explainConceptBody = note.pronunciationTips
                        },
                        onListenClick = { viewModel.speakFrench(note.pronunciationTips.take(150)) }
                    )
                }

                // Cultural Information
                item {
                    NoteSectionCard(
                        title = "Cultural Information",
                        content = note.culturalNotes,
                        icon = Icons.Default.Public,
                        containerColor = MaterialTheme.colorScheme.surface,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onExplainClick = {
                            explainConceptTitle = "French Cultural Norms"
                            explainConceptBody = note.culturalNotes
                        },
                        onListenClick = { viewModel.speakFrench(note.culturalNotes.take(150)) }
                    )
                }

                // Examples to Remember
                item {
                    NoteSectionCard(
                        title = "Examples to Remember",
                        content = note.examples,
                        icon = Icons.Default.FormatQuote,
                        containerColor = MaterialTheme.colorScheme.surface,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onExplainClick = {
                            explainConceptTitle = "Example Sentences"
                            explainConceptBody = note.examples
                        },
                        onListenClick = { viewModel.speakFrench(note.examples.take(150)) }
                    )
                }
            }
        }
    }

    // "Explain This" AI Pop-up Modal without bloating entire notes
    explainConceptTitle?.let { title ->
        Dialog(onDismissRequest = { explainConceptTitle = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Explain This: $title",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { explainConceptTitle = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Here is a dedicated plain-English explanation for an American college student learning Beginning French:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = explainConceptBody ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.speakFrench(explainConceptBody?.take(150) ?: "")
                            }
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Listen")
                        }
                        Button(onClick = { explainConceptTitle = null }) {
                            Text("Got it")
                        }
                    }
                }
            }
        }
    }

    // "Paste into Study Guide" Dialog
    if (showPasteToGuideDialog) {
        Dialog(onDismissRequest = { showPasteToGuideDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paste into Study Guide",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(onClick = { showPasteToGuideDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste text, rules, or lecture notes directly into your course study guide.",
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
                                    textToPaste = text
                                    Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("paste_clipboard_action_btn")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Target Study Section:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = pasteSectionTarget == "GRAMMAR",
                            onClick = { pasteSectionTarget = "GRAMMAR" },
                            label = { Text("Grammar", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = pasteSectionTarget == "VOCAB",
                            onClick = { pasteSectionTarget = "VOCAB" },
                            label = { Text("Vocab", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = pasteSectionTarget == "PROFESSOR",
                            onClick = { pasteSectionTarget = "PROFESSOR" },
                            label = { Text("Professor", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = pasteSectionTarget == "EXAMPLES",
                            onClick = { pasteSectionTarget = "EXAMPLES" },
                            label = { Text("Examples", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = pasteSectionTarget == "PRONUNCIATION",
                            onClick = { pasteSectionTarget = "PRONUNCIATION" },
                            label = { Text("Phonetics", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = pasteSectionTarget == "SUMMARY",
                            onClick = { pasteSectionTarget = "SUMMARY" },
                            label = { Text("Summary", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = textToPaste,
                        onValueChange = { textToPaste = it },
                        label = { Text("Content to add") },
                        placeholder = { Text("Type or paste study text, vocabulary, grammar rules...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPasteToGuideDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (textToPaste.isNotBlank()) {
                                    viewModel.pasteIntoStudyGuide(pasteSectionTarget, textToPaste)
                                    showPasteToGuideDialog = false
                                    Toast.makeText(context, "Added to Study Guide!", Toast.LENGTH_SHORT).show()
                                    textToPaste = ""
                                }
                            },
                            enabled = textToPaste.isNotBlank(),
                            modifier = Modifier.testTag("save_paste_to_guide_btn")
                        ) {
                            Text("Add to Guide")
                        }
                    }
                }
            }
        }
    }

    // Document Source Preview Modal
    previewDoc?.let { doc ->
        Dialog(onDismissRequest = { previewDoc = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${doc.sourceTypeName} • ${doc.pageOrTimestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = { previewDoc = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        Text(
                            text = doc.contentText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { previewDoc = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
fun NoteSectionCard(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onExplainClick: () -> Unit,
    onListenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("note_section_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(title, content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "$title copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy section",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onListenClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(
                        onClick = onExplainClick,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Explain", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = textColor
            )
        }
    }
}
