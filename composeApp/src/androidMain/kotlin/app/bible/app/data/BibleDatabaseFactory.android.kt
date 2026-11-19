package app.bible.app.data

import android.content.Context
import app.bible.data.bible.BibleDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Set once from the Application (see androidApp). */
lateinit var appContext: Context

actual suspend fun openBundledBible(): BibleDatabase = withContext(Dispatchers.IO) {
    val file = appContext.getDatabasePath(BUNDLED_BIBLE)
    val bytes = bundledBibleBytes()
    if (!file.exists() || file.length() != bytes.size.toLong()) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
    BibleDatabase(AndroidSqliteDriver(BibleDatabase.Schema, appContext, BUNDLED_BIBLE))
}
