package com.forsyth.novm

import android.os.Bundle

interface StateSaver {
    fun saveStateConfigChange(component: Any): StateHolder
    fun restoreStateConfigChange(component: Any, stateHolder: StateHolder)
    fun saveStateBundle(component: Any, bundle: Bundle)
    fun restoreStateBundle(component: Any, bundle: Bundle)
}

class EmptyStateSaver : StateSaver {
    override fun saveStateConfigChange(component: Any): StateHolder { return EmptyStateHolder() }
    override fun restoreStateConfigChange(component: Any, stateHolder: StateHolder) { }
    override fun saveStateBundle(component: Any, bundle: Bundle) { }
    override fun restoreStateBundle(component: Any, bundle: Bundle) { }
}

fun provideStateSaver(): StateSaver {
    try {
        // doesn't exist until codegen
        val clazz = Class.forName("com.forsyth.novm.GeneratedStateSaver")
        return clazz.getDeclaredConstructor().newInstance() as StateSaver
    } catch (ex: ClassNotFoundException) {
        return EmptyStateSaver()
    }
}