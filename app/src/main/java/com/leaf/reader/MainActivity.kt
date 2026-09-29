package com.leaf.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.leaf.reader.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Forest = Color(0xFF183C30)
private val Parchment = Color(0xFFF5EEDC)
private val Brass = Color(0xFFB89759)

class MainActivity : ComponentActivity() {
    private val database by lazy { Room.databaseBuilder(applicationContext, LeafDatabase::class.java, "leaf.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build() }
    private val repository by lazy { ImportRepository(this, database.dao()) }
    private val error = mutableStateOf<String?>(null)
    private val selected = mutableStateOf<String?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching { repository.import(uri) }.onSuccess { selected.value = it.id }.onFailure { error.value = it.message }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receive(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Brass, background = Forest, surface = Forest, onBackground = Parchment, onSurface = Parchment)) {
                val books by database.dao().books().collectAsState(initial = emptyList())
                val history by database.dao().history().collectAsState(initial = emptyList())
                val id by selected
                var section by remember { mutableStateOf("Home") }
                if (id != null) {
                    val book by database.dao().book(id!!).collectAsState(initial = null)
                    book?.let { Reader(it, database.dao(), onBack = { selected.value = null }) }
                } else Scaffold(
                    bottomBar = { NavigationBar(containerColor = Color(0xFF10291F)) {
                        listOf("Home", "Library", "Discover", "Articles", "History").forEach { label ->
                            NavigationBarItem(selected = section == label, onClick = { section = label }, label = { Text(label, fontSize = 10.sp) }, icon = { Icon(when(label) { "Home" -> Icons.Default.Home; "Library" -> Icons.Default.MenuBook; "Discover" -> Icons.Default.Search; "Articles" -> Icons.Default.Article; else -> Icons.Default.History }, null) })
                        }
                    } }
                ) { inset ->
                    Column(Modifier.fillMaxSize().padding(inset).background(Forest).padding(20.dp)) {
                        Text("LEAF", fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Parchment)
                        Text("Read deeply. Return tomorrow.", color = Brass)
                        Spacer(Modifier.height(24.dp))
                        when(section) {
                            "Home", "Library" -> {
                                Text(if(section == "Home") "Continue reading" else "Your library", fontSize = 24.sp, fontFamily = FontFamily.Serif)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")) }) { Text("Import EPUB or PDF") }
                                LazyColumn { items(books) { book ->
                                    Card(onClick = { selected.value = book.id }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF254D3D))) {
                                        Column(Modifier.padding(18.dp)) {
                                            Text(book.title, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                                            Text(book.author, color = Brass)
                                            Text("${book.format.uppercase()} · ${if(book.format == "pdf") "Page" else "Chapter"} ${book.position + 1}")
                                        }
                                    }
                                } }
                            }
                            "History" -> {
                                Text("Reading history", fontSize = 24.sp, fontFamily = FontFamily.Serif)
                                LazyColumn { items(history) { entry ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF254D3D))) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(entry.title, fontFamily = FontFamily.Serif, fontSize = 19.sp)
                                                Text("${entry.author} · ${entry.type.uppercase()} · ${java.text.DateFormat.getDateInstance().format(java.util.Date(entry.lastReadAt))}", color = Brass)
                                            }
                                            IconButton(onClick = { lifecycleScope.launch { database.dao().removeHistory(entry.id) } }) { Icon(Icons.Default.Delete, "Remove ${entry.title} from history") }
                                        }
                                    }
                                } }
                            }
                            else -> Text("$section is planned for a later milestone. Your offline library and reader are available now.", fontFamily = FontFamily.Serif, fontSize = 19.sp)
                        }
                    }
                }
                error.value?.let { message -> AlertDialog(onDismissRequest = { error.value = null }, confirmButton = { TextButton(onClick = { error.value = null }) { Text("OK") } }, title = { Text("Could not import") }, text = { Text(message) }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); receive(intent) }
    private fun receive(intent: Intent?) {
        @Suppress("DEPRECATION")
        val uri: android.net.Uri? = when(intent?.action) { Intent.ACTION_VIEW -> intent.data; Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM); else -> null }
        if (uri is android.net.Uri) lifecycleScope.launch {
            runCatching { repository.import(uri) }.onSuccess { selected.value = it.id }.onFailure { error.value = it.message }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Reader(book: Book, dao: LeafDao, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var location by remember(book.id) { mutableIntStateOf(book.position) }
    var textOffset by remember(book.id) { mutableIntStateOf(book.textOffset) }
    var count by remember(book.id) { mutableIntStateOf(0) }
    var chapters by remember(book.id) { mutableStateOf<List<String>>(emptyList()) }
    var page by remember(book.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pdfText by remember(book.id) { mutableStateOf<String?>(null) }
    var pdfReflow by remember(book.id) { mutableStateOf(false) }
    var pdfModeChosen by remember(book.id) { mutableStateOf(false) }
    var side by remember { mutableStateOf(false) }
    var addingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var wordBank by remember { mutableStateOf(false) }
    var wordInput by remember { mutableStateOf("") }
    var definitionInput by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    val notes by dao.notes(book.id).collectAsState(initial = emptyList())
    val words by dao.words().collectAsState(initial = emptyList())
    val collections by dao.collections().collectAsState(initial = emptyList())
    val collectionIds by dao.collectionIds(book.id).collectAsState(initial = emptyList())
    var newCollection by remember { mutableStateOf("") }
    fun move(to: Int) {
        val next = to.coerceIn(0, (count - 1).coerceAtLeast(0))
        location = next
        textOffset = 0
        scope.launch { dao.recordRead(book, next, 0) }
    }
    LaunchedEffect(book.id) {
        runCatching { if(book.format == "epub") { chapters = ReaderContent.chapters(File(book.path)); count = chapters.size } }
            .onFailure { failure = it.message }
    }
    LaunchedEffect(book.id, location) {
        if(book.format == "pdf") {
            pdfText = null
            runCatching { ReaderContent.pdfPage(File(book.path), location) }
                .onSuccess { page = it.first; count = it.second }.onFailure { failure = it.message }
            pdfText = runCatching { ReaderContent.reflowablePdfText(File(book.path), location) }.getOrNull()
            if (!pdfModeChosen) pdfReflow = pdfText != null
        }
    }
    Box(Modifier.fillMaxSize().background(Parchment)) {
        if (failure != null) Text(failure ?: "Unable to read file", Modifier.align(Alignment.Center).padding(24.dp), color = Forest)
        else if (book.format == "epub") {
            EpubPage(
                text = chapters.getOrNull(location) ?: "",
                offset = textOffset,
                onOffset = { next -> textOffset = next; scope.launch { dao.recordRead(book, location, next) } },
                onNextChapter = { move(location + 1) },
                onPreviousChapter = { move(location - 1) },
                hasNext = location + 1 < count,
                hasPrevious = location > 0
            )
        } else if (pdfReflow && pdfText != null) {
            EpubPage(
                text = pdfText ?: "", offset = textOffset,
                onOffset = { next -> textOffset = next; scope.launch { dao.recordRead(book, location, next) } },
                onNextChapter = { move(location + 1) }, onPreviousChapter = { move(location - 1) },
                hasNext = location + 1 < count, hasPrevious = location > 0
            )
        } else {
            page?.let { bitmap -> Image(bitmap.asImageBitmap(), "PDF page ${location + 1}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().weight(1f).pointerInput(location, count) { detectTapGestures { move(location - 1) } })
                Spacer(Modifier.weight(3f))
                Box(Modifier.fillMaxHeight().weight(1f).pointerInput(location, count) { detectTapGestures { move(location + 1) } })
            }
        }
        IconButton(onClick = { side = true }, modifier = Modifier.align(Alignment.CenterEnd).background(Forest, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))) {
            Icon(Icons.Default.Spa, "Open reader sidebar", tint = Brass)
        }
    }
    if (side) ModalBottomSheet(onDismissRequest = { side = false }, containerColor = Forest) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            TextButton(onClick = { side = false; onBack() }) { Text("Back to library") }
            Text("Reading place", fontSize = 23.sp, fontFamily = FontFamily.Serif)
            Text("${if(book.format == "pdf") "Page" else "Chapter"} ${location + 1} of $count")
            if (book.format == "pdf") {
                Text(if (pdfText == null) "Original layout: this page cannot be safely reflowed" else "PDF reading mode")
                if (pdfText != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Leaf text mode")
                    Switch(checked = pdfReflow, onCheckedChange = { pdfReflow = it; pdfModeChosen = true })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { move(location - 1); side = false }, enabled = location > 0) { Text("Previous") }
                Text("${location + 1} / $count", color = Parchment)
                TextButton(onClick = { move(location + 1); side = false }, enabled = location + 1 < count) { Text("Next") }
            }
            Button(onClick = { scope.launch { dao.bookmark(book.id, location) } }) { Text("Set bookmark here") }
            book.bookmark?.let { TextButton(onClick = { move(it); side = false }) { Text("Go to bookmark: ${it + 1}") } }
            TextButton(onClick = { addingNote = true }) { Text("Add note here") }
            Text("Notes", color = Brass)
            notes.forEach { note -> TextButton(onClick = { move(note.position); side = false }) { Text("${note.position + 1} · ${note.text}", maxLines = 2) } }
            TextButton(onClick = { wordBank = true }) { Text("Word Bank") }
            Text("Collections", color = Brass)
            collections.forEach { collection ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = collection.id in collectionIds, onCheckedChange = { checked -> scope.launch {
                        if (checked) dao.addToCollection(BookCollection(book.id, collection.id))
                        else dao.removeFromCollection(book.id, collection.id)
                    } })
                    Text(collection.name)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(value = newCollection, onValueChange = { newCollection = it }, label = { Text("New collection") }, modifier = Modifier.weight(1f), singleLine = true)
                TextButton(onClick = { val name = newCollection.trim(); if (name.isNotEmpty()) scope.launch {
                    runCatching { dao.addCollection(Collection(UUID.randomUUID().toString(), name)) }
                    newCollection = ""
                } }) { Text("Add") }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (addingNote) AlertDialog(onDismissRequest = { addingNote = false }, title = { Text("Note at ${location + 1}") }, text = { TextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Your note") }) }, confirmButton = {
        TextButton(onClick = { if(noteText.isNotBlank()) scope.launch { dao.addNote(Note(UUID.randomUUID().toString(), book.id, location, noteText.trim())); noteText = ""; addingNote = false } }) { Text("Save") }
    })
    if (wordBank) AlertDialog(onDismissRequest = { wordBank = false }, title = { Text("Word Bank") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            TextField(value = wordInput, onValueChange = { wordInput = it }, label = { Text("Word") }, singleLine = true)
            TextField(value = definitionInput, onValueChange = { definitionInput = it }, label = { Text("Definition") })
            TextButton(onClick = { val word = wordInput.trim(); val meaning = definitionInput.trim(); if (word.isNotEmpty() && meaning.isNotEmpty()) scope.launch {
                dao.putWord(VocabularyWord(UUID.randomUUID().toString(), word, meaning)); wordInput = ""; definitionInput = ""
            } }) { Text("Save word") }
            words.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.word} — ${entry.definition}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { dao.deleteWord(entry.id) } }) { Icon(Icons.Default.Delete, "Delete ${entry.word}") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { wordBank = false }) { Text("Done") } })
}

@Composable private fun EpubPage(
    text: String,
    offset: Int,
    onOffset: (Int) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 30.dp)) {
        val width = with(density) { maxWidth.toPx() }.toInt()
        val height = with(density) { maxHeight.toPx() }.toInt()
        val fontPx = with(density) { 20.sp.toPx() }
        val pages by produceState<List<PageSlice>>(initialValue = emptyList(), text, width, height, fontPx) {
            value = withContext(Dispatchers.Default) { EpubPagination.paginate(text, width, height, fontPx) }
        }
        val index = EpubPagination.pageForOffset(pages, offset)
        fun turn(direction: Int) {
            val next = index + direction
            when {
                next in pages.indices -> onOffset(pages[next].start)
                direction > 0 && hasNext -> onNextChapter()
                direction < 0 && hasPrevious -> onPreviousChapter()
            }
        }
        var drag by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize()
            .pointerInput(index, pages, hasNext, hasPrevious) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (drag < -45f) turn(1) else if (drag > 45f) turn(-1); drag = 0f },
                    onHorizontalDrag = { _, delta -> drag += delta }
                )
            }
            .pointerInput(index, pages, hasNext, hasPrevious) {
                detectTapGestures { position ->
                    if (position.x < size.width * 0.25f) turn(-1)
                    else if (position.x > size.width * 0.75f) turn(1)
                }
            }
        ) {
            val slice = pages.getOrNull(index)
            Text(
                if (slice == null) "Loading…" else text.substring(slice.start, slice.end),
                modifier = Modifier.fillMaxSize(), color = Color(0xFF302D26),
                fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 29.sp
            )
        }
    }
}
