package app.bible.stories

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * BlurHash (https://blurha.sh): a short string that decodes to a soft colour preview of an image. The publisher
 * encodes one per image; the app decodes it into a tiny bitmap to show while the real image loads.
 * Pixels are ARGB ints.
 */
object Blurhash {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    fun isValid(hash: String): Boolean {
        if (hash.length < 6 || hash.any { it !in CHARS }) return false
        val flag = decode83(hash, 0, 1)
        val nx = flag % 9 + 1
        val ny = flag / 9 + 1
        return hash.length == 4 + 2 * nx * ny
    }

    /** Decodes [hash] to [width] × [height] ARGB pixels, or null when the hash is malformed. */
    fun decode(hash: String, width: Int, height: Int, punch: Float = 1f): IntArray? {
        if (!isValid(hash) || width <= 0 || height <= 0) return null
        val flag = decode83(hash, 0, 1)
        val nx = flag % 9 + 1
        val ny = flag / 9 + 1
        val maxValue = (decode83(hash, 1, 2) + 1) / 166f * punch
        val colors = Array(nx * ny) { i ->
            if (i == 0) {
                val v = decode83(hash, 2, 6)
                floatArrayOf(toLinear(v shr 16), toLinear((v shr 8) and 255), toLinear(v and 255))
            } else {
                val v = decode83(hash, 4 + i * 2, 6 + i * 2)
                floatArrayOf(
                    signPow(((v / (19 * 19)) - 9) / 9f, 2f) * maxValue,
                    signPow((((v / 19) % 19) - 9) / 9f, 2f) * maxValue,
                    signPow(((v % 19) - 9) / 9f, 2f) * maxValue,
                )
            }
        }
        val out = IntArray(width * height)
        val cosX = Array(nx) { i -> FloatArray(width) { x -> cos(PI * x * i / width).toFloat() } }
        val cosY = Array(ny) { j -> FloatArray(height) { y -> cos(PI * y * j / height).toFloat() } }
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0f; var g = 0f; var b = 0f
            for (j in 0 until ny) for (i in 0 until nx) {
                val basis = cosX[i][x] * cosY[j][y]
                val c = colors[i + j * nx]
                r += c[0] * basis; g += c[1] * basis; b += c[2] * basis
            }
            out[y * width + x] = (0xFF shl 24) or (toSrgb(r) shl 16) or (toSrgb(g) shl 8) or toSrgb(b)
        }
        return out
    }

    /** Encodes ARGB [pixels] ([width] × [height]) with [nx] × [ny] components (1..9 each). */
    fun encode(pixels: IntArray, width: Int, height: Int, nx: Int = 3, ny: Int = 4): String {
        require(nx in 1..9 && ny in 1..9) { "components must be 1..9" }
        require(pixels.size == width * height) { "pixel count does not match size" }
        val linear = Array(3) { c -> FloatArray(pixels.size) { p -> toLinear((pixels[p] shr (16 - 8 * c)) and 255) } }
        val factors = ArrayList<FloatArray>(nx * ny)
        for (j in 0 until ny) for (i in 0 until nx) {
            val norm = if (i == 0 && j == 0) 1f else 2f
            var r = 0f; var g = 0f; var b = 0f
            for (y in 0 until height) {
                val cy = cos(PI * j * y / height).toFloat()
                for (x in 0 until width) {
                    val basis = cos(PI * i * x / width).toFloat() * cy
                    val p = y * width + x
                    r += basis * linear[0][p]; g += basis * linear[1][p]; b += basis * linear[2][p]
                }
            }
            val scale = norm / (width * height)
            factors += floatArrayOf(r * scale, g * scale, b * scale)
        }
        val sb = StringBuilder()
        encode83((nx - 1) + (ny - 1) * 9, 1, sb)
        val ac = factors.drop(1)
        val maxValue: Float
        if (ac.isNotEmpty()) {
            val actualMax = ac.maxOf { f -> max(abs(f[0]), max(abs(f[1]), abs(f[2]))) }
            val q = floor(max(0f, min(82f, floor(actualMax * 166 - 0.5f)))).toInt()
            maxValue = (q + 1) / 166f
            encode83(q, 1, sb)
        } else {
            maxValue = 1f
            encode83(0, 1, sb)
        }
        val dc = factors[0]
        encode83((toSrgb(dc[0]) shl 16) + (toSrgb(dc[1]) shl 8) + toSrgb(dc[2]), 4, sb)
        for (f in ac) {
            fun q(v: Float) = floor(max(0f, min(18f, floor(signPow(v / maxValue, 0.5f) * 9 + 9.5f)))).toInt()
            encode83(q(f[0]) * 19 * 19 + q(f[1]) * 19 + q(f[2]), 2, sb)
        }
        return sb.toString()
    }

    private fun decode83(s: String, from: Int, to: Int): Int {
        var v = 0
        for (k in from until to) v = v * 83 + CHARS.indexOf(s[k])
        return v
    }

    private fun encode83(value: Int, length: Int, sb: StringBuilder) {
        for (k in 1..length) {
            var div = 1
            repeat(length - k) { div *= 83 }
            sb.append(CHARS[(value / div) % 83])
        }
    }

    private fun toLinear(v: Int): Float {
        val x = v / 255f
        return if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun toSrgb(v: Float): Int {
        val x = v.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) (x * 12.92f * 255 + 0.5f).toInt()
        else ((1.055f * x.pow(1 / 2.4f) - 0.055f) * 255 + 0.5f).toInt()
    }

    private fun signPow(v: Float, e: Float) = sign(v) * abs(v).pow(e)

}
