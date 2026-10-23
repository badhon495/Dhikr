package com.dhikr.app.feature.dua

/**
 * Seeded dua category keys and their localized display labels, kept here as
 * plain Kotlin (no `Context`/`stringResource`) so both the Compose screen
 * ([DuaScreen]'s `categoryLabel()`) and the editor ViewModel (no Context
 * available) can resolve a key <-> label mapping consistently.
 *
 * Must stay in sync with `dua_category_*` in strings.xml / values-bn/strings.xml.
 */
internal object DuaCategories {
    /** category key -> (English label, Bangla label) */
    private val knownLabels: Map<String, Pair<String, String>> = mapOf(
        "bathroom" to ("Bathroom" to "বাথরুম"),
        "eating" to ("Eating" to "খাওয়া"),
        "hardship" to ("Hardship" to "কষ্ট"),
        "house" to ("House" to "বাড়ি"),
        "mosque" to ("Mosque" to "মসজিদ"),
        "nature" to ("Nature" to "প্রকৃতি"),
        "seeing" to ("Seeing" to "দেখা"),
        "travel" to ("Travel" to "যাত্রা"),
    )

    /**
     * Resolves [typed] (a free-text category entered by the user) to an
     * existing category key, matching case-insensitively (after trimming)
     * against: known seeded keys, their known localized labels, and the
     * keys/labels already present in [existingCategoryKeys] (so a user
     * re-typing a previously-user-created category like "My Category" also
     * reuses that same key instead of creating a near-duplicate).
     *
     * Falls back to the trimmed input itself (a genuinely new category) when
     * nothing matches.
     */
    fun normalize(typed: String, existingCategoryKeys: Collection<String>): String {
        val trimmed = typed.trim()
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()

        knownLabels.entries.firstOrNull { (key, labels) ->
            key.lowercase() == lower || labels.first.lowercase() == lower || labels.second.lowercase() == lower
        }?.let { return it.key }

        existingCategoryKeys.firstOrNull { it.lowercase() == lower }?.let { return it }

        return trimmed
    }
}
