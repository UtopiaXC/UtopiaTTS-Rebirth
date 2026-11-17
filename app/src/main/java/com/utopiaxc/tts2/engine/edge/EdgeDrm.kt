package com.utopiaxc.tts2.engine.edge

import android.util.Log
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Edge TTS DRM 工具类，实现 Sec-MS-GEC v2 签名算法。
 * 移植自 https://github.com/rany2/edge-tts/blob/master/src/edge_tts/drm.py
 *
 * Token 格式: "<CS64 16 hex>-<SHA256 64 hex>"
 */
object EdgeDrm {
    private const val TAG = "EdgeDrm"

    @Volatile
    var clockSkewSeconds: Long = 0L

    // ======== 常量 ========
    private const val WIN_EPOCH = 11644473600L
    private const val S_TO_NS_OVER_100 = 10_000_000L

    // 用 Long 存储避免 Kotlin hex 字面量类型推断问题
    private val SHA256_INITIAL = longArrayOf(
        0x6A09E667L, 0xBB67AE85L, 0x3C6EF372L, 0xA54FF53AL,
        0x510E527FL, 0x9B05688CL, 0x1F83D9ABL, 0x5BE0CD19L
    )
    private val SHA256_STATE = longArrayOf(
        0xFD9FBEA2L, 0x91243CE7L, 0xBE499094L, 0xCD2E1E0AL,
        0x6C006F49L, 0x04BD35ABL, 0xFEB0A7E4L, 0x03EFFBD2L
    )
    private val SHA256_SCHEDULE = longArrayOf(
        0xEB24BF9EL, 0x1558691FL, 0xA0C09EE2L, 0x0CFA32BAL,
        0xA622EE8CL, 0xD1DD9491L, 0xC0DD25A0L, 0xF9E70AB6L,
        0xF0C31573L, 0x7058CE4EL, 0x7188E606L, 0x00E5AB33L,
        0xD258B060L, 0x72766564L
    )
    private val SHA256_ROUND_CONSTANTS = longArrayOf(
        0x428A2F98L, 0x71374491L, 0xB5C0FBCFL, 0xE9B5DBA5L,
        0x3956C25BL, 0x59F111F1L, 0x923F82A4L, 0xAB1C5ED5L,
        0xD807AA98L, 0x12835B01L, 0x243185BEL, 0x550C7DC3L,
        0x72BE5D74L, 0x80DEB1FEL, 0x9BDC06A7L, 0xC19BF174L,
        0xE49B69C1L, 0xEFBE4786L, 0x0FC19DC6L, 0x240CA1CCL,
        0x2DE92C6FL, 0x4A7484AAL, 0x5CB0A9DCL, 0x76F988DAL,
        0x983E5152L, 0xA831C66DL, 0xB00327C8L, 0xBF597FC7L,
        0xC6E00BF3L, 0xD5A79147L, 0x06CA6351L, 0x14292967L,
        0x27B70A85L, 0x2E1B2138L, 0x4D2C6DFCL, 0x53380D13L,
        0x650A7354L, 0x766A0ABBL, 0x81C2C92EL, 0x92722C85L,
        0xA2BFE8A1L, 0xA81A664BL, 0xC24B8B70L, 0xC76C51A3L,
        0xD192E819L, 0xD6990624L, 0xF40E3585L, 0x106AA070L,
        0x19A4C116L, 0x1E376C08L, 0x2748774CL, 0x34B0BCB5L,
        0x391C0CB3L, 0x4ED8AA4AL, 0x5B9CCA4FL, 0x682E6FF3L,
        0x748F82EEL, 0x78A5636FL, 0x84C87814L, 0x8CC70208L,
        0x90BEFFFAL, 0xA4506CEBL, 0xBEF9A3F7L, 0xC67178F2L
    )

    private const val SHA256_ADJUSTMENT = 0xC9DC06A7L
    private val SHA256_BLOCK_CONSTANTS = LongArray(64) { i ->
        when {
            i < 14 -> SHA256_ROUND_CONSTANTS[i]
            i == 14 -> SHA256_ADJUSTMENT
            else -> SHA256_ROUND_CONSTANTS[i]
        }
    }
    private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80L
    private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0L

    private data class Cs64Coefficient(val coefficient: Long, val offset: Long)

    private val CS64_COEFFICIENTS = arrayOf(
        Cs64Coefficient(0xE82E91B1L, 0xB32700D0L),
        Cs64Coefficient(0x44DE24C6L, 0x88DFF51DL),
        Cs64Coefficient(0x3BEB65BCL, 0x29514038L),
        Cs64Coefficient(0xC89C5933L, 0xB328CBDFL),
        Cs64Coefficient(0x8D1EC211L, 0xD405B4D5L),
        Cs64Coefficient(0x314EF5D5L, 0x26E55F3CL),
        Cs64Coefficient(0xE92491B1L, 0x607801C6L),
        Cs64Coefficient(0xF0A5B9D5L, 0x82B2C1FBL),
        Cs64Coefficient(0x06064CF3L, 0xFA51A3DAL),
        Cs64Coefficient(0xDA79FA60L, 0x207CC464L),
        Cs64Coefficient(0xBED20560L, 0x3C3C74F3L),
        Cs64Coefficient(0x89F9C9F8L, 0xD2D14B61L),
        Cs64Coefficient(0x35FA38C6L, 0xBB04A2C1L),
        Cs64Coefficient(0xCB49E3C6L, 0xC5626167L)
    )
    private const val CS64_FACTOR = 0x82893933L
    private const val CS64_ADJUSTMENT0 = 0xF2F90B1AL
    private const val CS64_ADJUSTMENT1 = 0x0B1A0000L

    private const val MASK = 0xFFFFFFFFL

    // ======== 辅助函数 ========

    private fun rol16(value: Long): Long {
        val v = value and MASK
        return (((v shl 16) or (v shr 16)) and MASK)
    }

    private fun sigma0(value: Long): Long {
        val v = value and MASK
        return ((v shr 7) xor (v shl 25) xor (v shr 18) xor (v shl 14) xor (v shr 3)) and MASK
    }

    private fun sigma1(value: Long): Long {
        val v = value and MASK
        return ((v shr 17) xor (v shl 15) xor (v shr 19) xor (v shl 13) xor (v shr 10)) and MASK
    }

    private fun wordsLe4Even(data: ByteArray): LongArray {
        val padLen = ((-data.size) % 4 + 4) % 4
        val padded = data + ByteArray(padLen)
        if (padded.isEmpty()) return LongArray(0)
        val count = padded.size / 4
        val words = LongArray(count)
        for (i in 0 until count) {
            val o = i * 4
            words[i] = ((padded[o].toLong() and 0xFFL) or
                    ((padded[o + 1].toLong() and 0xFFL) shl 8) or
                    ((padded[o + 2].toLong() and 0xFFL) shl 16) or
                    ((padded[o + 3].toLong() and 0xFFL) shl 24)) and MASK
        }
        return if (words.size % 2 == 0) words else words + 0L
    }

    private fun expandSchedule(words: LongArray, firstWord: Int = 16) {
        for (index in firstWord until 64) {
            val x = words[index - 2]
            val y = words[index - 15]
            val s1 = sigma1(x)
            val s0 = sigma0(y)
            words[index] = (s1 + words[index - 7] + s0 + words[index - 16]) and MASK
        }
    }

    private fun sha256Rounds(state: LongArray, words: LongArray, firstRound: Int = 0): LongArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        val constants = if (firstRound == 14) SHA256_BLOCK_CONSTANTS else SHA256_ROUND_CONSTANTS
        val aligned = minOf(64, ((firstRound + 7) / 8) * 8)

        for (index in firstRound until aligned) {
            val rotE = e * 0x100000001L
            val rotA = a * 0x100000001L
            val t1 = (h +
                    ((rotE shr 6) xor (rotE shr 11) xor (rotE shr 25)) +
                    (g xor (e and (f xor g))) +
                    constants[index] + words[index]) and MASK
            val t2 = (((rotA shr 2) xor (rotA shr 13) xor (rotA shr 22)) +
                    ((a and b) or (c and (a or b)))) and MASK
            // Python 中是元组同时赋值：a,b,c,d,e,f,g,h =
            //     (t1+t2)&mask, oldA, oldB, oldC, (d+t1)&mask, oldE, oldF, oldG
            // 必须先缓存旧值，否则连写会互相覆盖，算出错误的 SHA-256 状态。
            val oldA = a; val oldB = b; val oldC = c; val oldD = d
            val oldE = e; val oldF = f; val oldG = g
            a = (t1 + t2) and MASK
            b = oldA
            c = oldB
            d = oldC
            e = (oldD + t1) and MASK
            f = oldE
            g = oldF
            h = oldG
        }
        for (index in aligned until 64 step 8) {
            val rotE = e * 0x100000001L
            val rotA = a * 0x100000001L
            var t1 = (h +
                    ((rotE shr 6) xor (rotE shr 11) xor (rotE shr 25)) +
                    (g xor (e and (f xor g))) +
                    constants[index] + words[index]) and MASK
            d = (d + t1) and MASK
            h = (t1 + (((rotA shr 2) xor (rotA shr 13) xor (rotA shr 22)) + ((a and b) or (c and (a or b))))) and MASK

            val rotEd = d * 0x100000001L
            val rotAh = h * 0x100000001L
            t1 = (g +
                    ((rotEd shr 6) xor (rotEd shr 11) xor (rotEd shr 25)) +
                    (f xor (d and (e xor f))) +
                    constants[index + 1] + words[index + 1]) and MASK
            c = (c + t1) and MASK
            g = (t1 + (((rotAh shr 2) xor (rotAh shr 13) xor (rotAh shr 22)) + ((h and a) or (b and (h or a))))) and MASK

            val rotEc = c * 0x100000001L
            val rotAg = g * 0x100000001L
            t1 = (f +
                    ((rotEc shr 6) xor (rotEc shr 11) xor (rotEc shr 25)) +
                    (e xor (c and (d xor e))) +
                    constants[index + 2] + words[index + 2]) and MASK
            b = (b + t1) and MASK
            f = (t1 + (((rotAg shr 2) xor (rotAg shr 13) xor (rotAg shr 22)) + ((g and h) or (a and (g or h))))) and MASK

            val rotEb = b * 0x100000001L
            val rotEf = f * 0x100000001L
            t1 = (e +
                    ((rotEb shr 6) xor (rotEb shr 11) xor (rotEb shr 25)) +
                    (d xor (b and (c xor d))) +
                    constants[index + 3] + words[index + 3]) and MASK
            a = (a + t1) and MASK
            e = (t1 + (((rotEf shr 2) xor (rotEf shr 13) xor (rotEf shr 22)) + ((f and g) or (h and (f or g))))) and MASK

            val rotEa = a * 0x100000001L
            val rotEe = e * 0x100000001L
            t1 = (d +
                    ((rotEa shr 6) xor (rotEa shr 11) xor (rotEa shr 25)) +
                    (c xor (a and (b xor c))) +
                    constants[index + 4] + words[index + 4]) and MASK
            h = (h + t1) and MASK
            d = (t1 + (((rotEe shr 2) xor (rotEe shr 13) xor (rotEe shr 22)) + ((e and f) or (g and (e or f))))) and MASK

            val rotEh = h * 0x100000001L
            val rotEd2 = d * 0x100000001L
            t1 = (c +
                    ((rotEh shr 6) xor (rotEh shr 11) xor (rotEh shr 25)) +
                    (b xor (h and (a xor b))) +
                    constants[index + 5] + words[index + 5]) and MASK
            g = (g + t1) and MASK
            c = (t1 + (((rotEd2 shr 2) xor (rotEd2 shr 13) xor (rotEd2 shr 22)) + ((d and e) or (f and (d or e))))) and MASK

            val rotEg = g * 0x100000001L
            val rotEc2 = c * 0x100000001L
            t1 = (b +
                    ((rotEg shr 6) xor (rotEg shr 11) xor (rotEg shr 25)) +
                    (a xor (g and (h xor a))) +
                    constants[index + 6] + words[index + 6]) and MASK
            f = (f + t1) and MASK
            b = (t1 + (((rotEc2 shr 2) xor (rotEc2 shr 13) xor (rotEc2 shr 22)) + ((c and d) or (e and (c or d))))) and MASK

            val rotEf2 = f * 0x100000001L
            val rotEb2 = b * 0x100000001L
            t1 = (a +
                    ((rotEf2 shr 6) xor (rotEf2 shr 11) xor (rotEf2 shr 25)) +
                    (h xor (f and (g xor h))) +
                    constants[index + 7] + words[index + 7]) and MASK
            e = (e + t1) and MASK
            a = (t1 + (((rotEb2 shr 2) xor (rotEb2 shr 13) xor (rotEb2 shr 22)) + ((b and c) or (d and (b or c))))) and MASK
        }
        return longArrayOf(a, b, c, d, e, f, g, h)
    }

    private val sha256InitCache = HashMap<ByteArray, LongArray>()

    private fun sha256Init(data: ByteArray): LongArray {
        sha256InitCache[data]?.let { return it.copyOf() }
        val words = LongArray(64)
        System.arraycopy(SHA256_SCHEDULE, 0, words, 0, 14)
        words[14] = ((data[0].toLong() and 0xFFL) shl 16) or ((data[1].toLong() and 0xFFL) shl 8) or (data[2].toLong() and 0xFFL)
        words[15] = ((data[3].toLong() and 0xFFL) shl 24) or ((data[4].toLong() and 0xFFL) shl 16) or ((data[5].toLong() and 0xFFL) shl 8) or (data[6].toLong() and 0xFFL)

        for (index in 16 until 30) {
            var value = SHA256_SCHEDULE[index - 16]
            if (index - 2 >= 14) {
                value += if (index == 16) {
                    (sigma1(words[index - 2]) xor SHA256_SIGMA1_ADJUSTMENT)
                } else {
                    sigma1(words[index - 2])
                }
            }
            if (index - 7 >= 14) value += words[index - 7]
            if (index - 15 >= 14) {
                value += if (index == 29) {
                    (sigma0(words[index - 15]) xor SHA256_SIGMA0_ADJUSTMENT)
                } else {
                    sigma0(words[index - 15])
                }
            }
            words[index] = value and MASK
        }
        words[30] = ((sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] +
                SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14]) and MASK)

        expandSchedule(words, 31)
        val working = sha256Rounds(SHA256_STATE.copyOf(), words, 14)
        val result = LongArray(8) { i -> ((SHA256_INITIAL[i] + working[i]) and MASK) }
        sha256InitCache[data.copyOf()] = result.copyOf()
        return result
    }

    private fun sha256Digest(data: ByteArray): ByteArray {
        require(data.size >= 7) { "Token salt must contain at least seven bytes" }
        val length = 57 + data.size
        val pad = ((55 - length) % 64 + 64) % 64
        val padded = data.copyOfRange(7, data.size) + byteArrayOf(0x80.toByte()) + ByteArray(pad)
        val totalLen = padded.size + 8
        val msg = ByteArray(totalLen)
        System.arraycopy(padded, 0, msg, 0, padded.size)
        val bitLen = length.toLong() * 8
        msg[padded.size + 0] = ((bitLen shr 56) and 0xFF).toByte()
        msg[padded.size + 1] = ((bitLen shr 48) and 0xFF).toByte()
        msg[padded.size + 2] = ((bitLen shr 40) and 0xFF).toByte()
        msg[padded.size + 3] = ((bitLen shr 32) and 0xFF).toByte()
        msg[padded.size + 4] = ((bitLen shr 24) and 0xFF).toByte()
        msg[padded.size + 5] = ((bitLen shr 16) and 0xFF).toByte()
        msg[padded.size + 6] = ((bitLen shr 8) and 0xFF).toByte()
        msg[padded.size + 7] = (bitLen and 0xFF).toByte()

        var state = sha256Init(data.copyOfRange(0, 7))
        for (offset in 0 until msg.size step 64) {
            val block = msg.copyOfRange(offset, offset + 64)
            val words = LongArray(64) { i ->
                if (i < 16) {
                    val o = i * 4
                    ((block[o].toLong() and 0xFFL) shl 24) or
                            ((block[o + 1].toLong() and 0xFFL) shl 16) or
                            ((block[o + 2].toLong() and 0xFFL) shl 8) or
                            (block[o + 3].toLong() and 0xFFL)
                } else 0L
            }
            expandSchedule(words)
            val working = sha256Rounds(state.copyOf(), words)
            state = LongArray(8) { i -> ((state[i] + working[i]) and MASK) }
        }

        val digest = ByteArray(32)
        for (i in 0 until 8) {
            digest[i * 4 + 0] = ((state[i] shr 24) and 0xFF).toByte()
            digest[i * 4 + 1] = ((state[i] shr 16) and 0xFF).toByte()
            digest[i * 4 + 2] = ((state[i] shr 8) and 0xFF).toByte()
            digest[i * 4 + 3] = (state[i] and 0xFF).toByte()
        }
        return digest
    }

    private fun cs64Digest(data: ByteArray, digest: ByteArray): Pair<Long, Long> {
        // Python: key_a, key_b = struct.unpack_from("<II", digest)
        // 每个 key 是 4 个小端字节（32 位），不能只读前 2 字节。
        val keyA = (digest[0].toLong() and 0xFFL) or
                ((digest[1].toLong() and 0xFFL) shl 8) or
                ((digest[2].toLong() and 0xFFL) shl 16) or
                ((digest[3].toLong() and 0xFFL) shl 24) and MASK
        val keyB = (digest[4].toLong() and 0xFFL) or
                ((digest[5].toLong() and 0xFFL) shl 8) or
                ((digest[6].toLong() and 0xFFL) shl 16) or
                ((digest[7].toLong() and 0xFFL) shl 24) and MASK
        val kA = keyA or 1L
        val kB = keyB or 1L
        val factors = longArrayOf(
            (kA * CS64_FACTOR) and MASK,
            (kB * CS64_FACTOR) and MASK
        )

        val terms = mutableListOf<Long>()
        val offsets = mutableListOf<Long>()
        for ((idx, pair) in CS64_COEFFICIENTS.withIndex()) {
            terms.add((pair.coefficient * factors[idx % 2]) and MASK)
            offsets.add(pair.offset)
        }

        val words = wordsLe4Even(byteArrayOf(0) + data)
        for ((idx, word) in words.withIndex()) {
            val ki = if (idx % 2 == 0) kA else kB
            val term = (word * ki) and MASK
            val offsetMul = if (idx % 2 == 0) 0xEF0569FBL else 0xC31713DBL
            val offset = (rol16(word) * offsetMul) and MASK
            if (idx == 0) {
                terms.add((term + CS64_ADJUSTMENT0 * factors[0]) and MASK)
                offsets.add((offset + CS64_ADJUSTMENT1) and MASK)
            } else {
                terms.add(term)
                offsets.add(offset)
            }
        }

        var swapAcc = 0L
        var swapLast = 0L
        var state = 0L
        var acc = 0L
        var i = 0
        while (i < terms.size) {
            val x = terms[i]; val y = terms[i + 1]
            var t = rol16((offsets[i] + x) and MASK)
            var q = (t * 0x76F50F34L) and MASK
            swapAcc = (swapAcc + (rol16(q) * 0xC3EFEA97L + q)) and MASK
            t = rol16((offsets[i + 1] + y) and MASK)
            q = (t * 0x1881CE3CL) and MASK
            swapLast = (rol16(q) * 0x35BD1EC9L + q) and MASK
            swapAcc = (swapAcc + swapLast) and MASK

            state = (state * kA + x) and MASK
            state = (rol16(state) * 0xCF98B111L) and MASK
            state = (rol16(state) * 0x87085B9FL) and MASK
            state = (rol16(state) * 0x12CEB96DL) and MASK
            state = (rol16(state) * 0x257E1D83L) and MASK
            acc += state

            t = (state * kB + y) and MASK
            t = (rol16(t) * 0xA27416F5L) and MASK
            t = (rol16(t) * 0xD38396FFL) and MASK
            val t2 = rol16((rol16(t) * 0x7C932B89L) and MASK)
            state = (t2 * 0xBFA49F69L) and MASK
            acc = (acc + state) and MASK

            i += 2
        }
        return Pair(swapLast xor state, swapAcc xor acc)
    }

    private fun tokenHash(data: ByteArray): Pair<String, String> {
        val digest = sha256Digest(data)
        val (r1, r2) = cs64Digest(data, digest)
        // Python: struct.pack("<II", r1, r2).hex().upper()
        // i.e. little-endian 32-bit ints packed as bytes, then hex-encoded
        val cs64 = littleEndianIntToHex(r1) + littleEndianIntToHex(r2)
        val sha = digest.joinToString("") { String.format(Locale.US, "%02X", it) }
        return Pair(cs64, sha)
    }

    private fun littleEndianIntToHex(value: Long): String {
        val v = value and MASK
        val bytes = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        )
        return bytes.joinToString("") { String.format(Locale.US, "%02X", it) }
    }

    // ======== 公共 API ========

    fun generateSecMsGecHeader(): String {
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        return generateSecMsGecHeaderAt(clientTimeSeconds + clockSkewSeconds)
    }

    /** 按指定的 Unix 秒数生成 token，供单元测试固定时间向量使用。 */
    internal fun generateSecMsGecHeaderAt(adjustedTimeSeconds: Long): String {
        var ticks = adjustedTimeSeconds + WIN_EPOCH
        ticks -= ticks % 300
        ticks *= S_TO_NS_OVER_100

        val strToHash = ticks.toString() + EdgeConstants.TRUSTED_CLIENT_TOKEN
        val hashBytes = strToHash.toByteArray(Charsets.US_ASCII)
        val (cs64, sha256) = tokenHash(hashBytes)
        val token = "$cs64-$sha256"

        return token
    }

    fun generateMuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { String.format(Locale.US, "%02X", it) }
    }

    fun adjustClockSkew(serverDateUnixSeconds: Long) {
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        val newSkew = serverDateUnixSeconds - clientTimeSeconds
        Log.i(TAG, "Clock skew: ${clockSkewSeconds}s -> ${newSkew}s")
        clockSkewSeconds = newSkew
    }
}
