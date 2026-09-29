package com.septuary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        MedicationEntity::class,
        DoseLogEntity::class,
        WeightEntity::class,
        GlucoseEntity::class,
        GoalEntity::class,
        FlagEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun weightDao(): WeightDao
    abstract fun glucoseDao(): GlucoseDao
    abstract fun goalDao(): GoalDao
    abstract fun flagDao(): FlagDao

    companion object {
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
                .build()
        }
    }
}
