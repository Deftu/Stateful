@file:JvmName("ColorUtils")

package dev.deftu.stateful.ext

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import java.awt.Color
import dev.deftu.stateful.map

/** The packed ARGB value, so a different `Color` instance holding the same colour changes nothing. */
public fun State<Color>.rgb(): State<Int> {
    return map { value -> value.rgb }
}

/** The red channel alone; a change confined to another channel does not reach dependents. */
public fun State<Color>.red(): State<Int> {
    return map { value -> value.red }
}

/** The green channel alone; a change confined to another channel does not reach dependents. */
public fun State<Color>.green(): State<Int> {
    return map { value -> value.green }
}

/** The blue channel alone; a change confined to another channel does not reach dependents. */
public fun State<Color>.blue(): State<Int> {
    return map { value -> value.blue }
}

/** The alpha channel alone; a change confined to another channel does not reach dependents. */
public fun State<Color>.alpha(): State<Int> {
    return map { value -> value.alpha }
}

/** One step brighter, per `Color.brighter`. Saturates at white, after which dependents stop waking. */
public fun State<Color>.lighter(): State<Color> {
    return map { value -> value.brighter() }
}

/** One step darker, per `Color.darker`. Saturates at black, after which dependents stop waking. */
public fun State<Color>.darker(): State<Color> {
    return map { value -> value.darker() }
}

/** Writes a packed ARGB value, keeping its alpha rather than forcing it opaque. */
public fun MutableState<Color>.setRgb(value: Int) {
    set(Color(value, true))
}
