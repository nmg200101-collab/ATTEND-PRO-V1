package com.attendpro.foundation.backup

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal object PreferenceBackupCodec {
    data class Entry(val key: String, val type: String, val value: Any)

    fun capture(context: Context, name: String, include: (String) -> Boolean): JSONObject {
        val entries = JSONArray()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            .filterKeys(include)
            .toSortedMap()
            .forEach { (key, value) ->
                val encoded = encode(key, value) ?: return@forEach
                entries.put(JSONObject().put("key", encoded.key).put("type", encoded.type).put("value", encoded.value))
            }
        return JSONObject().put("name", name).put("entries", entries)
    }

    fun decode(section: JSONObject, allowedNames: Set<String>): Pair<String, List<Entry>> {
        val name = section.optString("name").trim()
        require(name in allowedNames) { "Backup contains an unsupported settings area" }
        val array = section.optJSONArray("entries") ?: throw IllegalArgumentException("Invalid settings backup")
        require(array.length() <= 20_000) { "Backup contains too many settings" }
        val seen = mutableSetOf<String>()
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val key = item.optString("key")
            val type = item.optString("type")
            require(key.isNotBlank() && key.length <= 240 && '\u0000' !in key && seen.add(key)) { "Invalid backup setting key" }
            when (type) {
                "STRING" -> Entry(key, type, item.getString("value"))
                "INT" -> Entry(key, type, item.getInt("value"))
                "LONG" -> Entry(key, type, item.getLong("value"))
                "FLOAT" -> Entry(key, type, item.getDouble("value").toFloat())
                "BOOLEAN" -> Entry(key, type, item.getBoolean("value"))
                "STRING_SET" -> {
                    val values = item.getJSONArray("value")
                    require(values.length() <= 10_000) { "Backup setting set is too large" }
                    Entry(key, type, (0 until values.length()).map { values.getString(it) }.toSet())
                }
                else -> throw IllegalArgumentException("Unsupported backup setting type")
            }
        }
        return name to entries
    }

    fun put(editor: SharedPreferences.Editor, entry: Entry): SharedPreferences.Editor = when (entry.type) {
        "STRING" -> editor.putString(entry.key, entry.value as String)
        "INT" -> editor.putInt(entry.key, entry.value as Int)
        "LONG" -> editor.putLong(entry.key, entry.value as Long)
        "FLOAT" -> editor.putFloat(entry.key, entry.value as Float)
        "BOOLEAN" -> editor.putBoolean(entry.key, entry.value as Boolean)
        "STRING_SET" -> @Suppress("UNCHECKED_CAST") editor.putStringSet(entry.key, (entry.value as Set<String>).toSet())
        else -> throw IllegalArgumentException("Unsupported backup setting type")
    }

    fun putRaw(editor: SharedPreferences.Editor, key: String, value: Any?): SharedPreferences.Editor = when (value) {
        null -> editor.remove(key)
        is String -> editor.putString(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is Boolean -> editor.putBoolean(key, value)
        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> throw IllegalArgumentException("Unsupported stored setting type")
    }

    private fun encode(key: String, value: Any?): Entry? = when (value) {
        is String -> Entry(key, "STRING", value)
        is Int -> Entry(key, "INT", value)
        is Long -> Entry(key, "LONG", value)
        is Float -> Entry(key, "FLOAT", value.toDouble())
        is Boolean -> Entry(key, "BOOLEAN", value)
        is Set<*> -> Entry(key, "STRING_SET", JSONArray(value.filterIsInstance<String>().sorted()))
        else -> null
    }
}
