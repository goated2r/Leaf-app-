package com.leaf.reader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(indices = [Index(value = ["sha256"], unique = true)])
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val format: String,
    val path: String,
    val sha256: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val position: Int = 0,
    val scrollOffset: Int = 0,
    val textOffset: Int = 0,
    val bookmark: Int? = null
)

@Entity(foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)], indices = [Index("bookId")])
data class Note(@PrimaryKey val id: String, val bookId: String, val position: Int, val text: String, val createdAt: Long = System.currentTimeMillis())

@Dao interface LeafDao {
    @Query("SELECT * FROM Book ORDER BY COALESCE(lastReadAt, addedAt) DESC") fun books(): Flow<List<Book>>
    @Query("SELECT * FROM Book WHERE id = :id") fun book(id: String): Flow<Book?>
    @Query("SELECT * FROM Book WHERE sha256 = :hash LIMIT 1") suspend fun byHash(hash: String): Book?
    @Insert suspend fun insert(book: Book)
    @Query("UPDATE Book SET position = :position, textOffset = :offset, lastReadAt = :time WHERE id = :id") suspend fun progress(id: String, position: Int, offset: Int = 0, time: Long = System.currentTimeMillis())
    @Query("UPDATE Book SET bookmark = :position WHERE id = :id") suspend fun bookmark(id: String, position: Int)
    @Query("SELECT * FROM Note WHERE bookId = :id ORDER BY position, createdAt") fun notes(id: String): Flow<List<Note>>
    @Insert suspend fun addNote(note: Note)
    @Query("DELETE FROM Note WHERE id = :id") suspend fun deleteNote(id: String)
}

@Database(entities = [Book::class, Note::class], version = 3, exportSchema = true)
abstract class LeafDatabase : RoomDatabase() { abstract fun dao(): LeafDao }

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Book ADD COLUMN scrollOffset INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Book ADD COLUMN textOffset INTEGER NOT NULL DEFAULT 0")
    }
}
