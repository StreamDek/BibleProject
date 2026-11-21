package app.bible.data.study

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Guides copied from Strong's entries in study.db. */
class PronunciationTest {
    @Test fun hebrew() {
        assertEquals("el o heem", Pronunciation.speakable("el-o-heem'")) // H430
        assertEquals("shaw lome", Pronunciation.speakable("shaw-lome'")) // H7965
        assertEquals("ab ee gah yil", Pronunciation.speakable("ab-ee-gah'-yil")) // H26
        assertEquals("awb", Pronunciation.speakable("awb")) // H1
    }

    @Test fun alternativesAndOptionalSounds() {
        assertEquals("yaw no akh", Pronunciation.speakable("yaw-no'-akh or (with enclitic) Yanowchah yaw-no'-khaw")) // H3239
        assertEquals("i deh may", Pronunciation.speakable("i deh may'-(gheh)")) // G1490
        assertEquals("bayth haw ay mek", Pronunciation.speakable("bayth haw-Ay'-mek")) // H1025
    }

    @Test fun greek() {
        assertEquals("ag ah pay", Pronunciation.speakable("ag-ah'-pay")) // G26
    }

    @Test fun missing() {
        assertNull(Pronunciation.speakable(null))
        assertNull(Pronunciation.speakable(" ' - "))
    }
}
