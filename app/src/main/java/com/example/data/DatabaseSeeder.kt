package com.example.data

object DatabaseSeeder {
    suspend fun seedInitialData(dao: AppDao) {
        val chapters = listOf(
            ChapterEntity(
                id = "ch1",
                number = 1,
                title = "Leçon 1: Paroles (Lieux, Matières, Nationalités)",
                description = "Campus places, academic subjects, nationalities, and preposition à + ville."
            ),
            ChapterEntity(
                id = "ch2",
                number = 2,
                title = "Salutations et Premiers Pas",
                description = "Greetings, subject pronouns, and the verb être."
            ),
            ChapterEntity(
                id = "ch3",
                number = 3,
                title = "La Famille et Les Amis",
                description = "Adjective agreement, BANGS adjectives, and the verb avoir."
            ),
            ChapterEntity(
                id = "ch4",
                number = 4,
                title = "Le Temps Libre et Les Loisirs",
                description = "Regular -er verbs, negation with ne...pas, aller and faire."
            ),
            ChapterEntity(
                id = "ch5",
                number = 5,
                title = "Au Café et En Ville",
                description = "Food, drinks, partitive articles (du, de la, des), and ordering."
            )
        )
        dao.insertChapters(chapters)

        // Seed Course Documents directly from the user's textbook pages (pp. 28-33)
        val documents = listOf(
            CourseDocumentEntity(
                chapterId = "ch1",
                title = "Textbook pp. 28–30: Les lieux & Les matières",
                sourceTypeName = "Textbook PDF",
                originalFileName = "lecon_1_paroles_lieux_matieres.pdf",
                pageOrTimestamp = "Pages 28–30",
                contentText = """
                    LEÇON 1: PAROLES — LES LIEUX (Campus places)
                    • Voici la salle de classe (classroom)
                    • Voici la cité universitaire / la cité-U (university residence hall / campus housing)
                    • Voici le restaurant universitaire / le resto-U (university dining hall)
                    • Voici la bibliothèque (library)
                    
                    AUTRES MOTS UTILES:
                    • l'amphithéâtre (l'amphi) (m.) — lecture hall
                    • le bureau — office
                    • l'école (f.) — school
                    • le gymnase — gymnasium
                    • le laboratoire (le labo) — lab
                    • la librairie — bookstore (Note false friend: NOT library, that's bibliothèque!)
                    • la salle de classe — classroom

                    LES MATIÈRES (Academic subjects):
                    À la faculté des lettres et sciences humaines, on étudie:
                    • la littérature, la linguistique, les langues (f.) étrangères
                    • l'allemand (m.), l'anglais (m.), le chinois (m.), l'espagnol (m.), l'italien (m.), le japonais (m.)
                    • l'histoire (f.), la géographie, la philosophie, la psychologie, la sociologie
                    À la faculté des sciences, on étudie:
                    • les mathématiques (les maths) (f.)
                    • l'informatique (computer science)
                    • les sciences naturelles: la biologie, la chimie, la géologie, la physique
                    Autres: le commerce (business), le cours (class), le droit (law), l'économie (f.), l'ingénierie (f.) (engineering)
                """.trimIndent(),
                isProfessorEmphasis = false
            ),
            CourseDocumentEntity(
                chapterId = "ch1",
                title = "Textbook pp. 31–33: Les pays, nationalités & les loisirs",
                sourceTypeName = "Textbook PDF",
                originalFileName = "lecon_1_nationalites_loisirs.pdf",
                pageOrTimestamp = "Pages 31–33",
                contentText = """
                    LES PAYS ET LES NATIONALITÉS:
                    • la France — français, française
                    • les États-Unis — américain, américaine
                    • l'Allemagne — allemand, allemande
                    • l'Espagne — espagnol, espagnole
                    • l'Angleterre — anglais, anglaise
                    • le Canada — canadien, canadienne
                    • le Mexique — mexicain, mexicaine
                    • la Chine — chinois, chinoise
                    • le Liban — libanais, libanaise
                    • l'Italie — italien, italienne
                    • le Japon — japonais, japonaise
                    • le Sénégal — sénégalais, sénégalaise
                    • la Suisse — suisse
                    • la Tunisie — tunisien, tunisienne

                    PRONONCEZ BIEN !
                    Masculine vs feminine forms of nationalities:
                    Adding -e to masculine form makes them feminine. The final consonant is then pronounced:
                    • allemand -> allemande (d is pronounced)
                    • anglais -> anglaise (s becomes /z/ sound)
                    • américain [ɛ̃] -> américaine [ɛn] (denasalizes vowel, 'n' is pronounced)
                    • canadien -> canadienne (-ien doubles n and becomes [jɛn])

                    MOTS CLÉS:
                    The preposition à + ville (city):
                    • J'habite à Genève. (I live in Geneva.)
                    • Vous allez à Montréal. (You are going to Montreal.)

                    LES LOISIRS (Hobbies):
                    • La musique: le jazz, la musique classique, la musique country, le rap, le rock
                    • Le sport: le basket-ball, le football, le football américain, le jogging, le ski, le tennis
                    • Le cinéma: les films d'amour, les films d'aventure, les films d'horreur, les films de science-fiction
                """.trimIndent(),
                isProfessorEmphasis = true,
                professorGuidance = "Exam 1 Focus: Preposition à + ville; Pronunciation of nationality endings; Distinction between la bibliothèque (library) and la librairie (bookstore)!"
            ),
            CourseDocumentEntity(
                chapterId = "ch1",
                title = "Lecture Audio: Prof. Martin on Leçon 1 Campus & Nationalities",
                sourceTypeName = "Lecture Audio",
                originalFileName = "lecture_prof_martin_lecon_1.m4a",
                pageOrTimestamp = "00:08:14 – 00:35:10",
                contentText = """
                    [00:08:14] PROFESSOR: "Class, pay attention! On the midterm, I always put the false friend 'la librairie' vs 'la bibliothèque'. 'La librairie' is a BOOKSTORE where you buy books. 'La bibliothèque' is the LIBRARY where you study. Do not lose points on this!"
                    [00:15:20] PROFESSOR: "Remember this for the exam: with cities, always use 'à'. 'J'habite à Paris', 'J'étudie à Montréal'. Never use 'en' or 'au' with city names."
                    [00:22:45] PROFESSOR: "In nationality pronunciation: in 'français' and 'américain', the 's' and 'n' are silent in the masculine form. But in the feminine ('française', 'américaine'), the consonant sounds clearly!"
                """.trimIndent(),
                isProfessorEmphasis = true,
                professorGuidance = "Exam 1 alert: à + city; False friend librairie (bookstore) vs bibliothèque (library); Nationality pronunciation."
            )
        )
        for (doc in documents) {
            dao.insertDocument(doc)
        }

        // Consolidated Smart Notes for Chapter 1
        val ch1Notes = StudyNoteEntity(
            chapterId = "ch1",
            chapterTitle = "Leçon 1: Paroles (Lieux, Matières, Nationalités)",
            keyVocab = "• la salle de classe (classroom)\n• la cité-U (campus residence)\n• le resto-U (dining hall)\n• la bibliothèque (library - false friend!)\n• la librairie (bookstore)\n• l'amphithéâtre / l'amphi (lecture hall)\n• l'informatique (computer science)\n• les langues étrangères (foreign languages)",
            grammarRules = "• Preposition 'à + ville': Always use 'à' before cities (J'habite à Paris, Vous allez à Montréal).\n• Nationality agreement: Add -e for feminine (américain -> américaine, français -> française).\n• Invariable nationalities: Words ending in -e don't change (suisse, belge).",
            pronunciationTips = "• Final consonants: Silent in masculine (français, américain), clearly sounded in feminine (française, américaine).\n• Vowel denasalization: 'américain' has nasal [ɛ̃], 'américaine' sounds like [ɛn].",
            culturalNotes = "• Campus abbreviations: French students constantly say 'le resto-U', 'la cité-U', 'l'amphi', and 'les maths'.\n• Shop etiquette: Say 'Bonjour' upon entering a librairie or café.",
            professorEmphasis = "⭐ PROFESSOR'S MIDTERM EMPHASIS:\n1. 'La librairie' = bookstore. 'La bibliothèque' = library!\n2. Always 'à + ville' (à Paris, à Genève).\n3. Pronounce final consonant in feminine nationalities, silent in masculine.",
            examples = "• J'habite à Montréal et j'étudie l'informatique.\n• Voici la bibliothèque universitaire.\n• Il est américain, elle est américaine.",
            summaryShort = "Master campus locations (resto-U, bibliothèque vs librairie), academic subjects, nationalities with masculine/feminine pronunciation rules, and preposition 'à' with cities.",
            summaryStandard = "Leçon 1 introduces university life vocabulary. Essential false friend: la librairie is a bookstore, while la bibliothèque is the library. Nationalities add -e in feminine form which causes the silent final consonant to be pronounced. Use 'à' for all cities.",
            summaryDetailed = "Comprehensive coverage of Leçon 1: Paroles. Focuses on university vocabulary, subject faculties (lettres, sciences), nationality adjectives and flags, and the phonology of nasal vowel denasalization in feminine adjective endings. Professor Martin flagged false friends and city prepositions for Exam 1."
        )
        dao.insertStudyNote(ch1Notes)

        // Seed Flashcards from the actual textbook
        val flashcards = listOf(
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "la bibliothèque",
                englishText = "the library (False friend: NOT bookstore!)",
                phoneticText = "lah bee-blee-oh-TEK",
                exampleSentence = "J'étudie à la bibliothèque universitaire.",
                status = "NEW"
            ),
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "la librairie",
                englishText = "the bookstore",
                phoneticText = "lah lee-breh-REE",
                exampleSentence = "J'achète un livre à la librairie.",
                status = "NEW"
            ),
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "le restaurant universitaire (le resto-U)",
                englishText = "the university dining hall",
                phoneticText = "luh res-toh OO",
                exampleSentence = "On mange au resto-U à midi.",
                status = "NEW"
            ),
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "l'informatique (f.)",
                englishText = "computer science",
                phoneticText = "lan-for-mah-TEEK",
                exampleSentence = "Mon ami étudie l'informatique.",
                status = "NEW"
            ),
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "américain / américaine",
                englishText = "American (m. / f.)",
                phoneticText = "ah-may-ree-KAN / ah-may-ree-KEN",
                exampleSentence = "Il est américain, elle est américaine.",
                status = "NEW"
            ),
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "J'habite à...",
                englishText = "I live in... (used with cities)",
                phoneticText = "zhah-BEET ah",
                exampleSentence = "J'habite à Montréal.",
                status = "NEW"
            ),
            FlashcardEntity(
                chapterId = "ch1",
                frenchText = "la salle de classe",
                englishText = "the classroom",
                phoneticText = "lah sal duh KLASS",
                exampleSentence = "Voici la salle de classe de français.",
                status = "NEW"
            )
        )
        dao.insertFlashcards(flashcards)

        // Seed Quiz Items
        val quizItems = listOf(
            QuizItemEntity(
                chapterId = "ch1",
                topic = "Campus Locations & False Friends",
                questionType = "MULTIPLE_CHOICE",
                questionText = "Where do you go to study or borrow books on campus?",
                optionsJson = "[\"la bibliothèque\", \"la librairie\", \"le resto-U\", \"l'amphi\"]",
                correctAnswer = "la bibliothèque",
                explanation = "Remember! 'La bibliothèque' is the library. 'La librairie' is a bookstore where you purchase books.",
                isProfessorEmphasis = true
            ),
            QuizItemEntity(
                chapterId = "ch1",
                topic = "Preposition with Cities",
                questionType = "FILL_BLANK",
                questionText = "Complete the sentence: 'J'habite ______ Genève.'",
                optionsJson = "[\"à\", \"en\", \"au\", \"de\"]",
                correctAnswer = "à",
                explanation = "Before any city name, always use the preposition 'à'.",
                isProfessorEmphasis = true
            ),
            QuizItemEntity(
                chapterId = "ch1",
                topic = "Nationalities Pronunciation",
                questionType = "MULTIPLE_CHOICE",
                questionText = "In which form is the final 's' sounded as a /z/ sound?",
                optionsJson = "[\"anglaise (f.)\", \"anglais (m.)\", \"Both m. and f.\", \"Neither\"]",
                correctAnswer = "anglaise (f.)",
                explanation = "In 'anglais' (m.), the final 's' is silent. In 'anglaise' (f.), adding the 'e' causes the 's' to be pronounced like /z/.",
                isProfessorEmphasis = true
            ),
            QuizItemEntity(
                chapterId = "ch1",
                topic = "Academic Subjects",
                questionType = "TRANSLATE_EN_FR",
                questionText = "How do you say 'computer science' in French?",
                optionsJson = "[\"l'informatique\", \"l'information\", \"l'ordinateur\", \"la computation\"]",
                correctAnswer = "l'informatique",
                explanation = "'Computer science' in French is 'l'informatique' (feminine noun).",
                isProfessorEmphasis = false
            ),
            QuizItemEntity(
                chapterId = "ch1",
                topic = "Campus Abbreviations",
                questionType = "MULTIPLE_CHOICE",
                questionText = "What is the common student abbreviation for 'le restaurant universitaire'?",
                optionsJson = "[\"le resto-U\", \"la cité-U\", \"l'amphi\", \"le labo\"]",
                correctAnswer = "le resto-U",
                explanation = "French college students refer to the university dining hall as 'le resto-U'.",
                isProfessorEmphasis = false
            )
        )
        dao.insertQuizItems(quizItems)

        // Seed App Settings
        dao.setSetting(AppSettingEntity("source_preference", "COURSE_FIRST"))
        dao.setSetting(AppSettingEntity("show_phonetics", "true"))
        dao.setSetting(AppSettingEntity("exam_date", "November 14, 2026"))
        dao.setSetting(AppSettingEntity("exam_chapters", "ch1,ch2"))
    }
}
