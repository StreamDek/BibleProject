package app.bible.stories

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtTest {
    @Test fun knownHashDecodes() {
        // The example from the BlurHash README.
        val px = Blurhash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 4, 3)!!
        assertEquals(12, px.size)
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }

    @Test fun roundTripKeepsColours() {
        // Left half warm orange, right half dark blue.
        val w = 32; val h = 48
        val px = IntArray(w * h) { p -> if (p % w < w / 2) 0xFFE08030.toInt() else 0xFF102040.toInt() }
        val hash = Blurhash.encode(px, w, h, 3, 4)
        assertTrue(Blurhash.isValid(hash), hash)
        assertEquals(4 + 2 * 12, hash.length)
        val out = Blurhash.decode(hash, w, h)!!
        fun ch(c: Int, s: Int) = (c shr s) and 255
        val left = out[h / 2 * w + 2]
        val right = out[h / 2 * w + w - 3]
        assertTrue(ch(left, 16) > ch(left, 0) + 60, "left should be warm")
        assertTrue(ch(right, 0) > ch(right, 16), "right should be blue")
        assertTrue(abs(ch(right, 16) - 0x10) < 40)
    }

    @Test fun malformedHashIsRejected() {
        assertFalse(Blurhash.isValid("abc"))
        assertNull(Blurhash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdn", 4, 3))
    }

    @Test fun manifestPicksWidthAndRoundTrips() {
        val img = ArtImage("ab12", listOf(360, 720, 1080), "LEHV6nWB2yk8pyo0adR*.7kCMdnj", Depiction.Direct)
        val m = ArtManifest(base = "https://cdn.example.com/", images = mapOf("b" to img, "a" to img))
        assertEquals("https://cdn.example.com/img/ab12.720.jpg", m.url(img, 500))
        assertEquals("https://cdn.example.com/img/ab12.1080.jpg", m.url(img, 4000))
        assertEquals("https://cdn.example.com/img/ab12.360.jpg", m.url(img, 100))
        assertNull(ArtManifest().url(img, 500))
        val text = m.encode()
        assertTrue(text.indexOf("\"a\"") < text.indexOf("\"b\""), "keys are sorted for stable diffs")
        assertEquals(m, ArtManifest.decode(text))
    }
}
