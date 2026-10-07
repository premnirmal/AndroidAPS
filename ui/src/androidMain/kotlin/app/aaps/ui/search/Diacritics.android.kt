package app.aaps.ui.search

import java.text.Normalizer

internal actual fun String.removeDiacritics(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
