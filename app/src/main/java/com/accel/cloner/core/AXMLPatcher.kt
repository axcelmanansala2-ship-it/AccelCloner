package com.accel.cloner.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Patches Android Binary XML (AXML) to replace a string in the string pool.
 * Used to change the package name in AndroidManifest.xml inside an APK,
 * enabling a second installable copy of an app with a unique package name.
 */
object AXMLPatcher {
    private const val UTF8_FLAG = 0x100

    fun patchPackageName(axml: ByteArray, oldPkg: String, newPkg: String): ByteArray {
        val buf = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)

        // File header (8 bytes)
        val fileType   = buf.short
        val fileHdrSz  = buf.short
        val fileTotSz  = buf.int

        // StringPool chunk header starts at offset 8
        val spPos       = 8
        val spType      = buf.short
        val spHdrSz     = buf.short.toInt() and 0xFFFF
        val spChunkSz   = buf.int
        val stringCount = buf.int
        val styleCount  = buf.int
        val flags       = buf.int
        val stringsStart = buf.int  // offset from sp chunk start to string data
        val stylesStart  = buf.int
        val isUtf8 = (flags and UTF8_FLAG) != 0

        // String offsets
        val offsets = IntArray(stringCount) { buf.int }
        repeat(styleCount) { buf.int } // skip style offsets

        // String data area
        val strDataStart = spPos + stringsStart
        val strDataSize  = (if (stylesStart > 0) stylesStart else spChunkSz) - stringsStart
        val strData = axml.copyOfRange(strDataStart, strDataStart + strDataSize)
        val strBuf  = ByteBuffer.wrap(strData).order(ByteOrder.LITTLE_ENDIAN)

        // Decode all strings
        val strings = Array(stringCount) { i ->
            strBuf.position(offsets[i])
            if (isUtf8) readUtf8(strBuf) else readUtf16(strBuf)
        }

        if (strings.none { it == oldPkg }) return axml // nothing to do

        // Replace all occurrences
        val patched = Array(stringCount) { i -> if (strings[i] == oldPkg) newPkg else strings[i] }

        // Rebuild string data + offsets
        val newStrData  = encodeStrings(patched, isUtf8)
        val newOffsets  = computeOffsets(patched, isUtf8)

        // New sizes
        val offsetTableSize = (stringCount + styleCount) * 4
        val newSpChunkSz = spHdrSz + offsetTableSize + newStrData.size
        val delta = newSpChunkSz - spChunkSz

        val out = ByteArrayOutputStream(axml.size + delta + 32)

        // Write updated file header
        out.le16(fileType.toInt()); out.le16(fileHdrSz.toInt()); out.le32(fileTotSz + delta)

        // Write updated string pool header
        out.le16(spType.toInt()); out.le16(spHdrSz)
        out.le32(newSpChunkSz)
        out.le32(stringCount); out.le32(styleCount)
        out.le32(flags); out.le32(stringsStart); out.le32(stylesStart)
        newOffsets.forEach { out.le32(it) }
        repeat(styleCount) { out.le32(0) }
        out.write(newStrData)

        // Append everything after the old string pool unchanged
        val afterSP = spPos + spChunkSz
        out.write(axml, afterSP, axml.size - afterSP)

        return out.toByteArray()
    }

    // ── Readers ──────────────────────────────────────────────────────────────

    private fun readUtf8(buf: ByteBuffer): String {
        val charLen = varLen(buf)
        val byteLen = varLen(buf)
        val bytes = ByteArray(byteLen) { buf.get() }
        buf.get() // null
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16(buf: ByteBuffer): String {
        val charLen = buf.short.toInt() and 0xFFFF
        val chars = CharArray(charLen) { (buf.short.toInt() and 0xFFFF).toChar() }
        buf.short // null
        return String(chars)
    }

    private fun varLen(buf: ByteBuffer): Int {
        val b = buf.get().toInt() and 0xFF
        return if (b and 0x80 != 0) ((b and 0x7F) shl 8) or (buf.get().toInt() and 0xFF) else b
    }

    // ── Writers ───────────────────────────────────────────────────────────────

    private fun encodeStrings(strings: Array<String>, utf8: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        strings.forEach { s ->
            if (utf8) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                writeVarLen(out, s.length); writeVarLen(out, bytes.size)
                out.write(bytes); out.write(0)
            } else {
                out.le16(s.length)
                s.forEach { c -> out.le16(c.code) }
                out.le16(0)
            }
        }
        return out.toByteArray()
    }

    private fun computeOffsets(strings: Array<String>, utf8: Boolean): IntArray {
        var pos = 0
        return IntArray(strings.size) { i ->
            val off = pos
            val s = strings[i]
            pos += if (utf8) {
                val b = s.toByteArray(Charsets.UTF_8)
                varLenSize(s.length) + varLenSize(b.size) + b.size + 1
            } else {
                2 + s.length * 2 + 2
            }
            off
        }
    }

    private fun varLenSize(v: Int) = if (v >= 0x80) 2 else 1
    private fun writeVarLen(out: ByteArrayOutputStream, v: Int) {
        if (v >= 0x80) { out.write((v ushr 8) or 0x80); out.write(v and 0xFF) } else out.write(v)
    }

    private fun ByteArrayOutputStream.le16(v: Int) { write(v and 0xFF); write((v ushr 8) and 0xFF) }
    private fun ByteArrayOutputStream.le32(v: Int) {
        write(v and 0xFF); write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF); write((v ushr 24) and 0xFF)
    }
}
