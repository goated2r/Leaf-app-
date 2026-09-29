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

@Entity(indices = [Index(value = ["word"], unique = true)])
data class VocabularyWord(@PrimaryKey val id: String, val word: String, val definition: String, val createdAt: Long = System.currentTimeMillis())

@Entity(indices = [Index(value = ["name"], unique = true)])
data class Collection(@PrimaryKey val id: String, val name: String)

@Entity(primaryKeys = ["bookId", "collectionId"], foreignKeys = [
    ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Collection::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("collectionId")])
data class BookCollection(val bookId: String, val collectionId: String)

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
    @Query("SELECT * FROM VocabularyWord ORDER BY word COLLATE NOCASE") fun words(): Flow<List<VocabularyWord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWord(word: VocabularyWord)
    @Query("DELETE FROM VocabularyWord WHERE id = :id") suspend fun deleteWord(id: String)
    @Query("SELECT * FROM Collection ORDER BY name COLLATE NOCASE") fun collections(): Flow<List<Collection>>
    @Insert suspend fun addCollection(collection: Collection)
    @Query("DELETE FROM Collection WHERE id = :id") suspend fun deleteCollection(id: String)
    @Query("SELECT collectionId FROM BookCollection WHERE bookId = :bookId") fun collectionIds(bookId: String): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToCollection(link: BookCollection)
    @Query("DELETE FROM BookCollection WHERE bookId = :bookId AND collectionId = :collectionId") suspend fun removeFromCollection(bookId: String, collectionId: String)
}

@Database(entities = [Book::class, Note::class, VocabularyWord::class, Collection::class, BookCollection::class], version = 4, exportSchema = true)
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

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `VocabularyWord` (`id` TEXT NOT NULL, `word` TEXT NOT NULL, `definition` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_VocabularyWord_word` ON `VocabularyWord` (`word`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `Collection` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Collection_name` ON `Collection` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `BookCollection` (`bookId` TEXT NOT NULL, `collectionId` TEXT NOT NULL, PRIMARY KEY(`bookId`, `collectionId`), FOREIGN KEY(`bookId`) REFERENCES `Book`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`collectionId`) REFERENCES `Collection`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_BookCollection_collectionId` ON `BookCollection` (`collectionId`)")
    }
}
