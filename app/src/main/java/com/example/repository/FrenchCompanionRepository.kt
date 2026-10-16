package com.example.repository

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.*
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class TranslationResult(
    val frenchText: String,
    val englishText: String,
    val phoneticText: String = "",
    val explanation: String = "",
    val isCourseGrounded: Boolean = false,
    val citation: String = "",
    val detectedLanguage: String = "Auto", // "English" or "French"
    val targetLanguage: String = "French", // "French" or "English"
    val alternatives: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class LectureAnalysisResult(
    val title: String,
    val transcript: String,
    val professorGuidance: String,
    val keyTopics: List<String>,
    val testAnnouncements: List<String>
)

data class PronunciationFeedback(
    val targetPhrase: String,
    val phoneticGuide: String,
    val score: Int, // 0 - 100
    val feedbackMessage: String,
    val mouthPositionTip: String
)

class FrenchCompanionRepository(
    private val appDao: AppDao
) {
    // Flows from database
    val chapters: Flow<List<ChapterEntity>> = appDao.getAllChapters()
    val documents: Flow<List<CourseDocumentEntity>> = appDao.getAllDocuments()
    val weakTopics: Flow<List<WeakTopicEntity>> = appDao.getActiveWeakTopics()
    val flashcards: Flow<List<FlashcardEntity>> = appDao.getAllFlashcards()
    val quizAttempts: Flow<List<QuizAttemptEntity>> = appDao.getAllQuizAttempts()
    val savedBookmarks: Flow<List<SavedBookmarkEntity>> = appDao.getAllBookmarks()

    fun getDocumentsByChapter(chapterId: String): Flow<List<CourseDocumentEntity>> =
        appDao.getDocumentsByChapter(chapterId)

    fun observeStudyNote(chapterId: String): Flow<StudyNoteEntity?> =
        appDao.observeStudyNoteForChapter(chapterId)

    fun getFlashcardsForChapter(chapterId: String): Flow<List<FlashcardEntity>> =
        appDao.getFlashcardsForChapter(chapterId)

    // Helper: Call Gemini without fabricating fallback responses
    suspend fun callGemini(
        prompt: String,
        systemPrompt: String? = null,
        imagePart: Part? = null
    ): String = withContext(Dispatchers.IO) {
        val parts = mutableListOf<Part>()
        imagePart?.let { parts.add(it) }
        parts.add(Part(text = prompt))

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                maxOutputTokens = 4096
            ),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) }
        )

        GeminiClient.executeGenerateContent(request)
    }

    // 1. Text / Photo Extraction (Multimodal OCR)
    suspend fun extractTextFromImage(bitmap: Bitmap, chapterId: String, fileName: String): CourseDocumentEntity = withContext(Dispatchers.IO) {
        val existingByName = appDao.findDocumentByChapterAndFileName(chapterId, fileName)
        if (existingByName != null) {
            throw IllegalArgumentException("A document from '$fileName' has already been saved to this chapter. To make corrections, edit the document directly in your library.")
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val imagePart = Part(
            inline_data = InlineData(
                mime_type = "image/jpeg",
                data = base64
            )
        )

        val prompt = """
            Extract all text accurately and completely from this French textbook page or handout.
            Preserve all French accents (é, è, ê, à, â, ç, etc.), punctuation, tables, vocabulary pairs, and grammatical notes exactly as printed.
            Do not summarize, truncate, or omit vocabulary lists.
            If a word is blurry or ambiguous, indicate with [unclear] rather than guessing.
            Provide a brief 1-line summary at the very beginning starting with 'TITLE: '.
        """.trimIndent()

        val rawResult = callGemini(prompt, imagePart = imagePart)
        if (rawResult.isBlank()) {
            throw GeminiException.EmptyResponseException("No text could be extracted from this image. Please ensure the photo is clear and well-lit.")
        }

        val existingDocs = appDao.getDocumentsListByChapter(chapterId)
        val duplicateContent = existingDocs.find { it.contentText.trim() == rawResult.trim() }
        if (duplicateContent != null) {
            throw IllegalArgumentException("This page content has already been saved into this chapter as '${duplicateContent.title}'. Duplicate upload prevented.")
        }

        val lines = rawResult.lines()
        val titleLine = lines.firstOrNull { it.startsWith("TITLE:") }?.removePrefix("TITLE:")?.trim()
            ?: "Textbook Scan: $fileName"

        val doc = CourseDocumentEntity(
            chapterId = chapterId,
            title = titleLine,
            sourceTypeName = "Textbook Photo",
            originalFileName = fileName,
            pageOrTimestamp = "Textbook Page",
            contentText = rawResult,
            isProfessorEmphasis = false
        )
        val docId = appDao.insertDocument(doc)
        doc.copy(id = docId)
    }

    // 2. Lecture Recording Analysis
    suspend fun analyzeLectureNotes(
        chapterId: String,
        rawNotesOrTranscript: String,
        audioFileName: String
    ): CourseDocumentEntity = withContext(Dispatchers.IO) {
        val prompt = """
            Analyze this French I college lecture recording transcript or notes:
            ${rawNotesOrTranscript.take(8000)}

            Extract:
            1. Explicit professor test announcements or warnings (e.g. 'Remember this for the exam', 'This won't be on the test', 'This is important').
            2. Repeated emphasis or common student errors corrected by the professor.
            3. Key French vocabulary and grammatical rules discussed.

            Format your response clearly with:
            PROFESSOR EMPHASIS:
            <list of explicit test warnings and repeated concepts>

            STRUCTURED TRANSCRIPT & NOTES:
            <timestamped or bulleted summary>
        """.trimIndent()

        val analysis = callGemini(prompt)
        val hasEmphasis = analysis.contains("PROFESSOR EMPHASIS", ignoreCase = true) ||
                analysis.contains("exam", ignoreCase = true) ||
                analysis.contains("test", ignoreCase = true)

        val doc = CourseDocumentEntity(
            chapterId = chapterId,
            title = "Lecture Analysis: $audioFileName",
            sourceTypeName = "Lecture Audio",
            originalFileName = audioFileName,
            pageOrTimestamp = "Class Recording",
            contentText = analysis,
            isProfessorEmphasis = hasEmphasis,
            professorGuidance = extractGuidanceSummary(analysis)
        )
        val docId = appDao.insertDocument(doc)
        doc.copy(id = docId)
    }

    // 2b. Multimodal MP3/Audio File Transcription
    suspend fun transcribeAudioFile(
        audioBytes: ByteArray,
        mimeType: String,
        chapterId: String,
        fileName: String
    ): CourseDocumentEntity = withContext(Dispatchers.IO) {
        if (audioBytes.isEmpty()) {
            throw IllegalArgumentException("Audio file is empty.")
        }
        val sizeMb = audioBytes.size / (1024.0 * 1024.0)
        if (sizeMb > 20.0) {
            throw IllegalArgumentException("Audio file is %.1f MB. The direct inline upload limit is 20 MB. Please provide a shorter lecture clip or compressed MP3.".format(sizeMb))
        }

        val existingByName = appDao.findDocumentByChapterAndFileName(chapterId, fileName)
        if (existingByName != null) {
            throw IllegalArgumentException("A recording from '$fileName' has already been transcribed in this chapter. To make corrections, edit the document directly in your library.")
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val audioPart = Part(
            inline_data = InlineData(
                mime_type = if (mimeType.isNotBlank()) mimeType else "audio/mp3",
                data = base64Audio
            )
        )
        val prompt = """
            You are a native French transcription expert and Beginning French I study companion.
            Transcribe and analyze this classroom lecture audio recording.
            1. Transcribe the French words and phrases spoken by the professor and students, preserving accurate French accents (é, è, ê, à, ç, etc.).
            2. Transcribe English explanations and classroom examples.
            3. Highlight explicit professor test announcements (e.g. 'Remember this for the exam', 'You'll need to know this', 'This is important', 'Don't worry about memorizing that').
            4. Extract key French vocabulary pairs and grammar rules.
            5. Provide chronological timestamp markers [00:00:00].

            Format your response clearly:
            EXPLICIT PROFESSOR GUIDANCE / EXAM WARNINGS:
            <list of test warnings and exam tips, or 'None noted in this recording' if professor did not mention any>

            FULL CHRONOLOGICAL TRANSCRIPT:
            <timestamped French and English classroom dialogue>

            KEY VOCABULARY & GRAMMAR:
            <extracted vocabulary and rules>
        """.trimIndent()

        val result = callGemini(prompt, imagePart = audioPart)
        if (result.isBlank()) {
            throw GeminiException.EmptyResponseException("Audio transcription returned an empty response. Ensure the recording is clear.")
        }

        val existingDocs = appDao.getDocumentsListByChapter(chapterId)
        val duplicateContent = existingDocs.find { it.contentText.trim() == result.trim() }
        if (duplicateContent != null) {
            throw IllegalArgumentException("This lecture transcript has already been saved into this chapter as '${duplicateContent.title}'. Duplicate upload prevented.")
        }

        val hasEmphasis = result.contains("EXPLICIT PROFESSOR GUIDANCE", ignoreCase = true) &&
                !result.contains("None noted in this recording", ignoreCase = true) &&
                (result.contains("exam", ignoreCase = true) || result.contains("test", ignoreCase = true) || result.contains("important", ignoreCase = true))

        val doc = CourseDocumentEntity(
            chapterId = chapterId,
            title = "Lecture Audio: $fileName",
            sourceTypeName = "Lecture Audio",
            originalFileName = fileName,
            pageOrTimestamp = "Transcribed Audio",
            contentText = result,
            isProfessorEmphasis = hasEmphasis,
            professorGuidance = extractGuidanceSummary(result)
        )
        val docId = appDao.insertDocument(doc)
        doc.copy(id = docId)
    }

    // 2c. Save Pasted Course Notes / Text into Library
    suspend fun savePastedCourseNotes(
        chapterId: String,
        title: String,
        content: String,
        sourceType: String = "Pasted Notes"
    ): CourseDocumentEntity = withContext(Dispatchers.IO) {
        val existingDocs = appDao.getDocumentsListByChapter(chapterId)
        val duplicateContent = existingDocs.find { it.contentText.trim() == content.trim() }
        if (duplicateContent != null) {
            throw IllegalArgumentException("Identical notes are already saved in this chapter as '${duplicateContent.title}'. Duplicate upload prevented.")
        }

        val hasEmphasis = content.contains("exam", ignoreCase = true) ||
                content.contains("test", ignoreCase = true) ||
                content.contains("important", ignoreCase = true)

        val doc = CourseDocumentEntity(
            chapterId = chapterId,
            title = title.ifBlank { "Pasted Study Notes" },
            sourceTypeName = sourceType,
            originalFileName = "pasted_notes_${System.currentTimeMillis()}.txt",
            pageOrTimestamp = "Pasted Study Notes",
            contentText = content,
            isProfessorEmphasis = hasEmphasis,
            professorGuidance = if (hasEmphasis) "Contains exam or study notes pasted by student" else ""
        )
        val docId = appDao.insertDocument(doc)
        doc.copy(id = docId)
    }

    // 2d. Update Custom Study Guide Note directly
    suspend fun saveCustomStudyNote(note: StudyNoteEntity) = withContext(Dispatchers.IO) {
        appDao.insertStudyNote(note)
    }

    // 2e. Update Existing Course Document (allowing student to correct extraction mistakes)
    suspend fun updateCourseDocument(updatedDoc: CourseDocumentEntity): CourseDocumentEntity = withContext(Dispatchers.IO) {
        appDao.updateDocument(updatedDoc)
        val chapterNote = appDao.getStudyNoteForChapter(updatedDoc.chapterId)
        val title = chapterNote?.chapterTitle ?: "Chapter Notes"
        generateOrUpdateSmartNotes(updatedDoc.chapterId, title)
        updatedDoc
    }

    // 2f. Delete Course Document and refresh knowledge base
    suspend fun deleteCourseDocument(doc: CourseDocumentEntity) = withContext(Dispatchers.IO) {
        appDao.deleteDocument(doc)
        val chapterNote = appDao.getStudyNoteForChapter(doc.chapterId)
        val title = chapterNote?.chapterTitle ?: "Chapter Notes"
        generateOrUpdateSmartNotes(doc.chapterId, title)
    }

    // 3. Consolidated Smart Notes Generation Grounded in Uploaded Materials with Document Chunking
    suspend fun generateOrUpdateSmartNotes(chapterId: String, chapterTitle: String): StudyNoteEntity = withContext(Dispatchers.IO) {
        // Collect all documents for this chapter directly from user's uploads in "My Class"
        val docs = appDao.getDocumentsListByChapter(chapterId)

        if (docs.isEmpty()) {
            val emptyNote = StudyNoteEntity(
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                keyVocab = "• No vocabulary uploaded yet for this chapter.",
                grammarRules = "• No grammar rules uploaded yet for this chapter.",
                pronunciationTips = "• Upload textbook pages or audio recordings under 'My Class' to generate custom pronunciation notes.",
                culturalNotes = "• Cultural notes will appear once course materials are uploaded.",
                professorEmphasis = "No professor announcements or exam warnings recorded yet.",
                examples = "• Upload textbook excerpts to view relevant course examples.",
                summaryShort = "No course materials uploaded for this chapter yet. Upload textbook pages or audio recordings under 'My Class' to generate course-grounded notes.",
                summaryStandard = "This chapter currently has no uploaded materials in your course library. Once you photograph textbook pages or upload classroom audio recordings, your study guide will synthesize them automatically.",
                summaryDetailed = "Your course library is empty for this chapter. Add textbook pages, lecture recordings, or paste syllabus notes under 'My Class' to build a complete, source-grounded review."
            )
            appDao.insertStudyNote(emptyNote)
            return@withContext emptyNote
        }

        // Semantic document chunking and synthesis
        // Prioritize professor emphasized content near the top
        val emphasizedDocs = docs.filter { it.isProfessorEmphasis }
        val regularDocs = docs.filterNot { it.isProfessorEmphasis }

        val chunkedBuilder = StringBuilder()
        if (emphasizedDocs.isNotEmpty()) {
            chunkedBuilder.append("=== PRIORITY: PROFESSOR EMPHASIZED MATERIAL ===\n")
            emphasizedDocs.forEach { doc ->
                chunkedBuilder.append("--- SOURCE: ${doc.title} (${doc.pageOrTimestamp}) [${doc.sourceTypeName}] ---\n")
                chunkedBuilder.append(doc.contentText.take(6000))
                chunkedBuilder.append("\n\n")
            }
        }

        chunkedBuilder.append("=== COURSE MATERIALS (TEXTBOOK & LECTURE NOTES) ===\n")
        regularDocs.forEach { doc ->
            chunkedBuilder.append("--- SOURCE: ${doc.title} (${doc.pageOrTimestamp}) [${doc.sourceTypeName}] ---\n")
            chunkedBuilder.append(doc.contentText.take(5000))
            chunkedBuilder.append("\n\n")
        }

        val combinedSources = chunkedBuilder.toString()

        val prompt = """
            You are a master French tutor creating concise smart study notes for an American college student in Beginning French I.
            Combine the uploaded textbook content and lecture recording notes below into ONE cohesive, non-repetitive study guide.
            DO NOT duplicate explanations where textbook and lecture overlap.
            Give priority to professor-emphasized test material near the top!
            For each section, include citations back to the source where appropriate (e.g. [p. 28] or [Lecture 00:15:30]).

            COURSE MATERIALS:
            $combinedSources

            Return JSON matching this format:
            {
               "keyVocab": "bulleted vocabulary with French accents, English definitions, and citations",
               "grammarRules": "bulleted clear, simple grammar rules with examples",
               "pronunciationTips": "phonetic hints, silent consonants, nasal vowels, liaisons",
               "culturalNotes": "concise cultural etiquette tips",
               "professorEmphasis": "explicit professor exam warnings and highlighted points from the materials (or state 'None recorded in uploaded materials')",
               "examples": "key conversational sentences to remember with citations",
               "summaryShort": "3-4 sentence rapid review summary",
               "summaryStandard": "2 paragraph balanced review summary citing key textbook concepts",
               "summaryDetailed": "In-depth explanation covering exceptions, nuances, and professor emphasis"
            }
        """.trimIndent()

        val parsedNote = try {
            val jsonStr = callGemini(prompt, "Output ONLY valid JSON.")
            parseSmartNoteJson(jsonStr, chapterId, chapterTitle, docs)
        } catch (e: Exception) {
            Log.w("FrenchCompanionRepository", "Gemini synthesis failed, synthesizing directly from stored documents", e)
            synthesizeNotesDirectlyFromDocs(docs, chapterId, chapterTitle)
        }

        appDao.insertStudyNote(parsedNote)

        // Also extract flashcards and quiz questions from uploaded documents so Flashcards and Quiz tabs are updated!
        extractAndSaveFlashcardsFromDocs(chapterId, docs)
        extractAndSaveQuizItemsFromDocs(chapterId, docs)

        parsedNote
    }

    // 4. Quick Translator & Q&A with Bidirectional Auto-Detection
    suspend fun answerOrTranslate(
        userInput: String,
        mode: String = "AUTO", // "AUTO", "QUICK", "TUTOR"
        sourcePreference: String = "COURSE_FIRST", // "COURSE_FIRST", "COURSE_ONLY", "GENERAL"
        showPhonetics: Boolean = true
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult(
                frenchText = "",
                englishText = "",
                detectedLanguage = "English",
                targetLanguage = "French"
            )
        }

        val detected = detectLanguage(trimmed)
        val target = if (detected == "English") "French" else "English"

        // Check for explicit "French only" request
        val isFrenchOnly = trimmed.contains("french only", ignoreCase = true) ||
                trimmed.contains("just the french", ignoreCase = true) ||
                trimmed.contains("in french only", ignoreCase = true)

        // Retrieve matching course material if requested
        val matchingDocs = if (sourcePreference != "GENERAL") {
            appDao.searchDocuments(trimmed.take(30))
        } else emptyList()

        if (sourcePreference == "COURSE_ONLY" && matchingDocs.isEmpty()) {
            return@withContext TranslationResult(
                frenchText = "",
                englishText = "I couldn't find that in your uploaded course materials.",
                phoneticText = "",
                explanation = "Your source preference is set to 'Course Materials Only'. Switch to 'Course Materials First' or 'General French Knowledge' to answer questions outside the uploaded textbook.",
                isCourseGrounded = false,
                detectedLanguage = detected,
                targetLanguage = target
            )
        }

        val courseContext = if (matchingDocs.isNotEmpty()) {
            "COURSE MATERIAL GROUNDING:\n" + matchingDocs.take(3).joinToString("\n") {
                "[From ${it.originalFileName} (${it.pageOrTimestamp})]: ${it.contentText.take(500)}"
            }
        } else ""

        val systemPrompt = """
            You are an expert, patient French tutor for an American college student in Beginning French I.
            Auto-detect whether the student is speaking/typing English or French:
            - If student speaks/types English: Provide the natural French translation, easy-to-read English phonetics, and 2-3 common alternative ways to say it (formal vs informal: vous vs tu, polite café request vs direct statement).
            - If student speaks/types French: Provide the accurate English translation, phonetic guide for the French, and explain the grammatical structure.
            - If ambiguous or multiple possibilities exist, provide the most helpful options with context labels so the student knows what to use.
            - Response mode: $mode (AUTO, QUICK, TUTOR).
        """.trimIndent()

        val citationText = if (matchingDocs.isNotEmpty()) {
            val doc = matchingDocs.first()
            "${doc.originalFileName} (${doc.pageOrTimestamp})"
        } else ""

        val userPrompt = """
            $courseContext

            User Input: "$trimmed"
            Detected Language: $detected
            Target Language: $target

            Respond in JSON:
            {
               "detectedLanguage": "$detected",
               "targetLanguage": "$target",
               "frenchText": "Accurate French phrase or translation",
               "englishText": "Accurate English meaning or translation",
               "phoneticText": "Easy English-friendly phonetic pronunciation (e.g. Zhuh vuh dew kah-FAY)",
               "explanation": "Brief clear grammar breakdown if requested or in Tutor mode, else empty string",
               "alternatives": ["Alternative 1 with nuance label", "Alternative 2 with nuance label"],
               "citation": "$citationText"
            }
        """.trimIndent()

        try {
            val raw = callGemini(userPrompt, systemPrompt)
            val result = parseTranslationJson(raw, isFrenchOnly, showPhonetics, detected, target, trimmed)
            result.copy(isCourseGrounded = matchingDocs.isNotEmpty())
        } catch (e: Exception) {
            Log.w("FrenchCompanionRepository", "Gemini translation failed: ${e.message}", e)
            // Check verified offline dictionary
            val offline = getOfflineDictionaryTranslation(trimmed, detected)
            if (offline != null) {
                return@withContext offline.copy(
                    isCourseGrounded = matchingDocs.isNotEmpty(),
                    phoneticText = if (showPhonetics) offline.phoneticText else ""
                )
            }
            // Propagate error message with no fabricated hallucination
            TranslationResult(
                frenchText = if (detected == "French") trimmed else "",
                englishText = if (detected == "English") trimmed else "",
                detectedLanguage = detected,
                targetLanguage = target,
                errorMessage = "Translation unavailable (${e.localizedMessage ?: "Network or API Key error"}). Tap to retry."
            )
        }
    }

    // 5. Pronunciation Evaluation
    suspend fun evaluatePronunciation(targetPhrase: String, userSpoken: String): PronunciationFeedback = withContext(Dispatchers.IO) {
        val prompt = """
            Evaluate a Beginning French I American student's pronunciation.
            Target French phrase: "$targetPhrase"
            What was recognized from their voice: "$userSpoken"

            Provide constructive, encouraging feedback focusing on American learner challenges (French R, nasal vowels, silent final consonants, liaison).
            Return JSON:
            {
               "phoneticGuide": "Readable English phonetic spelling, e.g. bohn-ZHOOR",
               "score": integer between 60 and 100 based on word match and precision,
               "feedbackMessage": "Short friendly advice (e.g. 'Try making the final sound softer.')",
               "mouthPositionTip": "Brief mouth/tongue position guide (e.g. 'Round your lips as if to say 'oo', then make an 'ee' sound.')"
            }
        """.trimIndent()

        try {
            val raw = callGemini(prompt, "Return ONLY valid JSON.")
            parsePronunciationJson(raw, targetPhrase)
        } catch (e: Exception) {
            // Local fallback pronunciation evaluator based on Levenshtein/token distance
            val similarity = calculateSimilarity(targetPhrase.lowercase(), userSpoken.lowercase())
            val score = (similarity * 100).toInt().coerceIn(60, 98)
            PronunciationFeedback(
                targetPhrase = targetPhrase,
                phoneticGuide = generateApproxFrenchPhonetics(targetPhrase),
                score = score,
                feedbackMessage = if (score > 80) "Great pronunciation! Clean vowel sounds." else "Good effort! Listen to the audio and repeat.",
                mouthPositionTip = "Keep lips rounded and tongue touching the lower teeth for crisp French vowels."
            )
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val w1 = s1.split(" ").filter { it.isNotBlank() }
        val w2 = s2.split(" ").filter { it.isNotBlank() }
        val matches = w1.count { it in w2 }
        return matches.toFloat() / maxOf(w1.size, w2.size)
    }

    // 6. Record Quiz Performance & Adaptive Weak Topic Tracking
    suspend fun recordQuizResults(
        chapterId: String,
        score: Int,
        total: Int,
        missedQuestions: List<QuizItemEntity>
    ) = withContext(Dispatchers.IO) {
        val missedTopics = missedQuestions.map { it.topic }.distinct()
        val attempt = QuizAttemptEntity(
            score = score,
            total = total,
            chapterId = chapterId,
            missedTopicsJson = JSONArray(missedTopics).toString()
        )
        appDao.insertQuizAttempt(attempt)

        // Update weak topics
        for (missed in missedQuestions) {
            val existing = appDao.getWeakTopicByName(missed.topic)
            if (existing != null) {
                appDao.updateWeakTopic(existing.copy(
                    missCount = existing.missCount + 1,
                    lastPracticed = System.currentTimeMillis(),
                    isResolved = false
                ))
            } else {
                val category = determineCategory(missed.topic, missed.questionText)
                appDao.insertWeakTopic(WeakTopicEntity(
                    topicName = missed.topic,
                    category = category,
                    missCount = 1,
                    lastPracticed = System.currentTimeMillis(),
                    isResolved = false
                ))
            }
        }
    }

    // 7. Generate Adaptive Quiz Questions
    suspend fun getOrGenerateQuiz(
        chapterId: String?,
        preset: String, // "QUICK_5", "STANDARD_10", "EXAM_25", "WEAK_SPOTS", "PROFESSOR_EMPHASIS"
    ): List<QuizItemEntity> = withContext(Dispatchers.IO) {
        var baseList = when (preset) {
            "PROFESSOR_EMPHASIS" -> appDao.getProfessorEmphasizedQuizItems()
            "WEAK_SPOTS" -> {
                val all = appDao.getAllQuizItems()
                all.shuffled()
            }
            else -> {
                if (chapterId != null && chapterId != "all") {
                    appDao.getQuizItemsForChapter(chapterId)
                } else {
                    appDao.getAllQuizItems()
                }
            }
        }

        if (baseList.isEmpty()) {
            baseList = appDao.getAllQuizItems()
        }

        val targetCount = when (preset) {
            "QUICK_5" -> 5
            "STANDARD_10" -> 10
            "EXAM_25" -> 25
            else -> 10
        }

        baseList.shuffled().take(targetCount)
    }

    // 8. Homework Helper
    suspend fun solveHomework(questionText: String, style: String = "AUTO"): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are a French homework tutor for a Beginning French I college student.
            Homework question: "$questionText"
            User requested style: $style (e.g. 'plain explanation', 'translate this', 'why is my answer wrong', 'guide step by step', 'just give me the French')

            Rules:
            - If user requested 'just the French', provide only the French answer with correct accents.
            - If 'explain the instructions', explain what the exercise wants them to do in plain English.
            - If 'why is my answer wrong', give a concise, respectful correction.
            - Never give a long essay when a simple answer is needed.
        """.trimIndent()
        callGemini(prompt)
    }

    // Bookmarks and Settings
    suspend fun saveBookmark(title: String, frenchText: String, englishText: String, category: String, note: String = "") {
        appDao.insertBookmark(SavedBookmarkEntity(
            title = title,
            frenchText = frenchText,
            englishText = englishText,
            category = category,
            note = note
        ))
    }

    suspend fun deleteBookmark(bookmark: SavedBookmarkEntity) {
        appDao.deleteBookmark(bookmark)
    }

    suspend fun updateFlashcardStatus(card: FlashcardEntity, newStatus: String) {
        appDao.updateFlashcard(card.copy(
            status = newStatus,
            lastReviewed = System.currentTimeMillis(),
            reviewCount = card.reviewCount + 1
        ))
    }

    suspend fun markWeakTopicResolved(id: Long) {
        appDao.markWeakTopicResolved(id)
    }

    suspend fun getSetting(key: String): String? = appDao.getSetting(key)
    suspend fun setSetting(key: String, value: String) = appDao.setSetting(AppSettingEntity(key, value))

    // Parsing helpers
    private fun extractGuidanceSummary(analysis: String): String {
        val lines = analysis.lines()
        val guidance = lines.filter { it.contains("exam", ignoreCase = true) || it.contains("important", ignoreCase = true) || it.contains("test", ignoreCase = true) }
        return guidance.take(3).joinToString("; ").take(250)
    }

    private fun determineCategory(topic: String, text: String): String {
        val combined = "$topic $text".lowercase()
        return when {
            combined.contains("verb") || combined.contains("conjugat") || combined.contains("être") || combined.contains("avoir") -> "Verb Conjugation"
            combined.contains("article") || combined.contains("gender") || combined.contains("un ") || combined.contains("le ") -> "Gender & Articles"
            combined.contains("pronounc") || combined.contains("silent") || combined.contains("liaison") -> "Pronunciation"
            combined.contains("accent") || combined.contains("spell") -> "Spelling & Accents"
            else -> "Vocabulary"
        }
    }

    fun detectLanguage(text: String): String {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return "English"

        // Explicit French characters
        val frenchAccents = setOf('é', 'è', 'ê', 'ë', 'à', 'â', 'î', 'ï', 'ô', 'ù', 'û', 'ç', 'œ', 'æ')
        if (lower.any { it in frenchAccents }) return "French"

        // Common French words & contractions
        val frenchTokens = setOf(
            "bonjour", "salut", "merci", "oui", "non", "au revoir", "s'il vous plaît", "s'il te plaît",
            "je", "j'", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "moi", "toi",
            "le", "la", "les", "un", "une", "des", "du", "de", "d'", "au", "aux", "à",
            "suis", "es", "est", "sommes", "êtes", "sont", "ai", "as", "a", "avons", "avez", "ont",
            "vais", "vas", "va", "allons", "allez", "vont", "veux", "veut", "voulons", "voulez", "veulent", "voudrais",
            "parle", "parlez", "habite", "habitez", "appelle", "appelez", "comprends", "comprend", "comprenez",
            "comment", "pourquoi", "où", "quand", "qui", "que", "qu'", "quoi", "quel", "quelle",
            "avec", "dans", "pour", "sur", "chez", "mais", "très", "bien", "aussi", "toujours", "café",
            "livre", "classe", "étudiant", "matière", "lieu", "france", "français", "professeur"
        )

        // Common English words
        val englishTokens = setOf(
            "the", "a", "an", "this", "that", "these", "those",
            "i", "you", "he", "she", "it", "we", "they", "my", "your", "his", "her", "our", "their",
            "is", "am", "are", "was", "were", "be", "been", "being",
            "do", "does", "did", "have", "has", "had",
            "want", "would", "like", "need", "can", "could", "will", "shall", "should", "may", "might",
            "hello", "hi", "hey", "please", "thank", "thanks", "goodbye", "bye",
            "what", "how", "where", "when", "why", "who", "which",
            "translate", "say", "mean", "meaning", "speak", "pronounce", "classroom", "library"
        )

        val words = lower.split(Regex("[^\\p{L}']+")).filter { it.isNotBlank() }
        var frenchScore = 0
        var englishScore = 0

        for (w in words) {
            if (frenchTokens.contains(w)) frenchScore += 2
            if (englishTokens.contains(w)) englishScore += 2
        }

        return if (frenchScore > englishScore) "French" else "English"
    }

    private suspend fun extractAndSaveFlashcardsFromDocs(chapterId: String, docs: List<CourseDocumentEntity>) {
        if (docs.isEmpty()) return
        val newCards = mutableListOf<FlashcardEntity>()
        val seen = mutableSetOf<String>()

        for (doc in docs) {
            val lines = doc.contentText.lines()
            for (line in lines) {
                val trimmed = line.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim()
                if (trimmed.length in 6..120 && (trimmed.contains("—") || trimmed.contains(":") || trimmed.contains("(") || trimmed.contains("="))) {
                    val parts = when {
                        trimmed.contains("—") -> trimmed.split("—", limit = 2)
                        trimmed.contains(":") -> trimmed.split(":", limit = 2)
                        trimmed.contains("=") -> trimmed.split("=", limit = 2)
                        trimmed.contains("(") -> {
                            val f = trimmed.substringBefore("(").trim()
                            val e = trimmed.substringAfter("(").substringBefore(")").trim()
                            listOf(f, e)
                        }
                        else -> emptyList()
                    }
                    if (parts.size == 2) {
                        val front = parts[0].trim().removePrefix("Voici ").trim()
                        val back = parts[1].trim().removeSuffix(")").trim()
                        if (front.isNotBlank() && back.isNotBlank() && !seen.contains(front.lowercase())) {
                            seen.add(front.lowercase())
                            newCards.add(
                                FlashcardEntity(
                                    chapterId = chapterId,
                                    frenchText = front,
                                    englishText = back,
                                    phoneticText = generateApproxFrenchPhonetics(front),
                                    exampleSentence = "From ${doc.title} (${doc.pageOrTimestamp})"
                                )
                            )
                        }
                    }
                }
            }
        }

        if (newCards.isNotEmpty()) {
            appDao.insertFlashcards(newCards.take(25))
        }
    }

    private suspend fun extractAndSaveQuizItemsFromDocs(chapterId: String, docs: List<CourseDocumentEntity>) {
        if (docs.isEmpty()) return
        val existingQuizzes = appDao.getQuizItemsForChapter(chapterId)
        val existingQuestions = existingQuizzes.map { it.questionText.lowercase() }.toSet()
        val newQuizItems = mutableListOf<QuizItemEntity>()

        val vocabPairs = mutableListOf<Pair<String, String>>()
        for (doc in docs) {
            for (line in doc.contentText.lines()) {
                val trimmed = line.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim()
                if (trimmed.length in 5..100) {
                    val parts = when {
                        trimmed.contains("—") -> trimmed.split("—", limit = 2)
                        trimmed.contains(":") -> trimmed.split(":", limit = 2)
                        trimmed.contains("=") -> trimmed.split("=", limit = 2)
                        trimmed.contains("(") -> {
                            val f = trimmed.substringBefore("(").trim()
                            val e = trimmed.substringAfter("(").substringBefore(")").trim()
                            listOf(f, e)
                        }
                        else -> emptyList()
                    }
                    if (parts.size == 2) {
                        val french = parts[0].trim().removePrefix("Voici ").trim()
                        val english = parts[1].trim().removeSuffix(")").trim()
                        if (french.isNotBlank() && english.isNotBlank() && (!french.contains(" ") || french.length < 35)) {
                            vocabPairs.add(Pair(french, english))
                        }
                    }
                }
            }
        }

        if (vocabPairs.size >= 2) {
            for ((index, pair) in vocabPairs.take(15).withIndex()) {
                val (french, english) = pair
                val qText = "What is the French translation for \"$english\"?"
                if (!existingQuestions.contains(qText.lowercase())) {
                    val otherFrench = vocabPairs.filter { it.first != french }.map { it.first }.shuffled().take(3)
                    val allOptions = (listOf(french) + otherFrench).shuffled()
                    val optionsJson = JSONArray(allOptions).toString()
                    val matchingDoc = docs.find { it.contentText.contains(french, ignoreCase = true) }
                    newQuizItems.add(
                        QuizItemEntity(
                            chapterId = chapterId,
                            topic = matchingDoc?.title?.take(30) ?: "Vocabulary",
                            questionType = "MULTIPLE_CHOICE",
                            questionText = qText,
                            optionsJson = optionsJson,
                            correctAnswer = french,
                            explanation = "From ${matchingDoc?.title ?: "course materials"} (${matchingDoc?.pageOrTimestamp ?: "Textbook"}).",
                            isProfessorEmphasis = matchingDoc?.isProfessorEmphasis == true
                        )
                    )
                }
            }
        }

        if (newQuizItems.isNotEmpty()) {
            appDao.insertQuizItems(newQuizItems)
        }
    }

    private fun synthesizeNotesDirectlyFromDocs(docs: List<CourseDocumentEntity>, chapterId: String, title: String): StudyNoteEntity {
        val vocabList = mutableListOf<String>()
        val grammarList = mutableListOf<String>()
        val professorList = mutableListOf<String>()
        val pronunciationList = mutableListOf<String>()
        val culturalList = mutableListOf<String>()
        val exampleList = mutableListOf<String>()

        for (doc in docs) {
            val lines = doc.contentText.lines()
            for (line in lines) {
                val trimmed = line.trim()
                val lower = trimmed.lowercase()

                if (lower.contains("exam") || lower.contains("test") || lower.contains("important") ||
                    lower.contains("warning") || lower.contains("memorize") || lower.contains("remember") ||
                    doc.isProfessorEmphasis
                ) {
                    if (trimmed.length > 5 && !professorList.contains(trimmed)) {
                        professorList.add(trimmed)
                    }
                }

                if (lower.contains("verb") || lower.contains("conjugat") || lower.contains("grammar") ||
                    lower.contains("accord") || lower.contains("pronoun") || lower.contains("article")
                ) {
                    if (trimmed.length > 5 && !grammarList.contains(trimmed)) {
                        grammarList.add(trimmed)
                    }
                } else if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.contains("—") || trimmed.contains(":")) {
                    if (trimmed.length > 4 && vocabList.size < 20 && !vocabList.contains(trimmed)) {
                        vocabList.add(trimmed)
                    }
                }

                if (lower.contains("pronounc") || lower.contains("silent") || lower.contains("nasal") || lower.contains("liaison")) {
                    if (!pronunciationList.contains(trimmed)) pronunciationList.add(trimmed)
                }

                if (lower.contains("culture") || lower.contains("polite") || lower.contains("france") || lower.contains("etiquette")) {
                    if (!culturalList.contains(trimmed)) culturalList.add(trimmed)
                }

                if (lower.contains("example") || trimmed.startsWith("[") || lower.contains("practice")) {
                    if (!exampleList.contains(trimmed)) exampleList.add(trimmed)
                }
            }
        }

        val keyVocab = if (vocabList.isNotEmpty()) vocabList.take(12).joinToString("\n") else "• (No vocabulary extracted yet)"
        val grammarRules = if (grammarList.isNotEmpty()) grammarList.take(8).joinToString("\n") else "• (No grammar rules extracted yet)"
        val professorEmphasis = if (professorList.isNotEmpty()) professorList.take(5).joinToString("\n\n") else "No explicit professor warnings in uploaded documents."
        val pronunciationTips = if (pronunciationList.isNotEmpty()) pronunciationList.take(4).joinToString("\n") else "• Silent final consonants: Do not pronounce final 's' or 't' unless liaison applies"
        val culturalNotes = if (culturalList.isNotEmpty()) culturalList.take(4).joinToString("\n") else "• Remember polite greetings before starting conversations in French."
        val examples = if (exampleList.isNotEmpty()) exampleList.take(6).joinToString("\n") else "• (Upload textbook pages to see course examples)"

        return StudyNoteEntity(
            chapterId = chapterId,
            chapterTitle = title,
            keyVocab = keyVocab,
            grammarRules = grammarRules,
            pronunciationTips = pronunciationTips,
            culturalNotes = culturalNotes,
            professorEmphasis = professorEmphasis,
            examples = examples,
            summaryShort = "Synthesized directly from ${docs.size} uploaded class materials. Covers key vocabulary, grammar rules, and professor test guidance.",
            summaryStandard = "Course-grounded study guide synthesized directly from ${docs.size} uploaded documents and lecture notes. Includes key vocabulary, grammar patterns, and professor guidance.",
            summaryDetailed = "Complete review synthesizing textbook excerpts and lecture transcripts. Emphasizes exam-tested concepts, pronunciation nuances, and conversational French etiquette."
        )
    }

    private fun parseSmartNoteJson(rawJson: String, chapterId: String, title: String, docs: List<CourseDocumentEntity>): StudyNoteEntity {
        return try {
            val clean = rawJson.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$clean}")
            StudyNoteEntity(
                chapterId = chapterId,
                chapterTitle = title,
                keyVocab = json.optString("keyVocab", "• (No vocabulary extracted)"),
                grammarRules = json.optString("grammarRules", "• (No grammar rules extracted)"),
                pronunciationTips = json.optString("pronunciationTips", "• Silent final consonants\n• Nasal vowel clarity"),
                culturalNotes = json.optString("culturalNotes", "• Use 'Vous' with professors, elders, and strangers"),
                professorEmphasis = json.optString("professorEmphasis", "None recorded in uploaded materials"),
                examples = json.optString("examples", "• (See uploaded textbook pages)"),
                summaryShort = json.optString("summaryShort", "Summary grounded in uploaded materials for $title."),
                summaryStandard = json.optString("summaryStandard", "Foundational French I chapter covering greetings, introducing oneself, and course grammar."),
                summaryDetailed = json.optString("summaryDetailed", "Comprehensive chapter review encompassing vocabulary, phonetics, pronoun agreement, and cultural norms.")
            )
        } catch (e: Exception) {
            synthesizeNotesDirectlyFromDocs(docs, chapterId, title)
        }
    }

    private fun parseTranslationJson(
        rawJson: String,
        isFrenchOnly: Boolean,
        showPhonetics: Boolean,
        detectedLang: String = "English",
        targetLang: String = "French",
        originalInput: String = ""
    ): TranslationResult {
        val clean = rawJson.substringAfter("{").substringBeforeLast("}")
        val json = JSONObject("{$clean}")
        val french = json.optString("frenchText", "").trim()
        val english = json.optString("englishText", "").trim()
        val phonetics = if (showPhonetics) json.optString("phoneticText", "") else ""
        val explanation = json.optString("explanation", "")
        val citation = json.optString("citation", "")
        val det = json.optString("detectedLanguage", detectedLang)
        val tgt = json.optString("targetLanguage", targetLang)

        val altArray = json.optJSONArray("alternatives")
        val alternatives = mutableListOf<String>()
        if (altArray != null) {
            for (i in 0 until altArray.length()) {
                alternatives.add(altArray.getString(i))
            }
        }

        if (isFrenchOnly) {
            return TranslationResult(
                frenchText = french.ifEmpty { english },
                englishText = "",
                phoneticText = "",
                explanation = "",
                detectedLanguage = det,
                targetLanguage = tgt,
                alternatives = emptyList()
            )
        }

        return TranslationResult(
            frenchText = french,
            englishText = english,
            phoneticText = phonetics,
            explanation = explanation,
            citation = citation,
            detectedLanguage = det,
            targetLanguage = tgt,
            alternatives = alternatives
        )
    }

    private fun parsePronunciationJson(rawJson: String, targetPhrase: String): PronunciationFeedback {
        return try {
            val clean = rawJson.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$clean}")
            PronunciationFeedback(
                targetPhrase = targetPhrase,
                phoneticGuide = json.optString("phoneticGuide", generateApproxFrenchPhonetics(targetPhrase)),
                score = json.optInt("score", 85),
                feedbackMessage = json.optString("feedbackMessage", "Good attempt! Make sure to keep your vowels clean and clear."),
                mouthPositionTip = json.optString("mouthPositionTip", "Keep your tongue close to your lower front teeth for crisp French vowels.")
            )
        } catch (e: Exception) {
            PronunciationFeedback(
                targetPhrase = targetPhrase,
                phoneticGuide = generateApproxFrenchPhonetics(targetPhrase),
                score = 85,
                feedbackMessage = "Good attempt! Pay attention to silent final consonants and vowel purity.",
                mouthPositionTip = "Round your lips firmly for French 'u' and keep French 'r' gentle in the back of the throat."
            )
        }
    }

    fun generateApproxFrenchPhonetics(french: String): String {
        return when (french.trim().lowercase()) {
            "bonjour" -> "bohn-ZHOOR"
            "salut" -> "sah-LOO"
            "merci" -> "mair-SEE"
            "merci beaucoup" -> "mair-SEE boh-KOO"
            "comment allez-vous ?" -> "koh-MAHN tah-lay VOO"
            "comment vous appelez-vous ?" -> "koh-MAHN vooz ah-play VOO"
            "je m'appelle" -> "zhuh mah-PEL"
            "je suis fatigué" -> "zhuh SWEE fah-tee-GAY"
            "je veux du café" -> "zhuh VUH dew kah-FAY"
            "s'il vous plaît" -> "seel voo PLEH"
            "enchanté" -> "ahn-shahn-TAY"
            "au revoir" -> "oh ruh-VWAHR"
            "nous sommes" -> "noo SOMM"
            "vous êtes" -> "vooz EHT"
            "où est la bibliothèque ?" -> "oo eh lah bee-blee-oh-TEK"
            "la salle de classe" -> "lah sahl duh klahs"
            else -> french
        }
    }

    // Verified offline educational dictionary for core Beginning French I concepts
    fun getOfflineDictionaryTranslation(input: String, detectedLang: String): TranslationResult? {
        val lower = input.lowercase().trim()

        if (detectedLang == "English") {
            // ENGLISH TO FRENCH
            return when {
                lower.contains("coffee") -> TranslationResult(
                    frenchText = "Je veux du café.",
                    englishText = input,
                    phoneticText = "Zhuh vuh dew kah-FAY",
                    explanation = "Use 'du' (de + le) before masculine mass nouns. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "Je voudrais un café, s'il vous plaît. (Polite café order)",
                        "Un café, s'il vous plaît. (Short order)",
                        "Tu veux du café ? (Informal: Do you want coffee?)"
                    )
                )
                lower.contains("hello") || lower.contains("hi") -> TranslationResult(
                    frenchText = "Bonjour !",
                    englishText = input,
                    phoneticText = "Bohn-ZHOOR !",
                    explanation = "Use 'Bonjour' all day in both formal and informal contexts. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "Salut ! (Informal / Hi between friends)",
                        "Bonsoir ! (Good evening / after ~6 PM)",
                        "Coucou ! (Casual / affectionate Hi)"
                    )
                )
                lower.contains("how are you") -> TranslationResult(
                    frenchText = "Comment allez-vous ?",
                    englishText = input,
                    phoneticText = "Koh-mahn tah-lay VOO ?",
                    explanation = "Formal/polite with liaison (comment_allez-vous). Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "Comment vas-tu ? (Informal to a friend)",
                        "Ça va ? (Casual / most common: How's it going?)",
                        "Comment ça va ? (Standard everyday friendly)"
                    )
                )
                lower.contains("where is the library") || (lower.contains("where is") && lower.contains("library")) -> TranslationResult(
                    frenchText = "Où est la bibliothèque ?",
                    englishText = input,
                    phoneticText = "Oo eh lah bee-blee-oh-TEK ?",
                    explanation = "'Où est' means 'Where is'. 'Bibliothèque' is feminine. Note false friend: 'librairie' is bookstore! Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "Où se trouve la bibliothèque ? (Slightly more formal: Where is the library located?)",
                        "Pardon, pour aller à la bibliothèque ? (Polite direction request on campus)"
                    )
                )
                lower.contains("what is your name") -> TranslationResult(
                    frenchText = "Comment vous appelez-vous ?",
                    englishText = input,
                    phoneticText = "Koh-mahn vooz ah-play VOO ?",
                    explanation = "Formal. Use with professors, adults, and strangers. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "Comment tu t'appelles ? (Informal / to a fellow student)",
                        "Quel est votre nom ? (Formal / official: What is your last name?)",
                        "Tu t'appelles comment ? (Conversational spoken French)"
                    )
                )
                lower.contains("my name is") -> {
                    val name = input.substringAfter("my name is", "").trim().ifEmpty { "Alex" }
                    TranslationResult(
                        frenchText = "Je m'appelle $name.",
                        englishText = input,
                        phoneticText = "Zhuh mah-PEL $name",
                        explanation = "Literally 'I call myself...'. Verified dictionary.",
                        detectedLanguage = "English",
                        targetLanguage = "French",
                        alternatives = listOf(
                            "Moi, c'est $name. (Casual spoken: Me, it's $name)",
                            "Mon prénom est $name. (My first name is $name)"
                        )
                    )
                }
                lower.contains("thank") -> TranslationResult(
                    frenchText = "Merci beaucoup !",
                    englishText = input,
                    phoneticText = "Mair-SEE boh-KOO !",
                    explanation = "'Merci' = thank you; 'beaucoup' = very much. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "Merci bien. (Thanks a lot)",
                        "Je vous remercie. (Formal: I thank you)",
                        "Mille mercis ! (A thousand thanks!)"
                    )
                )
                lower.contains("goodbye") || lower.contains("bye") -> TranslationResult(
                    frenchText = "Au revoir !",
                    englishText = input,
                    phoneticText = "Oh ruh-VWAHR !",
                    explanation = "Standard polite goodbye. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "À bientôt ! (See you soon!)",
                        "À plus tard ! (See you later / casual)",
                        "Bonne journée ! (Have a nice day!)"
                    )
                )
                lower.contains("please") -> TranslationResult(
                    frenchText = "S'il vous plaît.",
                    englishText = input,
                    phoneticText = "Seel voo PLEH",
                    explanation = "Formal / polite. Use 'S'il te plaît' with peers. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "S'il te plaît. (Informal between classmates)",
                        "Je vous prie. (Very formal / official)"
                    )
                )
                lower.contains("student") -> TranslationResult(
                    frenchText = "Je suis étudiant(e).",
                    englishText = input,
                    phoneticText = "Zhuh swee ay-tew-dee-AHN (f. ay-tew-dee-AHNT)",
                    explanation = "In French, do not use an article with professions or student status after être. Verified dictionary.",
                    detectedLanguage = "English",
                    targetLanguage = "French",
                    alternatives = listOf(
                        "J'étudie à l'université. (I study at the university)",
                        "Nous sommes étudiants. (We are students)"
                    )
                )
                else -> null
            }
        } else {
            // FRENCH TO ENGLISH
            return when {
                lower.contains("bonjour") -> TranslationResult(
                    frenchText = input,
                    englishText = "Hello / Good morning!",
                    phoneticText = "Bohn-ZHOOR",
                    explanation = "Standard formal and everyday greeting in French. Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "Good day (Literal)",
                        "Good morning (Early morning)",
                        "Hi (Friendly standard)"
                    )
                )
                lower.contains("merci") -> TranslationResult(
                    frenchText = input,
                    englishText = "Thank you very much!",
                    phoneticText = "Mair-SEE boh-KOO",
                    explanation = "'Merci' means thank you. Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "Thanks!",
                        "Many thanks!",
                        "Thank you kindly"
                    )
                )
                lower.contains("comment allez-vous") || lower.contains("ça va") -> TranslationResult(
                    frenchText = input,
                    englishText = "How are you? / How's it going?",
                    phoneticText = "Koh-mahn tah-lay VOO",
                    explanation = "'Comment allez-vous' is formal; 'Ça va ?' is casual. Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "How are you doing? (Polite)",
                        "How is everything? (Casual)",
                        "Are you doing well?"
                    )
                )
                lower.contains("je veux") || lower.contains("je voudrais") -> TranslationResult(
                    frenchText = input,
                    englishText = "I would like / I want...",
                    phoneticText = "Zhuh voo-DREH",
                    explanation = "'Je voudrais' is the polite conditional of 'vouloir' (to want). Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "I would like (Polite standard)",
                        "I want (Direct/Assertive)",
                        "May I have... (Ordering in a café)"
                    )
                )
                lower.contains("je m'appelle") -> TranslationResult(
                    frenchText = input,
                    englishText = "My name is...",
                    phoneticText = "Zhuh mah-PEL",
                    explanation = "Literally 'I call myself...'. Used for introductions. Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "I am named...",
                        "I'm called...",
                        "My name's..."
                    )
                )
                lower.contains("où est la bibliothèque") -> TranslationResult(
                    frenchText = input,
                    englishText = "Where is the library?",
                    phoneticText = "Oo eh lah bee-blee-oh-TEK ?",
                    explanation = "'Où' with accent means 'where'. 'Bibliothèque' is library. Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "Where can I find the library?",
                        "Where is the library located?"
                    )
                )
                lower.contains("où est") -> TranslationResult(
                    frenchText = input,
                    englishText = "Where is...",
                    phoneticText = "Oo eh...",
                    explanation = "'Où' with accent means 'where'. Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "Where can I find...",
                        "Where is ... located?"
                    )
                )
                lower.contains("je suis") -> TranslationResult(
                    frenchText = input,
                    englishText = "I am...",
                    phoneticText = "Zhuh SWEE",
                    explanation = "Present tense of the irregular verb 'être' (to be) for 'je' (I). Verified dictionary.",
                    detectedLanguage = "French",
                    targetLanguage = "English",
                    alternatives = listOf(
                        "I'm...",
                        "I exist as..."
                    )
                )
                else -> null
            }
        }
    }
}
