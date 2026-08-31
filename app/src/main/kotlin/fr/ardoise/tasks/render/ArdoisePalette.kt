package fr.ardoise.tasks.render

/**
 * One palette, shared by the wallpaper canvas and the Compose theme, so the
 * app and the lock screen never drift apart.
 *
 * Slate and chalk: a dark surface written on in light strokes, wiped and
 * rewritten. Ochre is reserved for what has slipped past its date.
 *
 * Only the ARGB ints exist. A parallel set of Long constants used to be kept
 * for the Compose side; nothing checked that the pairs stayed equal, so a
 * palette tweak could silently desynchronise the two surfaces. Compose's
 * `Color(Int)` overload makes the duplicates unnecessary.
 */
object ArdoisePalette {
    const val SLATE_DEEP = 0xFF141618.toInt()
    const val SLATE_RAISED = 0xFF25282C.toInt()
    const val CHALK = 0xFFF2EFE9.toInt()
    const val CHALK_DIM = 0xFF9A9791.toInt()
    const val OCHRE = 0xFFC9884A.toInt()
    const val OCHRE_SOFT = 0xFFE0A870.toInt()
}
