package app.aaps.core.ui.search

import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef

/**
 * An item offered by a [SearchableProvider].
 *
 * Only preference categories are provided today: the list is the single source of truth for the
 * built-in preference screens, and navigation resolves a screen key against it.
 */
sealed class SearchableItem {

    /**
     * A preference category/section.
     *
     * @param screenDef The PreferenceSubScreenDef defining the category
     */
    data class Category(
        val screenDef: PreferenceSubScreenDef
    ) : SearchableItem()
}
