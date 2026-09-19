package app.aaps.core.ui.search

/**
 * Built-in (non-plugin) preference screens are offered through this interface.
 * Plugin preferences are collected separately via ActivePlugin.
 */
interface SearchableProvider {

    /**
     * Returns the list of items provided by this source.
     */
    fun getSearchableItems(): List<SearchableItem>
}
