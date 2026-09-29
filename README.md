# LEAF — native Android reading app

This is the first **source-code milestone**, not the complete product described in the supplied brief. It is a Kotlin/Jetpack Compose Android application. No WebView or browser is used for reading.

## Current behavior

- Import EPUB and PDF through Android Files; Android view/share intents are declared.
- Copy imports into private app storage, reject unsupported files and duplicate content by SHA-256, extract basic EPUB title and creator.
- Offline library sorted by recent activity.
- Read EPUB spine chapters as extracted text or view PDF pages with Android `PdfRenderer` inside Leaf.
- Move between chapters/pages, save and restore the last chapter/page in Room, set one bookmark, add and reopen notes.
- Forest/parchment visual foundation and five navigation destinations. Discover, Articles, and History clearly indicate that they are pending.

## Material limitations

The EPUB reader currently treats each spine chapter as one position. It does not remember a position *within* a long chapter, preserve inline images/complex formatting, or offer physical page turns. The PDF reader displays original pages only; text reflow, layout confidence, OCR, selection, and dictionary are pending. The toolbar is visible, so immersive mode is pending. Notes cannot yet be edited or deleted. No cloud backup, encryption, widget, streaks, AI, discovery API, institutional login, purchased ebook integration, or release APK is implemented. These are not represented as working features.

The current code has not been compiled in this workspace because Android SDK and Gradle are absent. It needs a build and device verification before installation. Do not entrust irreplaceable reading data to this version; the database has no migration beyond schema version 1 or backup yet.

## Build

1. Open this directory in Android Studio with Android SDK 35 and JDK 17.
2. Allow Android Studio to install the requested Gradle plugin/dependencies and generate a Gradle wrapper if prompted. Alternatively use an installed Gradle 8.9+ to run `gradle wrapper`.
3. Run `./gradlew :app:assembleDebug` and install `app/build/outputs/apk/debug/app-debug.apk` on a Pixel.
4. Test import, reopening a chapter/page, bookmark, notes, sharing/open-with, and PDF rendering on the target device before trusting saved data.

No API keys are required for this first milestone. No paid SDK is required to build it. External libraries are AndroidX Compose, Material 3, Activity, Lifecycle, Navigation, Room, DataStore, Kotlin Coroutines, and JUnit (Apache 2.0 licenses); Android's built-in PDF renderer is used. DataStore and Navigation are included for planned architecture but are not yet wired into the first screens.

## Structure and next architecture steps

`data/LeafDatabase.kt`: Room entities/DAO; `data/ImportRepository.kt`: Storage Access Framework import and private file ownership; `data/ReaderContent.kt`: EPUB spine parsing and PDF rendering; `MainActivity.kt`: Compose screens. This narrow structure is intentional for a first vertical slice; split UI into screen ViewModels and add repositories as the features mature. Versioned Room migrations are mandatory before altering a schema with user data.

Next: implement EPUB CFI or equivalent fine-grained location and pagination, then an immersive reader/sidebar, robust EPUB media and navigation, persistence tests, PDF text extraction/reflow with an honest fallback, and the remaining phases in the supplied brief. Backups should use a user-recoverable key strategy; a key held only in Android Keystore may be lost on uninstall, making a cloud backup impossible to restore.
