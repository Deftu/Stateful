package dev.deftu.stateful

import java.util.function.Consumer

public actual abstract class TargetStateSubscriptionAdapter<T> actual constructor() : StateSubscriptionAdapter<T>() {

    // JVM utility functions

    public fun subscribe(listener: Consumer<T>): () -> Unit {
        return subscribe(listener::accept)
    }

    public fun subscribeOnce(listener: Consumer<T>): () -> Unit {
        return subscribeOnce(listener::accept)
    }

}
