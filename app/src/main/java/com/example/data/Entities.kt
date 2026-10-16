package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val number: Int,
    val title: String,
    val description: String
)

@Entity(tableName = "course_documents")
data class CourseDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: String,
    val title: String,
    val sourceTypeName: String, // "Textbook Photo", "Textbook PDF", "Lecture Audio", "Slides Handout", "Homework"
    val originalFileName: String,
    val pageOrTimestamp: String,
    val contentText: String,
    val isProfessorEmphasis: Boolean = false,
    val professorGuidance: String = "", // e.g. "Exam focus: Être conjugations", "Homework p.14"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey val chapterId: String,
    val chapterTitle: String,
    val keyVocab: String,
    val grammarRules: String,
    val pronunciationTips: String,
    val culturalNotes: String,
    val professorEmphasis: String,
    val examples: String,
    val summaryShort: String,
    val summaryStandard: String,
    val summaryDetailed: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_items")
data class QuizItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: String,
    val topic: String, // "Greetings", "Verb Être", "Gender of Nouns", "Numbers", etc.
    val questionType: String, // "MULTIPLE_CHOICE", "FILL_BLANK", "TRANSLATE_EN_FR", "TRANSLATE_FR_EN", "CONJUGATION"
    val questionText: String,
    val optionsJson: String, // JSON array of strings
    val correctAnswer: String,
    val explanation: String,
    val isProfessorEmphasis: Boolean = false
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val score: Int,
    val total: Int,
    val chapterId: String,
    val missedTopicsJson: String
)

@Entity(tableName = "weak_topics")
data class WeakTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicName: String,
    val category: String, // "Verb Conjugation", "Gender & Articles", "Spelling & Accents", "Pronunciation", "Vocabulary"
    val missCount: Int = 1,
    val lastPracticed: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: String,
    val frenchText: String,
    val englishText: String,
    val phoneticText: String,
    val exampleSentence: String = "",
    val status: String = "NEW", // "NEW", "STILL_LEARNING", "KNOWN"
    val lastReviewed: Long = 0,
    val reviewCount: Int = 0
)

@Entity(tableName = "saved_bookmarks")
data class SavedBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val frenchText: String,
    val englishText: String,
    val category: String, // "Phrase", "Grammar Rule", "Professor Tip", "Note Highlight"
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
