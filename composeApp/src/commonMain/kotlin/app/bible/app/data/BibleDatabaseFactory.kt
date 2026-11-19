package app.bible.app.data

import app.bible.app.resources.Res
import app.bible.data.bible.BibleDatabase
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** File name of the bundled KJV database inside composeResources/files. */
const val BUNDLED_BIBLE = "bible-kjv.db"

/** Opens the bundled KJV database, copying it out of the app's resources on first run (or after an app update). */
expect suspend fun openBundledBible(): BibleDatabase

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledBibleBytes(): ByteArray = Res.readBytes("files/$BUNDLED_BIBLE")
