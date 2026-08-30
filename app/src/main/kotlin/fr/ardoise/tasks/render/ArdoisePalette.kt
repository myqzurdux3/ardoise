package fr.ardoise.tasks.render

/**
 * One palette, shared by the wallpaper canvas and the Compose theme, so the
 * app and the lock screen never drift apart.
 *
 * Slate and chalk: a dark surface written on in light strokes, wiped and
 * rewritten. Ochre is reserved for what has slipped past its date.
 */
object ArdoisePalette {
    const val SLATE = 0xFF1C1E21.toInt()
    const val SLATE_DEEP = 0xFF141618.toInt()
    const val SLATE_RAISED = 0xFF25282C.toInt()
    const val CHALK = 0xFFF2EFE9.toInt()
    const val CHALK_DIM = 0xFF9A9791.toInt()
    const val OCHRE = 0xFFC9884A.toInt()
    const val OCHRE_SOFT = 0xFFE0A870.toInt()

    /** Same values as ARGB longs, for the Compose layer. */
    const val SLATE_L = 0xFF1C1E21L
    const val SLATE_DEEP_L = 0xFF141618L
    const val SLATE_RAISED_L = 0xFF25282CL
    const val CHALK_L = 0xFFF2EFE9L
    const val CHALK_DIM_L = 0xFF9A9791L
    const val OCHRE_L = 0xFFC9884AL
    const val OCHRE_SOFT_L = 0xFFE0A870L
}
