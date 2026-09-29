# LEAF architecture and implementation contract

LEAF is a native Android application. The core reader uses Kotlin and Android rendering APIs; no WebView, browser, PWA or website is used for reading. Internet access is optional for local reading.

## Target structure

```
app/
  core/design/       color, type, icons, Compose components
  core/database/     Room entities, DAOs, explicit migrations
  core/storage/      private files, integrity, import and cleanup
  core/reader/       EPUB package parsing, page layout, PDF rendering
  feature/home/      continue reading and daily goal
  feature/library/   books, collection membership, imports
  feature/reader/    immersive page, sidebar, notes, highlights
  feature/discover/  book metadata adapters and lawful downloads
  feature/articles/  source adapters, provenance and article reader
  feature/history/   sessions, days and retained history records
  feature/backup/    encrypted export, Google account/Drive adapter
  feature/tutor/     online-only AI provider
  feature/settings/  appearance, storage and institutional access
  widget/            Android home screen widget
```

The current first milestone has a narrower source layout under `data/` and `MainActivity.kt`. Code moves into the proposed packages as features mature. Room is the local source of truth. Imported files are copied into private storage; database rows refer to stable owned paths. Each schema change must provide a migration and a migration test. DataStore stores appearance and daily-target preferences. WorkManager schedules backup and eligible downloads. ViewModels expose Flow-backed UI state, with I/O in repositories on background dispatchers.

## Reader positions

EPUB positions are the spine chapter index plus a character offset in the extracted chapter text. Layout-derived page boundaries depend on viewport and typography; on a new layout the saved character offset maps to the containing page. The current parser extracts text and loses rich EPUB formatting and images, so this is not yet a premium-format EPUB engine. A future EPUB engine should preserve DOM structure, images and stable identifiers, and migrate saved positions rather than silently losing them.

PDF original layout uses Android PdfRenderer and the original page index. Reflow mode must only activate when extraction and layout confidence are adequate, preserving page provenance. Otherwise Leaf offers original layout. No DRM circumvention.

## Source adapters

Book discovery: an interface for Open Library/Google Books and a separate public-domain download adapter. Commercial purchase links are external checkout; import is only for files the user can lawfully use. Articles: provenance-bearing metadata adapters (such as Crossref/OpenAlex and licensed RSS) and a content availability flag. If full text is not licensed, Leaf shows metadata and a source link. AI: provider interface, online only, no hard-coded key. University authentication: provider-specific OAuth/SAML authorization in a secure system browser; no password storage.

## Backup design

A recoverable encryption secret is necessary for cross-install restore; a key held only in Android Keystore cannot survive uninstall. Plan an explicit recovery passphrase or equivalent account-mediated key wrapping. Encrypt with authenticated encryption, write archives atomically, verify before restore, and preserve local files on failed restore. Do not upload DRM-restricted files contrary to their license.

## Delivery gates

1. Offline library: safe import, covers, collections, search, storage.
2. Reader: complete EPUB pagination and formatting, exact restoration, notes, highlights, dictionary/word bank; PDF reflow with fallback.
3. Discovery/articles: real sources and provenance, lawful downloads/purchase links.
4. Reading system: activity-qualified sessions, 20-minute target, streak, history and widget.
5. Online extras: tutor, institutional access and encrypted backup.
6. Accessibility, failure recovery, performance, device tests and release configuration.

A green APK build only confirms compilation. A feature is called complete after behavior, persistence, offline and failure paths are exercised. The first public release also needs privacy review, rights/license audit and signing.
