package com.chayewuu.hypermatter.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Backup export / import.
 *
 * Export writes HyperDay's own JSON format:
 * `{"app":"HyperDay","version":1,"exportedAt":<millis>,"events":[CountdownEvent...]}`
 *
 * Import understands both formats:
 *  - HyperDay JSON (object with "events" array, or a bare array)
 *  - Official Days Matter (倒数日) `.idmbaks` V3 backups:
 *      ZIP (traditional ZipCrypto, password "idaily-8313")
 *      → entry "data" (DEFLATE)
 *      → AES-256-CBC (key "0a54d521f66a464ebd20813540" zero-padded to 32
 *        bytes, IV = 16 zero bytes, PKCS5 padding)
 *      → JSON array of commits; model_type "1000" rows are events.
 */
object BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Result of a successful import parse. */
    data class ImportResult(
        val events: List<CountdownEvent>,
        val source: String, // "HyperDay" or "Days Matter"
    )

    // ------------------------------------------------------------------ //
    // Export
    // ------------------------------------------------------------------ //

    suspend fun exportBackup(context: Context, uri: Uri, events: List<CountdownEvent>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JsonObject(
                    mapOf(
                        "app" to JsonPrimitive("HyperDay"),
                        "version" to JsonPrimitive(1),
                        "exportedAt" to JsonPrimitive(System.currentTimeMillis()),
                        "events" to JsonArray(events.map { json.encodeToJsonElement(CountdownEvent.serializer(), it) }),
                    )
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.encodeToString(JsonObject.serializer(), payload).toByteArray(Charsets.UTF_8))
                } ?: error("无法打开输出文件")
            }.isSuccess
        }

    // ------------------------------------------------------------------ //
    // Import
    // ------------------------------------------------------------------ //

    suspend fun importBackup(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选文件")

        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            parseIdmbaks(bytes)
        } else {
            parseHyperDay(String(bytes, Charsets.UTF_8))
        }
    }

    /** HyperDay's own JSON: `{"events":[...]}` or a bare `[...]`. */
    private fun parseHyperDay(text: String): ImportResult {
        val root = json.parseToJsonElement(text)
        val arr: JsonArray = when (root) {
            is JsonObject -> root["events"] as? JsonArray
                ?: error("不是有效的 HyperDay 备份文件")
            is JsonArray -> root
            else -> error("不是有效的 HyperDay 备份文件")
        }
        val events = arr.mapNotNull { el ->
            runCatching {
                json.decodeFromJsonElement(CountdownEvent.serializer(), el)
            }.getOrNull()
        }
        return ImportResult(events, "HyperDay")
    }

    /**
     * Official Days Matter `.idmbaks`.
     *
     * Layout (verified against com.clover.daysmatter's backup code and a
     * real backup file):
     *  - ZIP container, traditional ZipCrypto encryption, password
     *    "idaily-8313" (this password is ALSO the backup-file suffix key
     *    used by the official app).
     *  - Entry "data": AES-256-CBC ciphertext, key =
     *    "0a54d521f66a464ebd20813540" UTF-8 zero-padded to 32 bytes,
     *    IV = 16 zero bytes, PKCS5 padding. Plain text is a JSON array of
     *    commit rows.
     *  - Event rows: model_type "1000", data.a is a field map keyed by
     *    schema aid: 17=name, 6=dueDateString ("yyyy-MM-dd"), 9=eventID,
     *    13=isDelete. Last commit per model wins.
     */
    private fun parseIdmbaks(bytes: ByteArray): ImportResult {
        val zip = ZipArchive(bytes)
        val dataEntry = zip.readEntry("data", ZIP_PASSWORD)
            ?: error("备份文件中缺少 data 条目")

        val aesKey = ByteArray(32).also {
            IDMBAKS_AES_KEY.toByteArray(Charsets.UTF_8).copyInto(it)
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(ByteArray(16)))
        val plain = String(cipher.doFinal(dataEntry), Charsets.UTF_8)

        // model_id -> event (later commits overwrite earlier ones)
        val byId = LinkedHashMap<String, CountdownEvent>()
        val commits = json.parseToJsonElement(plain).jsonArray
        for (commitEl in commits) {
            val commit = commitEl as? JsonObject ?: continue
            if (commit["model_type"]?.jsonPrimitiveOrNull()?.content != "1000") continue
            val a = (commit["data"] as? JsonObject)?.get("a") as? JsonObject ?: continue
            val name = a["17"]?.jsonPrimitiveOrNull()?.content?.trim().orEmpty()
            if (name.isEmpty()) continue
            val id = a["9"]?.jsonPrimitiveOrNull()?.content?.trim().orEmpty()
                .ifEmpty { java.util.UUID.randomUUID().toString() }
            val isDelete = a["13"]?.jsonPrimitiveOrNull()?.content?.toIntOrNull() == 1
            val dateStr = a["6"]?.jsonPrimitiveOrNull()?.content?.trim().orEmpty()
            val epochDay = runCatching {
                java.time.LocalDate.parse(dateStr).toEpochDay()
            }.getOrNull() ?: continue
            if (isDelete) {
                byId.remove(id)
            } else {
                byId[id] = CountdownEvent(id = id, title = name, epochDay = epochDay)
            }
        }
        return ImportResult(byId.values.toList(), "Days Matter")
    }

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
}

/** ZIP layer password of official .idmbaks files. */
private const val ZIP_PASSWORD = "idaily-8313"

/** AES key seed of official .idmbaks inner encryption (zero-padded to 32 bytes). */
private const val IDMBAKS_AES_KEY = "0a54d521f66a464ebd20813540"

// ---------------------------------------------------------------------- //
// Minimal ZIP reader with traditional ZipCrypto (PKWARE) support.
// ---------------------------------------------------------------------- //

private class ZipArchive(private val bytes: ByteArray) {

    data class Entry(
        val name: String,
        val method: Int,
        val crc: Int,
        val compressedSize: Int,
        val localHeaderOffset: Int,
    )

    private val entries: Map<String, Entry> by lazy { readCentralDirectory() }

    /** Returns the decrypted + decompressed content of [name], or null. */
    fun readEntry(name: String, password: String): ByteArray? {
        val entry = entries[name] ?: return null
        val dataOffset = readLocalHeaderDataOffset(entry)
        val encrypted = bytes.copyOfRange(dataOffset, dataOffset + entry.compressedSize)

        val zipCrypto = ZipCrypto(password.toByteArray(Charsets.UTF_8))
        val decrypted = zipCrypto.decrypt(encrypted)

        // First 12 bytes are the encryption header; the last of them must
        // match the high byte of the CRC (official writer always sets it).
        val compressed = decrypted.copyOfRange(12, decrypted.size)
        return if (entry.method == 8) {
            inflateRaw(compressed)
        } else {
            compressed
        }
    }

    private fun readLocalHeaderDataOffset(entry: Entry): Int {
        val off = entry.localHeaderOffset
        require(off + 30 <= bytes.size) { "bad local header offset" }
        val nameLen = readU16(off + 26)
        val extraLen = readU16(off + 28)
        return off + 30 + nameLen + extraLen
    }

    private fun readCentralDirectory(): Map<String, Entry> {
        // Locate the End Of Central Directory record from the tail.
        val eocd = findEocd() ?: error("无效的备份文件（找不到 ZIP 目录）")
        val count = readU16(eocd + 10)
        var ptr = readU32(eocd + 16)

        val map = LinkedHashMap<String, Entry>()
        repeat(count) {
            require(readU32(ptr) == 0x02014b50) { "无效的备份文件（ZIP 目录损坏）" }
            val method = readU16(ptr + 10)
            val crc = readU32(ptr + 16)
            val compressedSize = readU32(ptr + 20)
            val nameLen = readU16(ptr + 28)
            val extraLen = readU16(ptr + 30)
            val commentLen = readU16(ptr + 32)
            val localOffset = readU32(ptr + 42)
            val name = String(bytes, ptr + 46, nameLen, Charsets.UTF_8)
            if (!name.endsWith("/")) {
                map[name] = Entry(name, method, crc, compressedSize, localOffset)
            }
            ptr += 46 + nameLen + extraLen + commentLen
        }
        return map
    }

    private fun findEocd(): Int? {
        val minOffset = (bytes.size - 22 - 0xFFFF).coerceAtLeast(0)
        for (i in bytes.size - 22 downTo minOffset) {
            if (readU32(i) == 0x06054b50) return i
        }
        return null
    }

    private fun readU16(off: Int): Int =
        (bytes[off].toInt() and 0xff) or ((bytes[off + 1].toInt() and 0xff) shl 8)

    private fun readU32(off: Int): Int =
        (bytes[off].toInt() and 0xff) or
            ((bytes[off + 1].toInt() and 0xff) shl 8) or
            ((bytes[off + 2].toInt() and 0xff) shl 16) or
            ((bytes[off + 3].toInt() and 0xff) shl 24)

    private fun inflateRaw(data: ByteArray): ByteArray {
        val inflater = java.util.zip.Inflater(true)
        return InflaterInputStream(ByteArrayInputStream(data), inflater).use { it.readBytes() }
    }
}

/** Traditional PKWARE ZipCrypto stream cipher. */
private class ZipCrypto(password: ByteArray) {

    private val keys = IntArray(3)
    private val crcTable = IntArray(256).also { table ->
        for (i in 0 until 256) {
            var c = i
            repeat(8) {
                c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1
            }
            table[i] = c
        }
    }

    init {
        keys[0] = 305419896
        keys[1] = 591751049
        keys[2] = 878082192
        password.forEach { updateKey(it.toInt() and 0xff) }
    }

    private fun crc32(c: Int): Int = crcTable[c and 0xff]

    private fun updateKey(byte: Int) {
        keys[0] = (keys[0] ushr 8) xor crc32(keys[0] and 0xff xor byte)
        keys[1] = keys[1] + (keys[0] and 0xffff)
        keys[1] = keys[1] * 134775813 + 1
        keys[2] = (keys[2] ushr 8) xor crc32((keys[2] xor keys[1]) and 0xff)
    }

    private fun streamByte(): Int {
        val temp = (keys[2] and 0xffff) or 2
        return ((temp * (temp xor 1)) ushr 8) and 0xff
    }

    fun decrypt(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            val plain = (data[i].toInt() and 0xff) xor streamByte()
            out[i] = plain.toByte()
            updateKey(plain)
        }
        return out
    }
}
