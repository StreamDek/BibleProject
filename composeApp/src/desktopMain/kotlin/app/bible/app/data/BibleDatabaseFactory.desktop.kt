package app.bible.app.data

import app.bible.data.user.UserDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

private val dataDir: File get() = File(System.getProperty("user.home"), ".bible-app").apply { mkdirs() }

actual suspend fun openBundledBibleDriver(): SqlDriver = withContext(Dispatchers.IO) {
    val file = File(dataDir, BUNDLED_BIBLE)
    val stampFile = File(dataDir, "$BUNDLED_BIBLE.stamp")
    val stamp = bundledBibleStamp()
    if (!file.exists() || !stampFile.exists() || stampFile.readText() != stamp) {
        file.writeBytes(bundledBibleBytes())
        stampFile.writeText(stamp)
    }
    JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties().apply { put("open_mode", "1") })
}

actual suspend fun openStudyDriver(): SqlDriver = withContext(Dispatchers.IO) {
    val file = File(dataDir, BUNDLED_STUDY)
    val stampFile = File(dataDir, "$BUNDLED_STUDY.stamp")
    val stamp = bundledStudyStamp()
    if (!file.exists() || !stampFile.exists() || stampFile.readText() != stamp) {
        val tmp = File(dataDir, "$BUNDLED_STUDY.tmp")
        tmp.writeBytes(bundledStudyBytes())
        file.delete()
        tmp.renameTo(file)
        stampFile.writeText(stamp)
    }
    JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties().apply { put("open_mode", "1") })
}

actual suspend fun openUserDriver(): SqlDriver = withContext(Dispatchers.IO) {
    JdbcSqliteDriver("jdbc:sqlite:${File(dataDir, USER_DB).absolutePath}", Properties(), UserDatabase.Schema)
}
