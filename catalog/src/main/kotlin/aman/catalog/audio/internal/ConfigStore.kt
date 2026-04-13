package aman.catalog.audio.internal

import aman.catalog.audio.CatalogConfig
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

internal class ConfigStore(context: Context) {

  private val prefs: SharedPreferences =
          context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  companion object {
    private const val PREFS_NAME = "aman.catalog.internal.prefs"
    private const val KEY_SPLIT_EXCEPTIONS = "split_exceptions"
    private const val KEY_SCANNER_IGNORES = "scanner_ignores"
    private const val KEY_CUSTOM_SPLITTERS = "custom_splitters"
    private const val KEY_MIN_DURATION = "min_duration_ms"
  }

  fun save(config: CatalogConfig) {
    val exceptionsArray = JSONArray(config.splitExceptions)
    val ignoresArray = JSONArray(config.scannerIgnores)
    val splittersArray = JSONArray(config.customSplitters)

    prefs.edit()
            .putString(KEY_SPLIT_EXCEPTIONS, exceptionsArray.toString())
            .putString(KEY_SCANNER_IGNORES, ignoresArray.toString())
            .putString(KEY_CUSTOM_SPLITTERS, splittersArray.toString())
            .putLong(KEY_MIN_DURATION, config.minDurationMs)
            .apply()
  }

  fun load(): CatalogConfig? {
    if (!prefs.contains(KEY_SPLIT_EXCEPTIONS)) return null

    return try {
      val exceptionsString = prefs.getString(KEY_SPLIT_EXCEPTIONS, "[]")
      val ignoresString = prefs.getString(KEY_SCANNER_IGNORES, "[]")
      val splittersString = prefs.getString(KEY_CUSTOM_SPLITTERS, "[]")
      val minDuration = prefs.getLong(KEY_MIN_DURATION, 10000L)

      val exceptionsList = parseJsonArray(exceptionsString)
      val ignoresList = parseJsonArray(ignoresString)
      val splittersList = parseJsonArray(splittersString)

      CatalogConfig(exceptionsList, ignoresList, splittersList, minDuration)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  private fun parseJsonArray(jsonString: String?): List<String> {
    if (jsonString.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<String>()
    val array = JSONArray(jsonString)
    for (i in 0 until array.length()) {
      list.add(array.getString(i))
    }
    return list
  }
}
