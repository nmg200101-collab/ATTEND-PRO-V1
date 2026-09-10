package com.attendpro.foundation.data

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest
import java.util.Base64

/** Copy-first migration. Legacy preferences are deliberately retained for rollback. */
class LegacyPreferencesMigration {
    fun migrateOnce(db: SQLiteDatabase, sourceName: String, legacy: SharedPreferences, now: Long): Boolean {
        require(sourceName.isNotBlank() && sourceName.length <= 120) { "Invalid legacy preference source" }
        require(now >= 0) { "Migration time must be non-negative" }
        db.rawQuery("SELECT 1 FROM migration_state WHERE source=?", arrayOf(sourceName)).use {
            if (it.moveToFirst()) return false
        }

        val snapshot = legacy.all.toSortedMap().map { (key, raw) ->
            require(key.isNotBlank()) { "Legacy preference key cannot be blank" }
            key to LegacyPreferenceCodec.encode(raw)
        }
        val checksum = checksum(sourceName, snapshot)

        db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM migration_state WHERE source=?", arrayOf(sourceName)).use {
                if (it.moveToFirst()) {
                    db.setTransactionSuccessful()
                    return false
                }
            }
            snapshot.forEach { (key, encoded) ->
                db.execSQL(
                    "INSERT INTO legacy_preferences(source,key,value_type,value,copied_at) VALUES(?,?,?,?,?)",
                    arrayOf(sourceName, key, encoded.type.name, encoded.value, now)
                )
            }
            db.execSQL(
                "INSERT INTO migration_state(source,schema_version,completed_at,item_count,checksum_sha256) VALUES(?,?,?,?,?)",
                arrayOf(sourceName, AttendProDatabase.VERSION, now, snapshot.size, checksum)
            )
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    private fun checksum(sourceName: String, values: List<Pair<String, EncodedLegacyPreference>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sourceName.toByteArray(Charsets.UTF_8))
        values.forEach { (key, encoded) ->
            digest.update(0.toByte())
            digest.update(key.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(encoded.type.name.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(encoded.value.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

enum class LegacyPreferenceType { NULL, STRING, INTEGER, LONG, FLOAT, BOOLEAN, STRING_SET }
data class EncodedLegacyPreference(val type: LegacyPreferenceType, val value: String)

/** Lossless, deterministic staging codec. Type information is never inferred from text. */
object LegacyPreferenceCodec {
    fun encode(raw: Any?): EncodedLegacyPreference = when (raw) {
        null -> EncodedLegacyPreference(LegacyPreferenceType.NULL, "")
        is String -> EncodedLegacyPreference(LegacyPreferenceType.STRING, raw)
        is Int -> EncodedLegacyPreference(LegacyPreferenceType.INTEGER, raw.toString())
        is Long -> EncodedLegacyPreference(LegacyPreferenceType.LONG, raw.toString())
        is Float -> EncodedLegacyPreference(LegacyPreferenceType.FLOAT, raw.toRawBits().toString())
        is Boolean -> EncodedLegacyPreference(LegacyPreferenceType.BOOLEAN, if (raw) "1" else "0")
        is Set<*> -> {
            require(raw.all { it is String }) { "SharedPreferences sets must contain only strings" }
            val encoded = raw.filterIsInstance<String>().sorted().joinToString(",") {
                Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
            }
            EncodedLegacyPreference(LegacyPreferenceType.STRING_SET, encoded)
        }
        else -> error("Unsupported SharedPreferences value: ${raw::class.java.name}")
    }

    fun decode(encoded: EncodedLegacyPreference): Any? = when (encoded.type) {
        LegacyPreferenceType.NULL -> null
        LegacyPreferenceType.STRING -> encoded.value
        LegacyPreferenceType.INTEGER -> encoded.value.toInt()
        LegacyPreferenceType.LONG -> encoded.value.toLong()
        LegacyPreferenceType.FLOAT -> Float.fromBits(encoded.value.toInt())
        LegacyPreferenceType.BOOLEAN -> when (encoded.value) {
            "1" -> true
            "0" -> false
            else -> error("Invalid encoded boolean")
        }
        LegacyPreferenceType.STRING_SET -> if (encoded.value.isEmpty()) emptySet<String>() else {
            encoded.value.split(',').mapTo(linkedSetOf()) {
                Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8)
            }
        }
    }
}
