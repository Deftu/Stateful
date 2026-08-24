package dev.deftu.stateful

public interface Disposable {
    public val isDisposed: Boolean

    public fun dispose()
}
