package com.attendpro.core

import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App-scoped language preference shared by Store and Employee apps.
 *
 * Android 13+ uses the platform per-app locale API so the choice is persisted by Android.
 * Android 8-12 keep the same preference in private app storage and apply it before UI creation.
 */
object AppLanguage {
    private const val PREFS = "attend_pro_language_v2"
    private const val KEY = "language"
    const val AR = "ar"
    const val EN = "en"

    fun code(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tags = context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
            val first = tags.substringBefore(',').substringBefore('-').lowercase(Locale.ROOT)
            if (first == AR || first == EN) return first
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AR)
            ?.takeIf { it == AR || it == EN } ?: AR
    }

    fun isEnglish(context: Context): Boolean = code(context) == EN

    fun set(context: Context, language: String) {
        val normalized = if (language == EN) EN else AR
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, normalized).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(normalized)
        }
        applyToResources(context, normalized)
    }

    @Suppress("DEPRECATION")
    fun applyToResources(context: Context, forced: String? = null) {
        val language = forced ?: code(context)
        val locale = Locale(language)
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        res.updateConfiguration(config, res.displayMetrics)
    }

    fun text(context: Context, arabic: String, english: String): String =
        if (isEnglish(context)) english else arabic

    fun showPicker(activity: Activity, onChanged: (() -> Unit)? = null) {
        val english = isEnglish(activity)
        val title = if (english) "Language" else "اللغة"
        val items = arrayOf("العربية", "English")
        val checked = if (english) 1 else 0
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                val next = if (which == 1) EN else AR
                if (next != code(activity)) {
                    set(activity, next)
                    dialog.dismiss()
                    onChanged?.invoke() ?: activity.recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(if (english) "Cancel" else "إلغاء", null)
            .show()
    }
}
