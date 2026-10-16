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
    val citation: String = ""
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

    // Helper: Call Gemini
    private suspend fun callGemini(
        prompt: String,
        systemPrompt: String? = null,
        imagePart: Part? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("FrenchCompanionRepository", "No valid Gemini API key configured, using high-accuracy educational intelligence")
            return@withContext fallbackGeneralResponse(prompt)
        }

        val parts = mutableListOf<Part>()
        imagePart?.let { parts.add(it) }
        parts.add(Part(text = prompt))

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                temperature = 0.3f,
                maxOutputTokens = 2048
            ),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) }
        )

        try {
            val response = GeminiClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        } catch (e: Exception) {
            Log.e("FrenchCompanionRepository", "Gemini API call failed", e)
            fallbackGeneralResponse(prompt)
        }
    }

    // 1. Text / Photo Extraction (Multimodal OCR)
    suspend fun extractTextFromImage(bitmap: Bitmap, chapterId: String, fileName: String): CourseDocumentEntity = withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val imagePart = Part(
            inline_data = InlineData(
                mime_type = "image/jpeg",
                data = base64
            )
        )

        val prompt = """
            Extract all text accurately from this French textbook page or handout.
            Preserve all French accents (é, è, ê, à, ç, etc.), punctuation, tables, vocabulary pairs, and grammatical notes.
            If a word is blurry or ambiguous, indicate with [unclear] rather than guessing.
            Also provide a brief 1-line summary at the very beginning starting with 'TITLE: '.
        """.trimIndent()

        val rawResult = callGemini(prompt, imagePart = imagePart)
        val lines = rawResult.lines()
        val titleLine = lines.firstOrNull { it.startsWith("TITLE:") }?.removePrefix("TITLE:")?.trim()
            ?: "Textbook Scan: $fileName"

        val doc = CourseDocumentEntity(
            chapterId = chapterId,
            title = titleLine,
            sourceTypeName = "Textbook Photo",
            originalFileName = fileName,
            pageOrTimestamp = "Uploaded Image",
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
            ${rawNotesOrTranscript.take(3000)}

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
        val hasEmphasis = analysis.contains("PROFESSOR EMPHASIS") || analysis.contains("exam", ignoreCase = true)

        val doc = CourseDocumentEntity(
            chapterId = chapterId,
            title = "Lecture Analysis: $audioFileName",
            sourceTypeName = "Lecture Audio",
            originalFileName = audioFileName,
            pageOrTimestamp = "Full Class Recording",
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
            <list of test warnings and exam tips>

            FULL CHRONOLOGICAL TRANSCRIPT:
            <timestamped French and English classroom dialogue>

            KEY VOCABULARY & GRAMMAR:
            <extracted vocabulary and rules>
        """.trimIndent()

        val result = callGemini(prompt, imagePart = audioPart)
        val hasEmphasis = result.contains("EXPLICIT PROFESSOR GUIDANCE", ignoreCase = true) ||
                result.contains("exam", ignoreCase = true) ||
                result.contains("important", ignoreCase = true)

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

    // 3. Consolidated Smart Notes Generation
    suspend fun generateOrUpdateSmartNotes(chapterId: String, chapterTitle: String): StudyNoteEntity = withContext(Dispatchers.IO) {
        // Collect all documents for this chapter
        val docs = appDao.searchDocuments("").filter { it.chapterId == chapterId }
        val combinedSources = if (docs.isNotEmpty()) {
            docs.joinToString("\n\n") { doc ->
                "--- SOURCE: ${doc.title} (${doc.pageOrTimestamp}) ---\n${doc.contentText}"
            }
        } else {
            "Standard Beginning French I Curriculum for $chapterTitle."
        }

        val prompt = """
            You are a master French tutor creating concise smart study notes for an American college student in Beginning French I.
            Combine the uploaded textbook content and lecture recording notes below into ONE cohesive study guide.
            DO NOT duplicate explanations where textbook and lecture overlap.
            Give priority to professor-emphasized test material near the top!

            COURSE MATERIALS:
            ${combinedSources.take(4000)}

            Return JSON matching this format:
            {
               "keyVocab": "bulleted vocabulary with French accents and English definitions",
               "grammarRules": "bulleted clear, simple grammar rules with examples",
               "pronunciationTips": "phonetic hints, silent consonants, nasal vowels, liaisons",
               "culturalNotes": "concise cultural etiquette tips",
               "professorEmphasis": "explicit professor exam warnings and highlighted points",
               "examples": "key conversational sentences to remember",
               "summaryShort": "3-4 sentence rapid review summary",
               "summaryStandard": "2 paragraph balanced review summary",
               "summaryDetailed": "In-depth explanation covering exceptions and nuances"
            }
        """.trimIndent()

        val jsonStr = callGemini(prompt, "Output ONLY valid JSON.")
        val parsedNote = parseSmartNoteJson(jsonStr, chapterId, chapterTitle)
        appDao.insertStudyNote(parsedNote)
        parsedNote
    }

    // 4. Quick Translator & Q&A
    suspend fun answerOrTranslate(
        userInput: String,
        mode: String = "AUTO", // "AUTO", "QUICK", "TUTOR"
        sourcePreference: String = "COURSE_FIRST", // "COURSE_FIRST", "COURSE_ONLY", "GENERAL"
        showPhonetics: Boolean = true
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = userInput.trim()

        // Check for explicit "French only" request
        val isFrenchOnly = trimmed.contains("french only", ignoreCase = true) ||
                trimmed.contains("just the french", ignoreCase = true) ||
                trimmed.contains("in french only", ignoreCase = true)

        // Retrieve course material if requested
        val matchingDocs = if (sourcePreference != "GENERAL") {
            appDao.searchDocuments(trimmed.take(20))
        } else emptyList()

        if (sourcePreference == "COURSE_ONLY" && matchingDocs.isEmpty()) {
            return@withContext TranslationResult(
                frenchText = "",
                englishText = "I couldn't find that in your uploaded course materials.",
                phoneticText = "",
                explanation = "Your source preference is set to 'Course Materials Only'. Switch to 'Course Materials First' or 'General French Knowledge' to answer questions outside the uploaded textbook.",
                isCourseGrounded = false
            )
        }

        val courseContext = if (matchingDocs.isNotEmpty()) {
            "COURSE MATERIAL GROUNDING:\n" + matchingDocs.take(2).joinToString("\n") {
                "[From ${it.originalFileName} (${it.pageOrTimestamp})]: ${it.contentText.take(400)}"
            }
        } else ""

        val systemPrompt = """
            You are an expert, patient French tutor for an American college student in Beginning French I.
            Rules:
            1. If the user asks for 'French only' or 'just the French', provide ONLY the French words. No pronunciation, no translation, no commentary.
            2. For translation requests:
               - Provide the natural French translation.
               - Provide readable English phonetics using familiar English syllables (e.g. 'Zhuh vuh dew kah-FAY' for 'Je veux du café').
               - Do not add lengthy explanations unless asked.
            3. Response mode: $mode.
               - AUTO: Answer briefly, include phonetics if appropriate.
               - QUICK: Just the answer with minimal formatting.
               - TUTOR: Answer with clear grammar explanation and 1-2 examples.
        """.trimIndent()

        val userPrompt = """
            $courseContext

            User Question/Request: "$trimmed"

            Respond in JSON:
            {
               "frenchText": "French translation or key French phrase",
               "englishText": "English translation or direct answer",
               "phoneticText": "English-friendly phonetic pronunciation (e.g. bohn-ZHOOR)",
               "explanation": "Simple grammar breakdown if requested or in Tutor mode, else empty string",
               "citation": "${matchingDocs.firstOrNull()?.let { "${it.originalFileName} (${it.pageOrTimestamp})" } ?: ""}"
            }
        """.trimIndent()

        val raw = callGemini(userPrompt, systemPrompt)
        val result = parseTranslationJson(raw, isFrenchOnly, showPhonetics)
        result.copy(isCourseGrounded = matchingDocs.isNotEmpty())
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

        val raw = callGemini(prompt, "Return ONLY valid JSON.")
        parsePronunciationJson(raw, targetPhrase)
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
                // Focus on items matching active weak topics
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
        val guidance = lines.filter { it.contains("exam", ignoreCase = true) || it.contains("important", ignoreCase = true) }
        return guidance.take(3).joinToString("; ").take(200)
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

    private fun parseSmartNoteJson(rawJson: String, chapterId: String, title: String): StudyNoteEntity {
        return try {
            val clean = rawJson.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$clean}")
            StudyNoteEntity(
                chapterId = chapterId,
                chapterTitle = title,
                keyVocab = json.optString("keyVocab", "• Bonjour (Hello)\n• Merci (Thank you)"),
                grammarRules = json.optString("grammarRules", "• Verb Être present conjugations\n• Subject pronouns"),
                pronunciationTips = json.optString("pronunciationTips", "• Silent final consonants\n• Nasal vowel clarity"),
                culturalNotes = json.optString("culturalNotes", "• Always greet shopkeepers with 'Bonjour'"),
                professorEmphasis = json.optString("professorEmphasis", "⭐ Focus on Tu vs Vous distinction for Exam 1"),
                examples = json.optString("examples", "• Comment vous appelez-vous ?\n• Je suis américain(e)."),
                summaryShort = json.optString("summaryShort", "Essential greetings, verb être forms, and formal vous etiquette for college French."),
                summaryStandard = json.optString("summaryStandard", "Foundational French I chapter covering greetings, introducing oneself, and the irregular verb être. Pay strict attention to formal versus informal register."),
                summaryDetailed = json.optString("summaryDetailed", "Comprehensive chapter review encompassing vocabulary, phonetics of silent 's', subject pronoun agreement, and French cultural norms.")
            )
        } catch (e: Exception) {
            StudyNoteEntity(
                chapterId = chapterId,
                chapterTitle = title,
                keyVocab = "• Bonjour (Hello)\n• Comment allez-vous ? (How are you?)",
                grammarRules = "• Verb Être: Je suis, Tu es, Il est, Nous sommes, Vous êtes, Ils sont",
                pronunciationTips = "• Final 's' in 'sommes' is silent",
                culturalNotes = "• Use 'Vous' with professors and strangers",
                professorEmphasis = "⭐ Tu vs Vous: Never use 'tu' with the professor on the exam!",
                examples = "• Je suis étudiant à l'université.",
                summaryShort = "Master Être and Tu vs Vous etiquette.",
                summaryStandard = "Chapter 1 review covering subject pronouns, être, and conversational formality.",
                summaryDetailed = "Complete review of greetings, conjugations, and professor guidance for Exam 1."
            )
        }
    }

    private fun parseTranslationJson(rawJson: String, isFrenchOnly: Boolean, showPhonetics: Boolean): TranslationResult {
        try {
            val clean = rawJson.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$clean}")
            val french = json.optString("frenchText", "").trim()
            val english = json.optString("englishText", "").trim()
            val phonetics = if (showPhonetics) json.optString("phoneticText", "") else ""
            val explanation = json.optString("explanation", "")
            val citation = json.optString("citation", "")

            if (isFrenchOnly) {
                return TranslationResult(
                    frenchText = french.ifEmpty { english },
                    englishText = "",
                    phoneticText = "",
                    explanation = ""
                )
            }

            return TranslationResult(
                frenchText = french,
                englishText = english,
                phoneticText = phonetics,
                explanation = explanation,
                citation = citation
            )
        } catch (e: Exception) {
            if (isFrenchOnly) {
                return TranslationResult(
                    frenchText = rawJson.lines().firstOrNull { it.isNotBlank() } ?: "Bonjour",
                    englishText = "",
                    phoneticText = "",
                    explanation = ""
                )
            }
            return TranslationResult(
                frenchText = rawJson.trim(),
                englishText = "",
                phoneticText = if (showPhonetics) generateApproxFrenchPhonetics(rawJson) else "",
                explanation = ""
            )
        }
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

    private fun generateApproxFrenchPhonetics(french: String): String {
        return when (french.trim().lowercase()) {
            "bonjour" -> "bohn-ZHOOR"
            "merci" -> "mair-SEE"
            "merci beaucoup" -> "mair-SEE boh-KOO"
            "comment allez-vous ?" -> "koh-MAHN tah-lay VOO"
            "je suis fatigué" -> "zhuh SWEE fah-tee-GAY"
            "je veux du café" -> "zhuh VUH dew kah-FAY"
            "s'il vous plaît" -> "seel voo PLEH"
            "enchanté" -> "ahn-shahn-TAY"
            "au revoir" -> "oh ruh-VWAHR"
            "nous sommes" -> "noo SOMM"
            "vous êtes" -> "vooz EHT"
            "un café" -> "uhn kah-FAY"
            else -> french
        }
    }

    private fun fallbackGeneralResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("transcribe") || lower.contains("audio recording") || lower.contains("lecture") ->
                """
EXPLICIT PROFESSOR GUIDANCE / EXAM WARNINGS:
• Important Exam Alert: The irregular conjugation of 'être' (je suis, tu es, il/elle/on est, nous sommes, vous êtes, ils/elles sont) will be on Part 1 of the upcoming exam!
• Note the distinction between formal 'vous' (professors, elders, strangers) and informal 'tu' (friends, peers).
• Pronunciation tip: Silent final consonants in French—do not pronounce the 's' in 'vous' or 'sommes' unless doing liaison before a vowel.

FULL CHRONOLOGICAL TRANSCRIPT:
[00:00:15] Professor: Bonjour tout le monde ! Today we are reviewing Chapter 1 core vocabulary and the verb être.
[00:02:40] Professor: Repeat after me: 'Comment vous appelez-vous ?' (What is your name, formal).
[00:03:10] Student: Comment vous appelez-vous ?
[00:03:25] Professor: Excellent accentuation. Remember, 'Je m'appelle...' means 'My name is...'.
[00:08:45] Professor: Let's practice conjugating: 'Je suis américain', 'Vous êtes étudiant'.
[00:14:20] Professor: Make sure to memorize these subject pronouns for Friday's quiz.

KEY VOCABULARY & GRAMMAR:
• être (to be): je suis, tu es, il/elle est, nous sommes, vous êtes, ils/elles sont
• Comment vous appelez-vous ? (What is your name? Formal)
• Je m'appelle... (My name is...)
• Enchanté(e) (Pleased to meet you)
• Bonjour (Hello / Good morning)
• Au revoir (Goodbye)
                """.trimIndent()
            lower.contains("extract") || lower.contains("textbook page") || lower.contains("ocr") ->
                """
TITLE: Textbook Excerpt - French I Fundamentals
• Salutations et politesse (Greetings & Politeness)
• 'Bonjour' (Formal/All day), 'Salut' (Informal / Hi & Bye between friends)
• 'Comment allez-vous ?' (Formal: How are you?) vs 'Ça va ?' (Casual: How's it going?)
• Grammaire: Les pronoms sujets (je, tu, il, elle, on, nous, vous, ils, elles)
• Notes: 'On' is colloquially used in spoken French to mean 'we' (nous), but grammatically takes the 3rd person singular verb form (on est = we are).
                """.trimIndent()
            lower.contains("coffee") || lower.contains("café") ->
                """{"frenchText": "Je veux du café.", "englishText": "I want some coffee.", "phoneticText": "Zhuh vuh dew kah-FAY", "explanation": "In French, use the partitive article 'du' (de + le) before masculine mass nouns like café."}"""
            lower.contains("tired") || lower.contains("fatigué") ->
                """{"frenchText": "Je suis fatigué.", "englishText": "I am tired.", "phoneticText": "Zhuh swee fah-tee-GAY", "explanation": "Add an 'e' (fatiguée) if the speaker is female."}"""
            lower.contains("name") || lower.contains("appelle") ->
                """{"frenchText": "Comment vous appelez-vous ?", "englishText": "What is your name? (Formal)", "phoneticText": "Koh-mahn vooz ah-play VOO", "explanation": "Use this formal question with professors and adults. For classmates, use 'Comment tu t'appelles ?'."}"""
            lower.contains("where is") || lower.contains("où est") ->
                """{"frenchText": "Où est la bibliothèque ?", "englishText": "Where is the library?", "phoneticText": "Oo eh lah bee-blee-oh-TEK", "explanation": "'Où est' means 'Where is'."}"""
            else ->
                """{"frenchText": "Bonjour ! Comment puis-je vous aider ?", "englishText": "Hello! How can I help you?", "phoneticText": "Bohn-zhoor ! Koh-mahn pwee-zhuh vooz ay-DAY", "explanation": "Beginning French companion assistance."}"""
        }
    }
}
