package app.bible.app.data

import app.bible.data.bible.BibleDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

actual suspend fun openBundledBible(): BibleDatabase = withContext(Dispatchers.IO) {
    val dir = File(System.getProperty("user.home"), ".bible-app").apply { mkdirs() }
    val file = File(dir, BUNDLED_BIBLE)
    val bytes = bundledBibleBytes()
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties().apply { put("open_mode", "1") })
    BibleDatabase(driver)
}
