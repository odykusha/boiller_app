package com.boiller.monitor.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LightChangeEvent(
    val timestamp: String,
    val hasLight: Boolean,
)

object LightChangeHistory {
    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_HISTORY = "light_change_history_v1"
    private const val KEY_SEEDED = "light_change_history_seeded_v1"
    private const val MAX_ITEMS = 10

    fun add(context: Context, hasLight: Boolean, timestamp: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"

        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        // не дублюємо 1-в-1 останній запис
        try {
            if (arr.length() > 0) {
                val last = arr.getJSONObject(arr.length() - 1)
                val lastTs = last.optString("ts", "")
                val lastHas = last.optBoolean("has", false)
                if (lastTs == timestamp && lastHas == hasLight) {
                    return
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        val obj = JSONObject()
            .put("ts", timestamp)
            .put("has", hasLight)

        arr.put(obj)

        // обрізаємо до MAX_ITEMS (залишаємо останні)
        val trimmed = if (arr.length() > MAX_ITEMS) {
            val out = JSONArray()
            val start = arr.length() - MAX_ITEMS
            for (i in start until arr.length()) {
                out.put(arr.get(i))
            }
            out
        } else {
            arr
        }

        prefs.edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun getLast(context: Context, limit: Int = MAX_ITEMS): List<LightChangeEvent> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"

        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        val out = ArrayList<LightChangeEvent>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ts = o.optString("ts", "")
            if (ts.isBlank()) continue
            val has = o.optBoolean("has", false)
            out.add(LightChangeEvent(timestamp = ts, hasLight = has))
        }

        // показуємо останні N зверху
        val last = out.takeLast(limit).asReversed()
        return last
    }

    fun isSeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SEEDED, false)
    }

    fun markSeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    fun replaceAll(context: Context, eventsOldToNew: List<LightChangeEvent>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        eventsOldToNew.takeLast(MAX_ITEMS).forEach { ev ->
            arr.put(
                JSONObject()
                    .put("ts", ev.timestamp)
                    .put("has", ev.hasLight)
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}

