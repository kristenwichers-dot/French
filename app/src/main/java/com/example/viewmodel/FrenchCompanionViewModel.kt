package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.FrenchSpeechEngine
import com.example.data.*
import com.example.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen {
    TRANSLATE,
    MY_CLASS,
    STUDY,
    QUIZ_ME,
    ASK_ANYTHING,
    SETTINGS
}

data class LiveTranscriptItem(
    val id: Long = System.currentTimeMillis(),
    val spokenText: String = "",
    val translatedText: String = "",
    val spokenLang: String = "English",
    val targetLang: String = "French",
    val french: String = "",
    val english: String = "",
    val phonetics: String = "",
    val alternatives: List<String> = emptyList(),
    val explanation: String = "",
    val timestamp: String = "Just now"
)

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sender: String, // "USER" or "TUTOR"
    val frenchText: String = "",
    val englishText: String = "",
    val phoneticText: String = "",
    val explanation: String = "",
    val citation: String = ""
)

class FrenchCompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FrenchCompanionRepository(database.appDao())
    val speechEngine = FrenchSpeechEngine(application)

    // Navigation matching user's exact markup: Translate, My Class, Study, Quiz Me, Ask Anything, Settings
    private val _currentScreen = MutableStateFlow(Screen.TRANSLATE)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val screenStack = mutableListOf<Screen>()

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        return if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            true
        } else {
            if (_currentScreen.value != Screen.TRANSLATE) {
                _currentScreen.value = Screen.TRANSLATE
                true
            } else false
        }
    }

    // Chapters & Selection
    val chapters: StateFlow<List<ChapterEntity>> = repository.chapters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChapterId = MutableStateFlow("ch1")
    val selectedChapterId: StateFlow<String> = _selectedChapterId.asStateFlow()

    fun selectChapter(chapterId: String) {
        _selectedChapterId.value = chapterId
        loadNotesForSelectedChapter()
    }

    // Course Documents
    val documents: StateFlow<List<CourseDocumentEntity>> = repository.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDocument = MutableStateFlow<CourseDocumentEntity?>(null)
    val selectedDocument: StateFlow<CourseDocumentEntity?> = _selectedDocument.asStateFlow()

    fun viewDocument(doc: CourseDocumentEntity?) {
        _selectedDocument.value = doc
    }

    // Study Notes
    private val _currentStudyNote = MutableStateFlow<StudyNoteEntity?>(null)
    val currentStudyNote: StateFlow<StudyNoteEntity?> = _currentStudyNote.asStateFlow()

    private val _noteLength = MutableStateFlow("SUPER_SHORT") // "SUPER_SHORT", "STANDARD", "DETAILED"
    val noteLength: StateFlow<String> = _noteLength.asStateFlow()

    fun setNoteLength(length: String) {
        _noteLength.value = length
    }

    fun loadNotesForSelectedChapter() {
        viewModelScope.launch {
            val chapterId = _selectedChapterId.value
            val note = database.appDao().getStudyNoteForChapter(chapterId)
            if (note != null) {
                _currentStudyNote.value = note
            } else {
                val chapter = chapters.value.find { it.id == chapterId }
                val title = chapter?.title ?: "Chapter Notes"
                val generated = repository.generateOrUpdateSmartNotes(chapterId, title)
                _currentStudyNote.value = generated
            }
        }
    }

    fun regenerateNotes() {
        viewModelScope.launch {
            val chapterId = _selectedChapterId.value
            val chapter = chapters.value.find { it.id == chapterId }
            val title = chapter?.title ?: "Chapter Notes"
            val generated = repository.generateOrUpdateSmartNotes(chapterId, title)
            _currentStudyNote.value = generated
        }
    }

    // Weak Topics & Stats
    val weakTopics: StateFlow<List<WeakTopicEntity>> = repository.weakTopics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizAttempts: StateFlow<List<QuizAttemptEntity>> = repository.quizAttempts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedBookmarks: StateFlow<List<SavedBookmarkEntity>> = repository.savedBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings
    private val _sourcePreference = MutableStateFlow("COURSE_FIRST") // "COURSE_FIRST", "COURSE_ONLY", "GENERAL"
    val sourcePreference: StateFlow<String> = _sourcePreference.asStateFlow()

    private val _showPhonetics = MutableStateFlow(true)
    val showPhonetics: StateFlow<Boolean> = _showPhonetics.asStateFlow()

    private val _translationMode = MutableStateFlow("AUTO") // "AUTO", "QUICK", "TUTOR"
    val translationMode: StateFlow<String> = _translationMode.asStateFlow()

    fun setSourcePreference(pref: String) {
        _sourcePreference.value = pref
        viewModelScope.launch { repository.setSetting("source_preference", pref) }
    }

    fun setShowPhonetics(show: Boolean) {
        _showPhonetics.value = show
        viewModelScope.launch { repository.setSetting("show_phonetics", show.toString()) }
    }

    fun setTranslationMode(mode: String) {
        _translationMode.value = mode
    }

    // Ask Tutor / Quick Answers Chat
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "TUTOR",
                frenchText = "Bonjour ! Je suis votre tuteur de français.",
                englishText = "Hello! I am your French tutor. Ask for a translation, pronunciation, or grammar explanation grounded in your course materials.",
                phoneticText = "Bohn-zhoor ! Zhuh swee vohtr tuh-TUR duh frahn-SAY"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(sender = "USER", englishText = text)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            val result = repository.answerOrTranslate(
                userInput = text,
                mode = _translationMode.value,
                sourcePreference = _sourcePreference.value,
                showPhonetics = _showPhonetics.value
            )
            val tutorMsg = ChatMessage(
                sender = "TUTOR",
                frenchText = result.frenchText,
                englishText = result.englishText,
                phoneticText = result.phoneticText,
                explanation = result.explanation,
                citation = result.citation
            )
            _chatMessages.value = _chatMessages.value + tutorMsg
            _isChatLoading.value = false
        }
    }

    fun translateText(
        text: String,
        addExplanation: Boolean,
        onResult: (TranslationResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.answerOrTranslate(
                userInput = text,
                mode = if (addExplanation) "TUTOR" else "QUICK",
                sourcePreference = _sourcePreference.value,
                showPhonetics = _showPhonetics.value
            )
            onResult(result)
        }
    }

    // Live French Screen State
    private val _liveTranscripts = MutableStateFlow<List<LiveTranscriptItem>>(emptyList())
    val liveTranscripts: StateFlow<List<LiveTranscriptItem>> = _liveTranscripts.asStateFlow()

    private val _liveMode = MutableStateFlow("LISTEN_TRANSLATE") // "LISTEN_TRANSLATE" or "TALK_TUTOR"
    val liveMode: StateFlow<String> = _liveMode.asStateFlow()

    fun setLiveMode(mode: String) {
        _liveMode.value = mode
    }

    fun startLiveListening(languageDirection: String = "AUTO") {
        val speechLang = when (languageDirection) {
            "ENGLISH_TO_FRENCH" -> "en-US"
            "FRENCH_TO_ENGLISH" -> "fr-FR"
            else -> "auto"
        }
        speechEngine.startListening(speechLang) { speechHeard ->
            if (speechHeard.isNotBlank()) {
                handleLiveSpeech(speechHeard)
            }
        }
    }

    fun stopLiveListening() {
        speechEngine.stopListening()
    }

    fun handleLiveSpeech(spokenText: String) {
        viewModelScope.launch {
            val translation = repository.answerOrTranslate(
                userInput = spokenText,
                mode = if (_liveMode.value == "TALK_TUTOR") "TUTOR" else "QUICK",
                showPhonetics = _showPhonetics.value
            )
            val isInputEnglish = translation.detectedLanguage == "English"
            val newItem = LiveTranscriptItem(
                spokenText = spokenText,
                translatedText = if (isInputEnglish) translation.frenchText else translation.englishText,
                spokenLang = translation.detectedLanguage,
                targetLang = translation.targetLanguage,
                french = translation.frenchText,
                english = if (isInputEnglish) spokenText else translation.englishText,
                phonetics = translation.phoneticText,
                alternatives = translation.alternatives,
                explanation = translation.explanation
            )
            _liveTranscripts.value = listOf(newItem) + _liveTranscripts.value
        }
    }

    fun clearLiveTranscripts() {
        _liveTranscripts.value = emptyList()
    }

    fun saveTranscriptAsDocument() {
        viewModelScope.launch {
            if (_liveTranscripts.value.isEmpty()) return@launch
            val content = _liveTranscripts.value.joinToString("\n\n") {
                "French: ${it.french}\nEnglish: ${it.english}"
            }
            val doc = CourseDocumentEntity(
                chapterId = _selectedChapterId.value,
                title = "Live French Recording Session",
                sourceTypeName = "Lecture Audio",
                originalFileName = "live_session_${System.currentTimeMillis()}.txt",
                pageOrTimestamp = "Live Capture",
                contentText = content,
                isProfessorEmphasis = false
            )
            database.appDao().insertDocument(doc)
        }
    }

    // Interactive Quiz Engine
    private val _quizQuestions = MutableStateFlow<List<QuizItemEntity>>(emptyList())
    val quizQuestions: StateFlow<List<QuizItemEntity>> = _quizQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _selectedAnswer = MutableStateFlow<String?>(null)
    val selectedAnswer: StateFlow<String?> = _selectedAnswer.asStateFlow()

    private val _isAnswerSubmitted = MutableStateFlow(false)
    val isAnswerSubmitted: StateFlow<Boolean> = _isAnswerSubmitted.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    private val _missedQuestions = mutableListOf<QuizItemEntity>()

    private val _isQuizCompleted = MutableStateFlow(false)
    val isQuizCompleted: StateFlow<Boolean> = _isQuizCompleted.asStateFlow()

    fun startQuiz(preset: String) {
        viewModelScope.launch {
            val list = repository.getOrGenerateQuiz(_selectedChapterId.value, preset)
            _quizQuestions.value = list
            _currentQuestionIndex.value = 0
            _selectedAnswer.value = null
            _isAnswerSubmitted.value = false
            _quizScore.value = 0
            _missedQuestions.clear()
            _isQuizCompleted.value = false
            navigateTo(Screen.QUIZ_ME)
        }
    }

    fun selectQuizAnswer(answer: String) {
        if (!_isAnswerSubmitted.value) {
            _selectedAnswer.value = answer
        }
    }

    fun submitQuizAnswer() {
        val currentQ = _quizQuestions.value.getOrNull(_currentQuestionIndex.value) ?: return
        val userAns = _selectedAnswer.value ?: return

        _isAnswerSubmitted.value = true
        val isCorrect = userAns.trim().equals(currentQ.correctAnswer.trim(), ignoreCase = true)

        if (isCorrect) {
            _quizScore.value += 1
        } else {
            _missedQuestions.add(currentQ)
        }
    }

    fun nextQuizQuestion() {
        if (_currentQuestionIndex.value + 1 < _quizQuestions.value.size) {
            _currentQuestionIndex.value += 1
            _selectedAnswer.value = null
            _isAnswerSubmitted.value = false
        } else {
            _isQuizCompleted.value = true
            // Record quiz result
            viewModelScope.launch {
                repository.recordQuizResults(
                    chapterId = _selectedChapterId.value,
                    score = _quizScore.value,
                    total = _quizQuestions.value.size,
                    missedQuestions = _missedQuestions
                )
            }
        }
    }

    // Flashcards
    val flashcards: StateFlow<List<FlashcardEntity>> = repository.flashcards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentFlashcardIndex = MutableStateFlow(0)
    val currentFlashcardIndex: StateFlow<Int> = _currentFlashcardIndex.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped: StateFlow<Boolean> = _isCardFlipped.asStateFlow()

    fun flipCard() {
        _isCardFlipped.value = !_isCardFlipped.value
    }

    fun nextCard() {
        _isCardFlipped.value = false
        if (flashcards.value.isNotEmpty()) {
            _currentFlashcardIndex.value = (_currentFlashcardIndex.value + 1) % flashcards.value.size
        }
    }

    fun prevCard() {
        _isCardFlipped.value = false
        if (flashcards.value.isNotEmpty()) {
            val total = flashcards.value.size
            _currentFlashcardIndex.value = (_currentFlashcardIndex.value - 1 + total) % total
        }
    }

    fun markCardKnown(card: FlashcardEntity) {
        viewModelScope.launch {
            repository.updateFlashcardStatus(card, "KNOWN")
            nextCard()
        }
    }

    fun markCardStillLearning(card: FlashcardEntity) {
        viewModelScope.launch {
            repository.updateFlashcardStatus(card, "STILL_LEARNING")
            nextCard()
        }
    }

    // Pronunciation Coach
    private val _targetPronunciationPhrase = MutableStateFlow("Bonjour Madame")
    val targetPronunciationPhrase: StateFlow<String> = _targetPronunciationPhrase.asStateFlow()

    private val _pronunciationFeedback = MutableStateFlow<PronunciationFeedback?>(null)
    val pronunciationFeedback: StateFlow<PronunciationFeedback?> = _pronunciationFeedback.asStateFlow()

    fun setTargetPronunciation(phrase: String) {
        _targetPronunciationPhrase.value = phrase
        _pronunciationFeedback.value = null
    }

    fun evaluateUserVoice(spokenText: String) {
        viewModelScope.launch {
            val feedback = repository.evaluatePronunciation(_targetPronunciationPhrase.value, spokenText)
            _pronunciationFeedback.value = feedback
        }
    }

    // Textbook Image & Audio Upload
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadStatusMessage = MutableStateFlow<String?>("Uploading & indexing course materials...")
    val uploadStatusMessage: StateFlow<String?> = _uploadStatusMessage.asStateFlow()

    fun uploadTextbookImage(bitmap: Bitmap, fileName: String) {
        _isUploading.value = true
        _uploadStatusMessage.value = "Extracting French text with Gemini & preserving accents..."
        viewModelScope.launch {
            try {
                repository.extractTextFromImage(bitmap, _selectedChapterId.value, fileName)
                _uploadStatusMessage.value = "Textbook page added! Updating study guide..."
                val chapter = chapters.value.find { it.id == _selectedChapterId.value }
                val title = chapter?.title ?: "Chapter Notes"
                val updatedNote = repository.generateOrUpdateSmartNotes(_selectedChapterId.value, title)
                _currentStudyNote.value = updatedNote
                _uploadStatusMessage.value = "Textbook page added & study guide updated!"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun uploadAndTranscribeAudio(audioBytes: ByteArray, mimeType: String, fileName: String) {
        _isUploading.value = true
        _uploadStatusMessage.value = "Transcribing $fileName with Gemini & extracting exam warnings..."
        viewModelScope.launch {
            try {
                repository.transcribeAudioFile(audioBytes, mimeType, _selectedChapterId.value, fileName)
                _uploadStatusMessage.value = "Updating study guide with lecture notes..."
                val chapter = chapters.value.find { it.id == _selectedChapterId.value }
                val title = chapter?.title ?: "Chapter Notes"
                val updatedNote = repository.generateOrUpdateSmartNotes(_selectedChapterId.value, title)
                _currentStudyNote.value = updatedNote
                _uploadStatusMessage.value = "Lecture added to study sources & study guide updated!"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun savePastedCourseNotes(title: String, content: String) {
        _isUploading.value = true
        _uploadStatusMessage.value = "Indexing pasted text into study sources..."
        viewModelScope.launch {
            try {
                repository.savePastedCourseNotes(_selectedChapterId.value, title, content)
                val chapter = chapters.value.find { it.id == _selectedChapterId.value }
                val noteTitle = chapter?.title ?: "Chapter Notes"
                val updatedNote = repository.generateOrUpdateSmartNotes(_selectedChapterId.value, noteTitle)
                _currentStudyNote.value = updatedNote
                _uploadStatusMessage.value = "Notes indexed into study guide!"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun uploadLectureTranscript(notes: String, fileName: String) {
        _isUploading.value = true
        viewModelScope.launch {
            try {
                repository.analyzeLectureNotes(_selectedChapterId.value, notes, fileName)
                val chapter = chapters.value.find { it.id == _selectedChapterId.value }
                val title = chapter?.title ?: "Chapter Notes"
                val updatedNote = repository.generateOrUpdateSmartNotes(_selectedChapterId.value, title)
                _currentStudyNote.value = updatedNote
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun saveCustomStudyGuide(updatedNote: StudyNoteEntity) {
        viewModelScope.launch {
            repository.saveCustomStudyNote(updatedNote)
            _currentStudyNote.value = updatedNote
        }
    }

    fun pasteIntoStudyGuide(targetSection: String, pastedText: String) {
        val current = _currentStudyNote.value ?: return
        val updated = when (targetSection) {
            "VOCAB" -> current.copy(keyVocab = "${current.keyVocab}\n• $pastedText".trim())
            "GRAMMAR" -> current.copy(grammarRules = "${current.grammarRules}\n• $pastedText".trim())
            "PROFESSOR" -> current.copy(professorEmphasis = "${current.professorEmphasis}\n• $pastedText".trim())
            "PRONUNCIATION" -> current.copy(pronunciationTips = "${current.pronunciationTips}\n• $pastedText".trim())
            "EXAMPLES" -> current.copy(examples = "${current.examples}\n• $pastedText".trim())
            "SUMMARY" -> current.copy(
                summaryShort = "${current.summaryShort} $pastedText".trim(),
                summaryStandard = "${current.summaryStandard}\n\n$pastedText".trim(),
                summaryDetailed = "${current.summaryDetailed}\n\n$pastedText".trim()
            )
            else -> current.copy(summaryStandard = "${current.summaryStandard}\n\n$pastedText".trim())
        }
        saveCustomStudyGuide(updated)
    }

    // Homework Help
    private val _homeworkAnswer = MutableStateFlow<String?>(null)
    val homeworkAnswer: StateFlow<String?> = _homeworkAnswer.asStateFlow()

    private val _isSolvingHomework = MutableStateFlow(false)
    val isSolvingHomework: StateFlow<Boolean> = _isSolvingHomework.asStateFlow()

    fun solveHomework(question: String, style: String) {
        _isSolvingHomework.value = true
        viewModelScope.launch {
            val ans = repository.solveHomework(question, style)
            _homeworkAnswer.value = ans
            _isSolvingHomework.value = false
        }
    }

    // Bookmarks
    fun saveBookmark(title: String, french: String, english: String, category: String, note: String = "") {
        viewModelScope.launch {
            repository.saveBookmark(title, french, english, category, note)
        }
    }

    fun deleteBookmark(bookmark: SavedBookmarkEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun resolveWeakTopic(id: Long) {
        viewModelScope.launch {
            repository.markWeakTopicResolved(id)
        }
    }

    // Audio Playback
    fun speakFrench(text: String, speed: Float? = null) {
        speechEngine.speak(text, speed)
    }

    init {
        // Initial notes load
        loadNotesForSelectedChapter()
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.destroy()
    }
}
