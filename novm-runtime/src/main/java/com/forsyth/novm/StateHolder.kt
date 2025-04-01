package com.forsyth.novm

interface StateHolder {
    var nonConfigState: MutableMap<String, MutableMap<String, Any?>?>?
}

class EmptyStateHolder : StateHolder {
    override var nonConfigState: MutableMap<String, MutableMap<String, Any?>?>? = null
}
