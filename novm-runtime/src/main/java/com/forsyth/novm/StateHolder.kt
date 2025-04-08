package com.forsyth.novm

import kotlinx.coroutines.CoroutineScope

interface StateHolder {
    var nonConfigRegistryState: MutableMap<String, Any?>?
    var retainedScope: CoroutineScope?
}

class EmptyStateHolder : StateHolder {
    override var nonConfigRegistryState: MutableMap<String, Any?>? = null
    override var retainedScope: CoroutineScope? = null
}
