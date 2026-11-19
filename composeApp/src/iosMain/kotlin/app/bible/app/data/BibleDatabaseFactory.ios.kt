package app.bible.app.data

import app.bible.data.bible.BibleDatabase
import app.bible.data.user.UserDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
private fun databaseDir(): String {
    val base = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String) + "/databases"
    NSFileManager.defaultManager.createDirectoryAtPath(base, withIntermediateDirectories = true, attributes = null, error = null)
    return base
}

@OptIn(ExperimentalForeignApi::class)
private fun writeFile(path: String, bytes: ByteArray) {
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    data.writeToFile(path, atomically = true)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openBundledBibleDriver(): SqlDriver {
    val base = databaseDir()
    val path = "$base/$BUNDLED_BIBLE"
    val stampPath = "$path.stamp"
    val stamp = bundledBibleStamp()
    val installed = NSString.stringWithContentsOfFile(stampPath, encoding = NSUTF8StringEncoding, error = null)
    if (!NSFileManager.defaultManager.fileExistsAtPath(path) || installed != stamp) {
        writeFile(path, bundledBibleBytes())
        writeFile(stampPath, stamp.encodeToByteArray())
    }
    return NativeSqliteDriver(
        DatabaseConfiguration(
            name = BUNDLED_BIBLE,
            version = BibleDatabase.Schema.version.toInt(),
            create = { connection -> wrapConnection(connection) { BibleDatabase.Schema.create(it) } },
            extendedConfig = DatabaseConfiguration.Extended(basePath = base),
        ),
    )
}

actual suspend fun openUserDriver(): SqlDriver =
    NativeSqliteDriver(
        DatabaseConfiguration(
            name = USER_DB,
            version = UserDatabase.Schema.version.toInt(),
            create = { connection -> wrapConnection(connection) { UserDatabase.Schema.create(it) } },
            extendedConfig = DatabaseConfiguration.Extended(basePath = databaseDir()),
        ),
    )
