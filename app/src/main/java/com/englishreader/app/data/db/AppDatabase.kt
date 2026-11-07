package com.englishreader.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromSource(v: BookSource): String = v.name
    @TypeConverter fun toSource(v: String): BookSource = runCatching { BookSource.valueOf(v) }.getOrDefault(BookSource.IMPORTED)
    @TypeConverter fun fromFormat(v: BookFormat): String = v.name
    @TypeConverter fun toFormat(v: String): BookFormat = runCatching { BookFormat.valueOf(v) }.getOrDefault(BookFormat.TXT)
    @TypeConverter fun fromDifficulty(v: Difficulty): String = v.name
    @TypeConverter fun toDifficulty(v: String): Difficulty = runCatching { Difficulty.valueOf(v) }.getOrDefault(Difficulty.UNKNOWN)
    @TypeConverter fun fromPractice(v: PracticeType): String = v.name
    @TypeConverter fun toPractice(v: String): PracticeType = runCatching { PracticeType.valueOf(v) }.getOrDefault(PracticeType.READING)
}

@Database(
    entities = [
        BookEntity::class,
        VocabWordEntity::class,
        LookupCacheEntity::class,
        DailyStatEntity::class,
        GeneratedContentEntity::class,
        PracticeSessionEntity::class,
        ExerciseResultEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun vocabDao(): VocabDao
    abstract fun lookupCacheDao(): LookupCacheDao
    abstract fun statsDao(): StatsDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "english_reader.db")
                // Future versions must add explicit Migration objects here so user data is kept.
                .build()
    }
}
