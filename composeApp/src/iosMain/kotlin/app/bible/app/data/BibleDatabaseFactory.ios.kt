package app.bible.app.data

import app.bible.data.bible.BibleDatabase
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openBundledBible(): BibleDatabase {
    val base = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String) + "/databases"
    val fm = NSFileManager.defaultManager
    fm.createDirectoryAtPath(base, withIntermediateDirectories = true, attributes = null, error = null)
    val path = "$base/$BUNDLED_BIBLE"
    val bytes = bundledBibleBytes()
    val existingSize = (fm.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? Number)?.toLong()
    if (existingSize != bytes.size.toLong()) {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToFile(path, atomically = true)
    }
    val driver = NativeSqliteDriver(
        DatabaseConfiguration(
            name = BUNDLED_BIBLE,
            version = BibleDatabase.Schema.version.toInt(),
            create = { connection -> wrapConnection(connection) { BibleDatabase.Schema.create(it) } },
            extendedConfig = DatabaseConfiguration.Extended(basePath = base),
        ),
    )
    return BibleDatabase(driver)
}
