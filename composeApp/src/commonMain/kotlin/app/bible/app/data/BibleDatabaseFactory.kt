package app.bible.app.data

import app.bible.app.resources.Res
import app.cash.sqldelight.db.SqlDriver
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** File name of the bundled KJV database inside composeResources/files. */
const val BUNDLED_BIBLE = "bible-kjv.db"

/** The reader's own data (position, settings, highlights, bookmarks, notes). */
const val USER_DB = "user.db"

/**
 * Opens the bundled KJV database. It is copied out of the app's resources on first run, and again only when
 * the bundled stamp (pack version + file hash, written by :pipeline) differs from the installed copy's stamp.
 */
expect suspend fun openBundledBibleDriver(): SqlDriver

/** Opens (creating if needed) the read-write user database. */
expect suspend fun openUserDriver(): SqlDriver

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledBibleBytes(): ByteArray = Res.readBytes("files/$BUNDLED_BIBLE")

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledBibleStamp(): String =
    Res.readBytes("files/${BUNDLED_BIBLE.removeSuffix(".db")}.stamp").decodeToString().trim()
