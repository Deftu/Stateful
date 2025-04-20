package dev.deftu.stateful

public abstract class StateSubscriptionAdapter<T> {

    protected val listeners: MutableList<(T) -> Unit> = mutableListOf()

    public abstract fun get(): T

    public fun subscribe(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return {
            listeners.remove(listener)
        }
    }

    public fun subscribeOnce(listener: (T) -> Unit): () -> Unit {
        val subscription = subscribe(listener)
        return {
            subscription()
            listener.invoke(get())
        }
    }

}

public expect abstract class TargetStateSubscriptionAdapter<T>() : StateSubscriptionAdapter<T>
