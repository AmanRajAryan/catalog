package aman.catalog.audio.internal.scanner

import aman.catalog.audio.CatalogConfig

class SmartSplitter(private val config: CatalogConfig) {

  private val tokenPrefix = "##PROTECTED_TOKEN_"

  // Build the split regex from defaults + any custom separators from config.
  private val splitRegex: Regex = run {
    val defaultSeparators = listOf(";", "&", ",", "\\bfeat\\b\\.?", "\\bft\\b\\.?")

    val customSafe =
            config.customSplitters
                    .filter { it.isNotBlank() }
                    .map { Regex.escape(it) }

    val allSeparators = defaultSeparators + customSafe
    val finalPattern = allSeparators.joinToString("|")

    Regex(finalPattern, RegexOption.IGNORE_CASE)
  }

  // Pre-compile exception regexes once at init time to avoid recompiling on every split() call.
  private val compiledExceptions: List<Pair<String, Regex>> = config.splitExceptions.map { exception ->
      val safePattern = Regex.escape(exception)
      exception to Regex(safePattern, RegexOption.IGNORE_CASE)
  }

  fun split(rawString: String?): List<String> {
    if (rawString.isNullOrBlank()) return listOf("")

    var workingString = rawString.trim()
    val protectedTokens = mutableMapOf<String, String>()

    // 1. PROTECTION PHASE
    // (Masks exceptions like "AC/DC" so they don't get split)
    var tokenIndex = 0
    compiledExceptions.forEach { (originalException, precompiledRegex) ->
      if (workingString.contains(originalException, ignoreCase = true)) {
        val token = "$tokenPrefix$tokenIndex##"
        // Use the pre-compiled regex instead of creating a new one
        workingString = workingString.replace(precompiledRegex, token)
        protectedTokens[token] = originalException
        tokenIndex++
      }
    }

    // 2. SPLITTING PHASE
    // Now uses the dynamic regex containing both defaults and custom splitters
    val rawParts = workingString.split(splitRegex)

    // 3. RESTORATION & CLEANUP PHASE
    return rawParts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part ->
              if (part.contains(tokenPrefix)) {
                var restoredPart = part
                protectedTokens.forEach { (token, originalName) ->
                  restoredPart = restoredPart.replace(token, originalName)
                }
                restoredPart
              } else {
                part
              }
            }
            .distinct()
  }
}
