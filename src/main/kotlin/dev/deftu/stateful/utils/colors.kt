package dev.deftu.stateful.utils

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import java.awt.Color

public val State<Color>.rgb: Int
    get() = get().rgb

public val State<Color>.red: Int
    get() = get().red

public val State<Color>.green: Int
    get() = get().green

public val State<Color>.blue: Int
    get() = get().blue

public val State<Color>.alpha: Int
    get() = get().alpha

public val State<Color>.lighter: Color
    get() = get().brighter()

public val State<Color>.darker: Color
    get() = get().darker()

public var MutableState<Color>.rgb: Int
    get() = get().rgb
    set(value) {
        set(Color(value))
    }
