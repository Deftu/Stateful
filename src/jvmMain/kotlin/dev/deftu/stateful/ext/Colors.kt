@file:JvmName("ColorUtils")

package dev.deftu.stateful.ext

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import java.awt.Color
import dev.deftu.stateful.map

public fun State<Color>.rgb(): State<Int> {
    return map { value -> value.rgb }
}

public fun State<Color>.red(): State<Int> {
    return map { value -> value.red }
}

public fun State<Color>.green(): State<Int> {
    return map { value -> value.green }
}

public fun State<Color>.blue(): State<Int> {
    return map { value -> value.blue }
}

public fun State<Color>.alpha(): State<Int> {
    return map { value -> value.alpha }
}

public fun State<Color>.lighter(): State<Color> {
    return map { value -> value.brighter() }
}

public fun State<Color>.darker(): State<Color> {
    return map { value -> value.darker() }
}

public fun MutableState<Color>.setRgb(value: Int) {
    set(Color(value, true))
}
