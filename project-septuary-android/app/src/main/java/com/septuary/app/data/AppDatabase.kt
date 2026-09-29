package com.septuary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        MedicationEntity::class,
        DoseLogEntity::class,
        WeightEntity::class,
        GlucoseEntity::class,
        GoalEntity::class,
        FlagEntity::class,
        ExerciseLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun weightDao(): WeightDao
    abstract fun glucoseDao(): GlucoseDao
    abstract fun goalDao(): GoalDao
    abstract fun flagDao(): FlagDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        /** Adds the exercise_log table. Existing meds/weight/glucose/goals/flags data is untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercise_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        type TEXT NOT NULL,
                        minutes INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Opens (or creates) the encrypted database using [passphrase] as the SQLCipher key.
         * A wrong passphrase throws when the DB is first touched — that's how PIN verification
         * works, with no separate "is this the right PIN" check needed.
         */
        fun open(context: Context, passphrase: CharArray): AppDatabase {
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "septuary_encrypted.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
