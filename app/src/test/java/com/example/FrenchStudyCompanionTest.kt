package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.network.GeminiClient
import com.example.network.GeminiException
import com.example.repository.FrenchCompanionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrenchStudyCompanionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: FrenchCompanionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDao = database.appDao()
        repository = FrenchCompanionRepository(appDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Language Detection Tests
    @Test
    fun testLanguageDetectionAccuratelyIdentifiesEnglish() {
        assertEquals("English", repository.detectLanguage("Where is the library?"))
        assertEquals("English", repository.detectLanguage("How do you say hello in French?"))
        assertEquals("English", repository.detectLanguage("I want a coffee, please."))
        assertEquals("English", repository.detectLanguage("What is the professor saying about the exam?"))
    }

    @Test
    fun testLanguageDetectionAccuratelyIdentifiesFrench() {
        assertEquals("French", repository.detectLanguage("Bonjour Madame, où est la bibliothèque ?"))
        assertEquals("French", repository.detectLanguage("Je m'appelle Alex et je suis américain."))
        assertEquals("French", repository.detectLanguage("Comment vous appelez-vous ?"))
        assertEquals("French", repository.detectLanguage("Voici la salle de classe."))
        assertEquals("French", repository.detectLanguage("Je voudrais un café, s'il vous plaît."))
    }

    // 2. Verified Offline Dictionary Translation & Alternatives Tests
    @Test
    fun testOfflineDictionaryEnglishToFrenchWithAlternatives() {
        val result = repository.getOfflineDictionaryTranslation("Where is the library?", "English")
        assertNotNull(result)
        assertEquals("Où est la bibliothèque ?", result!!.frenchText)
        assertEquals("French", result.targetLanguage)
        assertTrue(result.alternatives.isNotEmpty())
        assertTrue(result.explanation.contains("bibliothèque", ignoreCase = true))
        assertTrue(result.phoneticText.isNotBlank())
    }

    @Test
    fun testOfflineDictionaryFrenchToEnglish() {
        val result = repository.getOfflineDictionaryTranslation("Bonjour", "French")
        assertNotNull(result)
        assertTrue(result!!.englishText.contains("Hello", ignoreCase = true))
        assertEquals("English", result.targetLanguage)
        assertTrue(result.alternatives.isNotEmpty())
    }

    // 3. Gemini Security & Failure Handling (NO FAKE RESPONSES)
    @Test
    fun testGeminiMissingKeyThrowsExplicitExceptionWithoutFakeFallback() {
        // Temporarily clear custom key to verify missing key behavior
        GeminiClient.setCustomApiKey("")

        // If key is not configured, calling callGemini must throw MissingApiKeyException
        try {
            runBlocking {
                // Should throw GeminiException.MissingApiKeyException if key is empty/placeholder
                if (!GeminiClient.isKeyConfigured()) {
                    repository.callGemini("Transcribe this classroom lecture")
                    fail("Expected GeminiException when API key is missing")
                }
            }
        } catch (e: GeminiException) {
            // Expected: structured exception thrown without fake fallback hallucination
            assertTrue(e is GeminiException.MissingApiKeyException || e is GeminiException.AuthenticationException)
        }
    }

    // 4. Audio Processing & Size Limits
    @Test
    fun testAudioProcessingRejectsEmptyFile() {
        val emptyBytes = ByteArray(0)
        try {
            runBlocking {
                repository.transcribeAudioFile(emptyBytes, "audio/mp3", "ch1", "empty.mp3")
                fail("Expected IllegalArgumentException for empty audio")
            }
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("empty") == true)
        }
    }

    @Test
    fun testAudioProcessingRejectsOversizedFile() {
        // 21 MB byte array exceeds 20 MB limit
        val oversizedBytes = ByteArray(21 * 1024 * 1024)
        try {
            runBlocking {
                repository.transcribeAudioFile(oversizedBytes, "audio/mp3", "ch1", "large_lecture.mp3")
                fail("Expected IllegalArgumentException for audio > 20MB")
            }
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("20 MB") == true)
        }
    }

    // 5. Database Persistence & Source Grounding
    @Test
    fun testDatabasePersistenceAndDocumentRetrieval() = runBlocking {
        val doc = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Textbook p. 28: Les lieux",
            sourceTypeName = "Textbook Photo",
            originalFileName = "page_28.jpg",
            pageOrTimestamp = "Page 28",
            contentText = "Voici la bibliothèque (library). Voici la salle de classe (classroom).",
            isProfessorEmphasis = false
        )
        val docId = appDao.insertDocument(doc)
        assertTrue(docId > 0)

        val retrieved = appDao.getDocumentsListByChapter("ch1")
        assertEquals(1, retrieved.size)
        assertEquals("Textbook p. 28: Les lieux", retrieved[0].title)
        assertTrue(retrieved[0].contentText.contains("bibliothèque"))
    }

    @Test
    fun testProfessorEmphasisFlaggedInDocuments() = runBlocking {
        val lectureDoc = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Lecture Audio: Oct 14",
            sourceTypeName = "Lecture Audio",
            originalFileName = "lecture_oct14.mp3",
            pageOrTimestamp = "00:15:30",
            contentText = "[00:15:30] Professor: Remember for the exam! The verb être conjugations will be on Part 1.",
            isProfessorEmphasis = true,
            professorGuidance = "Exam alert: Verb être conjugations"
        )
        appDao.insertDocument(lectureDoc)

        val docs = appDao.getDocumentsListByChapter("ch1")
        val emphasized = docs.filter { it.isProfessorEmphasis }
        assertEquals(1, emphasized.size)
        assertEquals("Exam alert: Verb être conjugations", emphasized[0].professorGuidance)
    }

    // 6. Source-Grounded Synthesis without Fabrication
    @Test
    fun testDirectSynthesisFromUploadedDocsWithoutHallucinations() = runBlocking {
        val doc1 = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Textbook p. 28: Les lieux",
            sourceTypeName = "Textbook Photo",
            originalFileName = "p28.jpg",
            pageOrTimestamp = "Page 28",
            contentText = "• la bibliothèque — library\n• la salle de classe — classroom",
            isProfessorEmphasis = false
        )
        val doc2 = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Lecture Recording Oct 14",
            sourceTypeName = "Lecture Audio",
            originalFileName = "lec.mp3",
            pageOrTimestamp = "00:05:00",
            contentText = "PROFESSOR: Warning for Friday's test! Remember that librairie is bookstore, not library!",
            isProfessorEmphasis = true,
            professorGuidance = "Friday's test: librairie vs bibliothèque"
        )
        appDao.insertDocument(doc1)
        appDao.insertDocument(doc2)

        val note = repository.generateOrUpdateSmartNotes("ch1", "Leçon 1")
        assertNotNull(note)
        // Verify key vocabulary extracted from user's document
        assertTrue(note.keyVocab.contains("bibliothèque") || note.summaryShort.contains("2 uploaded"))
        // Verify professor emphasis reflects the actual warning
        assertTrue(note.professorEmphasis.contains("test", ignoreCase = true) || note.professorEmphasis.contains("Warning", ignoreCase = true))
    }

    // 7. Flashcard Extraction from Uploaded Documents
    @Test
    fun testFlashcardExtractionFromDocuments() = runBlocking {
        val doc = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Vocabulary Sheet",
            sourceTypeName = "Pasted Notes",
            originalFileName = "vocab.txt",
            pageOrTimestamp = "Handout",
            contentText = """
                • la bibliothèque — library
                • la salle de classe — classroom
                • l'université — university
            """.trimIndent(),
            isProfessorEmphasis = false
        )
        appDao.insertDocument(doc)

        repository.generateOrUpdateSmartNotes("ch1", "Leçon 1")
        val cards = repository.getFlashcardsForChapter("ch1").first()
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.any { it.frenchText.contains("bibliothèque", ignoreCase = true) })
    }

    // 8. Custom API Key Storage in Settings
    @Test
    fun testCustomApiKeyPersistence() = runBlocking {
        val testKey = "AIzaSyTestKey1234567890"
        repository.setSetting("custom_gemini_api_key", testKey)
        val retrieved = repository.getSetting("custom_gemini_api_key")
        assertEquals(testKey, retrieved)
    }

    // 9. Document Correction & Mistake Fixing
    @Test
    fun testDocumentCorrectionAndMistakeFixing() = runBlocking {
        val initialDoc = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Textbook Scan Draft",
            sourceTypeName = "Textbook Photo",
            originalFileName = "scan_draft.jpg",
            pageOrTimestamp = "Page 28",
            contentText = "• la bibliotheque (misspelled without accent) — library",
            isProfessorEmphasis = false
        )
        val id = appDao.insertDocument(initialDoc)
        val savedDoc = initialDoc.copy(id = id)

        // Student corrects the extraction mistake (fixes accents and updates professor guidance)
        val correctedDoc = savedDoc.copy(
            title = "Textbook p. 28: Campus Places (Corrected)",
            contentText = "• la bibliothèque — library\n• la salle de classe — classroom",
            isProfessorEmphasis = true,
            professorGuidance = "Exam warning: False friend librairie vs bibliothèque"
        )
        repository.updateCourseDocument(correctedDoc)

        val retrieved = appDao.getDocumentsListByChapter("ch1")
        assertEquals(1, retrieved.size)
        assertEquals("Textbook p. 28: Campus Places (Corrected)", retrieved[0].title)
        assertTrue(retrieved[0].contentText.contains("bibliothèque"))
        assertTrue(retrieved[0].isProfessorEmphasis)
        assertEquals("Exam warning: False friend librairie vs bibliothèque", retrieved[0].professorGuidance)
    }

    // 10. Duplicate Upload Prevention
    @Test
    fun testDuplicateUploadPrevention() = runBlocking {
        val doc = CourseDocumentEntity(
            chapterId = "ch1",
            title = "Chapter 1 Vocabulary",
            sourceTypeName = "Pasted Notes",
            originalFileName = "vocab_notes.txt",
            pageOrTimestamp = "Page 1",
            contentText = "• le resto-U — university dining hall",
            isProfessorEmphasis = false
        )
        appDao.insertDocument(doc)

        // Attempting to save duplicate identical content must be caught and prevented
        try {
            repository.savePastedCourseNotes("ch1", "Duplicate Notes", "• le resto-U — university dining hall")
            fail("Expected IllegalArgumentException for duplicate content")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("already") == true || e.message?.contains("Duplicate") == true)
        }
    }

    // 11. Quiz Questions Generated From Knowledge Base Documents
    @Test
    fun testQuizQuestionsGeneratedFromKnowledgeBase() = runBlocking {
        val doc = CourseDocumentEntity(
            chapterId = "ch2",
            title = "Greetings Vocabulary",
            sourceTypeName = "Textbook Photo",
            originalFileName = "greetings.jpg",
            pageOrTimestamp = "Page 15",
            contentText = "• Bonjour — Hello\n• Au revoir — Goodbye",
            isProfessorEmphasis = true,
            professorGuidance = "Exam question: Formal greetings"
        )
        appDao.insertDocument(doc)

        repository.generateOrUpdateSmartNotes("ch2", "Greetings Chapter")
        val quizItems = appDao.getQuizItemsForChapter("ch2")
        assertTrue(quizItems.isNotEmpty())
        assertTrue(quizItems.any { it.questionText.contains("Hello") || it.correctAnswer.contains("Bonjour") })
    }
}
