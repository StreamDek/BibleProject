package app.bible.app.data

import android.content.Context
import app.bible.data.bible.BibleDatabase
import app.bible.data.user.UserDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Set once from the Application (see androidApp). */
lateinit var appContext: Context

actual suspend fun openBundledBibleDriver(): SqlDriver = withContext(Dispatchers.IO) {
    val file = appContext.getDatabasePath(BUNDLED_BIBLE)
    val stampFile = File(file.parentFile, "$BUNDLED_BIBLE.stamp")
    val stamp = bundledBibleStamp()
    if (!file.exists() || !stampFile.exists() || stampFile.readText() != stamp) {
        file.parentFile?.mkdirs()
        listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(bundledBibleBytes())
        tmp.renameTo(file)
        stampFile.writeText(stamp)
    }
    AndroidSqliteDriver(BibleDatabase.Schema, appContext, BUNDLED_BIBLE)
}

actual suspend fun openUserDriver(): SqlDriver = withContext(Dispatchers.IO) {
    AndroidSqliteDriver(UserDatabase.Schema, appContext, USER_DB)
}
