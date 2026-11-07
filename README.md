# English Reader — Read, Learn & Practice

A free Android app for Arabic speakers who want to improve their English by reading real books.
Tap any word while reading to see its Arabic translation, English definition, pronunciation and
examples, then save it to **My Dictionary**.

**Version 1.1** includes Phase 1 (reading + personal dictionary) and Phase 2 (personalised practice).

---

## Features (Phase 1)

### 1. Free books library
- 30 hand-picked public-domain classics from **Project Gutenberg**, graded **Beginner / Intermediate / Advanced**.
- Search by title, author or category; filter by category and level.
- "Search all free books online" searches the whole Gutenberg catalogue (70,000+ books) through the free Gutendex API. Only public-domain books are shown.
- Book page with cover, author, description, level and category. One tap downloads the book and opens it.
- A short original story is built in, so the reader works the first time you open the app, even offline.

### 2. Import your own books
- Import **PDF, EPUB, TXT and HTML** files using the Android file picker.
- Files are copied into the app's private storage, so they keep working even if the original is moved.
- EPUB title, author and cover are read from the file; PDF text is extracted with PdfBox.
- Scanned PDFs (no text layer) are shown as page images.
- The level of each imported book is estimated automatically (Flesch reading-ease score).
- Remove imported or downloaded books from **Library → My Books**.

### 3. Interactive reading and instant translation
- Tap any word. A bottom sheet opens while the page stays visible, showing:
  - **Arabic translation** (right-to-left)
  - **English definition** and part of speech
  - **Phonetic transcription** (IPA) and a **pronounce** button
  - **Example sentences**, plus the sentence from your book
  - **Translate the sentence** button
  - **Save to My Dictionary**
- Words you already saved are underlined in the text.
- Reading position is saved automatically and restored when you reopen a book.
- Font size, line spacing and reading theme (Light / Sepia / Dark) are adjustable.

**Online and offline translation:**

| Source | Needs internet | Used for |
|---|---|---|
| Free Dictionary API (dictionaryapi.dev) | Yes | Definition, IPA, audio, examples |
| MyMemory translation API | Yes | Arabic translation |
| Google ML Kit on-device translator | Only for a one-time ~30 MB download (Settings) | Arabic translation offline |
| Local lookup cache (Room) | No | Every word you looked up before |
| Android text-to-speech | No | Pronunciation |

When a source is unavailable, the app says so and still lets you save the word.

### 4. My Dictionary
- Each entry has the word, Arabic translation, definition, pronunciation, examples, date added, source book and your personal notes.
- Search (English, Arabic, definition or notes), sort (newest, oldest, A–Z), filter (all, learning, learned).
- Edit, delete, mark as learned, add words manually (with **Auto-fill**).
- **Review** mode: flashcards with simple spaced repetition ("Again" / "I knew it").

### 5. Export
- Export the dictionary (or the filtered list) to **PDF, CSV or TXT** and save it anywhere with the system file picker.
- The PDF is laid out with Android's text engine, so Arabic letters are joined and shown right-to-left. The CSV is UTF-8 with a BOM, so Arabic shows correctly in Excel.

## Features (Phase 2 — Practice)

Open the **Practice** tab. The app picks the words you need most (not yet learned, low mastery, due for review); you can choose other words and your level.

- **Story & questions** — a short story built from your words (highlighted), read aloud on request, followed by comprehension, vocabulary and fill-in-the-blank questions. On-device generation uses your real example sentences, so the English is always natural. With an optional free Google Gemini key (Settings → AI practice) stories and questions are written by AI.
- **Fill in the blanks** — choose the missing word in real sentences.
- **Writing** — *Complete*: type the missing word (inflected forms like "struggling" are accepted). *Write*: write your own sentence; the app checks that you used the word, finds grammar and spelling mistakes with the free LanguageTool service, explains each one, suggests a corrected sentence and shows natural example sentences (or AI explanations in English and Arabic when a Gemini key is set).
- **Speaking** — listen (normal or slow), tap the microphone and speak; Android speech recognition turns your voice into text and every word is coloured green (clear), orange (close) or red (not recognised), with a score and tips.
- **Tracking** — every answer is stored (`practice_sessions`, `exercise_results`) and updates each word's spaced-repetition schedule and mastery level; Progress shows sessions, answers and accuracy.

### Pronunciation (fixed in 1.1)
- The speech engine starts with the app, so the first tapped word is spoken immediately (it was silently dropped in 1.0).
- An **offline female** English voice is chosen automatically; slow online audio files are no longer used. Measured start time on the test emulator: under 50 ms.
- Settings → Pronunciation voice: choose any installed voice, speaking speed, and "Speak the word when I tap it". If the phone has no English voice the app offers to install it.

### Also included
- Bottom navigation: **Home, Library, My Dictionary, Progress, Settings**.
- **Progress**: words saved/learned, reading minutes, day streak, a 7-day chart, books in progress.
- **Settings**: light/dark/system theme, **app language English / العربية** (full RTL layout), reading defaults, your English level, offline Arabic pack, clear cache.
- Layouts adapt to phones, tablets and landscape.
- No account. All data stays on the device.

---

## Architecture

```
app/src/main/java/com/englishreader/app/
├── EnglishReaderApp.kt        Application: creates AppContainer, seeds the sample book
├── AppContainer.kt            Manual dependency injection (single place that wires everything)
├── MainActivity.kt            Single activity, Jetpack Compose
├── data/
│   ├── db/                    Room: entities, DAOs, AppDatabase (+ Phase 2 tables)
│   ├── prefs/                 DataStore settings (theme, font, level…)
│   ├── catalog/               Curated public-domain catalogue
│   ├── net/                   HTTP client, Gutendex, Dictionary API, MyMemory
│   ├── translate/             ML Kit offline translator
│   ├── books/                 BookRepository, TextProcessing, EPUB parser, PDF extractor
│   ├── vocab/                 LookupRepository, VocabRepository, StatsRepository
│   └── export/                PDF / CSV / TXT exporter
├── practice/                  Phase 2: story/question generator, word forms, speech comparer,
│                              LanguageTool + optional Gemini clients, PracticeRepository
├── ui/                        Compose screens, one package per feature, each with its ViewModel
│   ├── home/  library/  reader/  dictionary/  practice/  progress/  settings/
│   ├── navigation/            NavHost + bottom bar
│   ├── components/            Shared UI (covers, badges, empty states)
│   └── theme/                 Material 3 colours, typography, reader themes
└── util/                      Text-to-speech, network helpers
```

**Pattern:** MVVM with unidirectional data flow. Room/DataStore expose `Flow`s → repositories → ViewModels expose `StateFlow` → Compose screens observe them.

**Key design decision:** every book format (Gutenberg text, TXT, HTML, EPUB, PDF) is converted once into the same internal format: a plain-text file of paragraphs. The reader, word lookup, progress tracking and future Phase 2 exercises therefore work the same way for every book.

**Tech stack:** Kotlin 2.0, Jetpack Compose + Material 3, Navigation Compose, Room 2.6, DataStore, Coroutines/Flow, Coil, PdfBox-Android, Google ML Kit Translate. minSdk 26 (Android 8.0), targetSdk 34.

### Database (Room, version 1)

| Table | Purpose |
|---|---|
| `books` | Downloaded and imported books, file paths, reading position and % |
| `vocabulary` | My Dictionary. Also stores review count, ease, interval, next review, mastery level |
| `lookup_cache` | Every lookup result, for instant offline lookups |
| `daily_stats` | Reading seconds, lookups, saves and reviews per day |
| `generated_content` | *Phase 2* — generated sentences, paragraphs and stories |
| `practice_sessions` | *Phase 2* — exercise sessions and scores |
| `exercise_results` | *Phase 2* — each answer, feedback and score, linked to a word |

The schema is exported to `app/schemas/` so future versions can add proper migrations without losing data.

### Updating without losing data
Phase 2 used the tables created in version 1.0, so the database is still version 1 and no migration was needed. Every release is signed with the same key and keeps the application id `com.englishreader.app`, so installing a new APK over the old one keeps all books, words and progress.

---

## Building the project

### Option A — Android Studio (recommended for development)
1. Install **Android Studio** (Koala 2024.1 or newer).
2. **File → Open** and choose this folder. Let Gradle sync (it downloads dependencies the first time).
3. Click **Run ▶** to install on a connected phone or emulator.
4. To build an APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

### Option B — command line
Requires JDK 17 and the Android SDK (set `ANDROID_HOME` or create `local.properties` with `sdk.dir=...`).
```bash
./gradlew testDebugUnitTest      # run the unit tests
./gradlew assembleRelease        # build the signed APK
# output: app/build/outputs/apk/release/app-release.apk
```

### Option C — GitHub Actions (no setup)
Every push to `main` runs `.github/workflows/build.yml`: it runs the tests, builds the signed release APK and attaches it to the run as the artifact **EnglishReader-apk**.

### Signing
The release APK is signed with the key in `keystore/release.jks` (password `englishreader`) so every build installs as an update over the previous one. **Before publishing on Google Play, create your own private key** and update `signingConfigs` in `app/build.gradle.kts`.

---

## Installing the APK on an Android phone
The latest tested APK is always on the repository's **Releases** page:
https://github.com/Adel-Khaled/english-reader/releases — open it on your phone and tap **EnglishReader-v1.0.0.apk**.

1. Or copy `EnglishReader-v1.0.0.apk` to the phone (download it, or send it via USB, Google Drive, Telegram, etc.).
2. Open the file from the **Files / Downloads** app.
3. If Android asks, allow **Install unknown apps** for that app (Settings → Apps → *Files* → Install unknown apps → Allow).
4. Tap **Install**, then **Open**.

Requires Android 8.0 or newer. To update later, install the new APK over the old one: your books and dictionary are kept.

**Tip:** open **Settings → Offline Arabic translation pack → Download** once while on Wi-Fi to translate words without internet.

---

## Tests
Every push runs **26 unit tests** and **14 on-device tests** on an Android 12 emulator in GitHub Actions. Results (`unit-tests.txt`, `device-tests.txt`) and screenshots of every screen (`screenshots.zip`) are attached to each release.

On-device tests (`app/src/androidTest/`) cover the real app end to end: open the sample book → tap the word "curious" → Arabic translation, definition and IPA appear → save → word is in My Dictionary and underlined in the text; Library, book details, Progress, Settings, dark mode and the Arabic (RTL) interface; importing TXT, EPUB and PDF files; downloading a Gutenberg book; online search; live dictionary lookup and caching; downloading the offline Arabic pack and translating offline; exporting a PDF with Arabic; the voice is female/offline and starts speaking in under 3 s on three consecutive taps; story generation and online grammar checking; and a UI walk through Practice (story, question feedback, writing corrections, speaking).

`app/src/test/` contains JVM and Robolectric tests for:
- Gutenberg cleanup, paragraph building, long-paragraph splitting, PDF paragraph rebuilding, HTML → text
- Tap-to-word detection, word normalisation, sentence extraction, difficulty estimation
- EPUB parsing (spine order, metadata, cover)
- Room: saving words (no duplicates), edit / learned / delete, review scheduling, reading progress
- Dictionary API parsing, CSV/TXT export (escaping, Arabic, BOM), dictionary search/sort/filter, streaks

## Credits and licences
- Books: [Project Gutenberg](https://www.gutenberg.org) — public domain in the USA. Search via [Gutendex](https://gutendex.com).
- Definitions: [Free Dictionary API](https://dictionaryapi.dev).
- Online translation: [MyMemory](https://mymemory.translated.net) (free tier, rate-limited).
- Offline translation: Google ML Kit (free, on-device).
