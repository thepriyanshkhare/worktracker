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
        ExerciseLogEntity::class,
        SleepEntity::class,
        StepsEntity::class
    ],
    version = 5,
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
    abstract fun sleepDao(): SleepDao
    abstract fun stepsDao(): StepsDao

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

        /** Adds a `category` column to medications (groups Today into Medications/Meals/Coffee/Tea,
         *  and Trends into Medicine/Food) and the sleep_log table. Existing medication rows default
         *  to category='medication', so nothing already scheduled changes group. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN category TEXT NOT NULL DEFAULT 'medication'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sleep_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        bedTime TEXT NOT NULL,
                        wakeTime TEXT NOT NULL,
                        hours REAL NOT NULL,
                        quality INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds an optional photoPath column to dose_log, so a food/coffee/tea/meal entry can
         *  carry a user-attached photo. Null for every existing row and for medication/non-photo
         *  entries going forward. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dose_log ADD COLUMN photoPath TEXT")
            }
        }

        /** Adds a `source` column to weight_log (tags manual vs. Health-Connect-synced rows;
         *  every existing row defaults to 'manual', so nothing already logged changes meaning)
         *  and the steps_log table for Health Connect step-count sync. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weight_log ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS steps_log (
                        date TEXT NOT NULL PRIMARY KEY,
                        stepCount INTEGER NOT NULL,
                        syncedAtIso TEXT NOT NULL
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}
