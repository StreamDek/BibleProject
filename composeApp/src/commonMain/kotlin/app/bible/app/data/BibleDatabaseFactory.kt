package app.bible.app.data

import app.bible.app.resources.Res
import app.cash.sqldelight.db.SqlDriver
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** File name of the bundled KJV database inside composeResources/files. */
const val BUNDLED_BIBLE = "bible-kjv.db"

/** File name of the bundled word-study pack (Strong's, STEP Bible, KJV word tags), built by :pipeline:buildStudy. */
const val BUNDLED_STUDY = "study.db"

/** The reader's own data (position, settings, highlights, bookmarks, notes). */
const val USER_DB = "user.db"

/**
 * Opens the bundled KJV database. It is copied out of the app's resources on first run, and again only when
 * the bundled stamp (pack version + file hash, written by :pipeline) differs from the installed copy's stamp.
 */
expect suspend fun openBundledBibleDriver(): SqlDriver

/**
 * Opens the bundled word-study pack, copied out of the app's resources the same way as the Bible (on first use and
 * whenever its stamp changes). The copy is written to a temporary file and renamed, so an interrupted update never
 * leaves a half-written pack; saved words live in user.db and are untouched by pack updates.
 */
expect suspend fun openStudyDriver(): SqlDriver

/** Opens (creating if needed) the read-write user database. */
expect suspend fun openUserDriver(): SqlDriver

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledBibleBytes(): ByteArray = Res.readBytes("files/$BUNDLED_BIBLE")

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledBibleStamp(): String =
    Res.readBytes("files/${BUNDLED_BIBLE.removeSuffix(".db")}.stamp").decodeToString().trim()

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledStudyBytes(): ByteArray = Res.readBytes("files/$BUNDLED_STUDY")

@OptIn(ExperimentalResourceApi::class)
internal suspend fun bundledStudyStamp(): String =
    Res.readBytes("files/${BUNDLED_STUDY.removeSuffix(".db")}.stamp").decodeToString().trim()
