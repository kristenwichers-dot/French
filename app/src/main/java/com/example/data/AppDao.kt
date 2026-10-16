package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Chapters
    @Query("SELECT * FROM chapters ORDER BY number ASC")
    fun getAllChapters(): Flow<List<ChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    // Course Documents
    @Query("SELECT * FROM course_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<CourseDocumentEntity>>

    @Query("SELECT * FROM course_documents WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    fun getDocumentsByChapter(chapterId: String): Flow<List<CourseDocumentEntity>>

    @Query("SELECT * FROM course_documents WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    suspend fun getDocumentsListByChapter(chapterId: String): List<CourseDocumentEntity>

    @Query("SELECT * FROM course_documents WHERE title LIKE '%' || :query || '%' OR contentText LIKE '%' || :query || '%'")
    suspend fun searchDocuments(query: String): List<CourseDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: CourseDocumentEntity): Long

    @Update
    suspend fun updateDocument(document: CourseDocumentEntity)

    @Delete
    suspend fun deleteDocument(document: CourseDocumentEntity)

    @Query("SELECT * FROM course_documents WHERE chapterId = :chapterId AND originalFileName = :fileName LIMIT 1")
    suspend fun findDocumentByChapterAndFileName(chapterId: String, fileName: String): CourseDocumentEntity?

    // Study Notes
    @Query("SELECT * FROM study_notes WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getStudyNoteForChapter(chapterId: String): StudyNoteEntity?

    @Query("SELECT * FROM study_notes WHERE chapterId = :chapterId LIMIT 1")
    fun observeStudyNoteForChapter(chapterId: String): Flow<StudyNoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNote(note: StudyNoteEntity)

    // Quiz Items
    @Query("SELECT * FROM quiz_items WHERE chapterId = :chapterId")
    suspend fun getQuizItemsForChapter(chapterId: String): List<QuizItemEntity>

    @Query("SELECT * FROM quiz_items WHERE isProfessorEmphasis = 1")
    suspend fun getProfessorEmphasizedQuizItems(): List<QuizItemEntity>

    @Query("SELECT * FROM quiz_items")
    suspend fun getAllQuizItems(): List<QuizItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizItems(items: List<QuizItemEntity>)

    // Quiz Attempts
    @Query("SELECT * FROM quiz_attempts ORDER BY timestamp DESC")
    fun getAllQuizAttempts(): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizAttempt(attempt: QuizAttemptEntity)

    // Weak Topics
    @Query("SELECT * FROM weak_topics WHERE isResolved = 0 ORDER BY missCount DESC")
    fun getActiveWeakTopics(): Flow<List<WeakTopicEntity>>

    @Query("SELECT * FROM weak_topics WHERE topicName = :topicName LIMIT 1")
    suspend fun getWeakTopicByName(topicName: String): WeakTopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeakTopic(topic: WeakTopicEntity)

    @Update
    suspend fun updateWeakTopic(topic: WeakTopicEntity)

    @Query("UPDATE weak_topics SET isResolved = 1 WHERE id = :id")
    suspend fun markWeakTopicResolved(id: Long)

    // Flashcards
    @Query("SELECT * FROM flashcards WHERE chapterId = :chapterId")
    fun getFlashcardsForChapter(chapterId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE status != 'KNOWN'")
    fun getFlashcardsNeedingReview(): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(card: FlashcardEntity)

    // Saved Bookmarks
    @Query("SELECT * FROM saved_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<SavedBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: SavedBookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: SavedBookmarkEntity)

    // Settings
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)

    // Clear all user data for reset/testing
    @Query("DELETE FROM course_documents")
    suspend fun clearDocuments()

    @Query("DELETE FROM quiz_attempts")
    suspend fun clearQuizAttempts()
}
