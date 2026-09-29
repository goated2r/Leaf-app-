package com.leaf.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.leaf.reader.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val Forest = Color(0xFF183C30)
private val Parchment = Color(0xFFF5EEDC)
private val Brass = Color(0xFFB89759)

class MainActivity : ComponentActivity() {
    private val database by lazy { Room.databaseBuilder(applicationContext, LeafDatabase::class.java, "leaf.db").build() }
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

@Composable private fun Reader(book: Book, dao: LeafDao, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var location by remember(book.id) { mutableIntStateOf(book.position) }
    var count by remember(book.id) { mutableIntStateOf(0) }
    var chapters by remember(book.id) { mutableStateOf<List<String>>(emptyList()) }
    var page by remember(book.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var side by remember { mutableStateOf(false) }
    var addingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    val notes by dao.notes(book.id).collectAsState(initial = emptyList())
    fun move(to: Int) {
        val next = to.coerceIn(0, (count - 1).coerceAtLeast(0))
        location = next
        scope.launch { dao.progress(book.id, next) }
    }
    LaunchedEffect(book.id) {
        runCatching { if(book.format == "epub") { chapters = ReaderContent.chapters(File(book.path)); count = chapters.size } }
            .onFailure { failure = it.message }
    }
    LaunchedEffect(book.id, location) {
        if(book.format == "pdf") runCatching { ReaderContent.pdfPage(File(book.path), location) }
            .onSuccess { page = it.first; count = it.second }.onFailure { failure = it.message }
    }
    Column(Modifier.fillMaxSize().background(Parchment)) {
        Row(Modifier.fillMaxWidth().background(Forest).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Parchment) }
            Text(book.title, Modifier.weight(1f), maxLines = 1, color = Parchment)
            IconButton(onClick = { side = true }) { Icon(Icons.Default.Spa, "Reader sidebar", tint = Brass) }
        }
        Box(Modifier.fillMaxSize().pointerInput(count, location) {
            var drag = 0f
            detectHorizontalDragGestures(onDragEnd = { if(drag < -40) move(location + 1) else if(drag > 40) move(location - 1); drag = 0f }, onHorizontalDrag = { _, delta -> drag += delta })
        }) {
            if (failure != null) Text(failure ?: "Unable to read file", Modifier.align(Alignment.Center).padding(24.dp), color = Forest)
            else if(book.format == "pdf") page?.let { bitmap -> Image(bitmap.asImageBitmap(), "PDF page ${location + 1}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
            else Text(chapters.getOrNull(location) ?: "Loading…", Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), color = Color(0xFF302D26), fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 31.sp)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Forest), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { move(location - 1) }, enabled = location > 0) { Text("Previous") }
                Text("${location + 1} / $count", color = Parchment)
                TextButton(onClick = { move(location + 1) }, enabled = location + 1 < count) { Text("Next") }
            }
        }
    }
    if (side) ModalBottomSheet(onDismissRequest = { side = false }, containerColor = Forest) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Reading place", fontSize = 23.sp, fontFamily = FontFamily.Serif)
            Text("${if(book.format == "pdf") "Page" else "Chapter"} ${location + 1} of $count")
            Button(onClick = { scope.launch { dao.bookmark(book.id, location) } }) { Text("Set bookmark here") }
            book.bookmark?.let { TextButton(onClick = { move(it); side = false }) { Text("Go to bookmark: ${it + 1}") } }
            TextButton(onClick = { addingNote = true }) { Text("Add note here") }
            Text("Notes", color = Brass)
            notes.forEach { note -> TextButton(onClick = { move(note.position); side = false }) { Text("${note.position + 1} · ${note.text}", maxLines = 2) } }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (addingNote) AlertDialog(onDismissRequest = { addingNote = false }, title = { Text("Note at ${location + 1}") }, text = { TextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Your note") }) }, confirmButton = {
        TextButton(onClick = { if(noteText.isNotBlank()) scope.launch { dao.addNote(Note(UUID.randomUUID().toString(), book.id, location, noteText.trim())); noteText = ""; addingNote = false } }) { Text("Save") }
    })
}
