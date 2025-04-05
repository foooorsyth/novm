package com.forsyth.novm

interface StateHolder {
    var nonConfigRegistryState: MutableMap<String, Any?>?
}

class EmptyStateHolder : StateHolder {
    override var nonConfigRegistryState: MutableMap<String, Any?>? = null
}
