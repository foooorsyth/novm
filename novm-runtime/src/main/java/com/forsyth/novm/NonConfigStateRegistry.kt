package com.forsyth.novm

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.arch.core.internal.SafeIterableMap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.savedstate.SavedStateRegistry

/**
 * Mirrors [SavedStateRegistry], but for non config state
 */
class NonConfigStateRegistry {
    @SuppressLint("RestrictedApi")
    private val components = SafeIterableMap<String, NonConfigStateProvider>()
    private var attached = false
    private var restoredState: MutableMap<String, MutableMap<String, Any?>?>? = null

    @get: MainThread
    var isRestored = false
        private set
    private var recreatorProvider: NonConfigRecreator.NonConfigStateProvider? = null
    internal var isAllowingSavingState = true

    @MainThread
    fun consumeRestoredStateForKey(key: String): MutableMap<String, Any?>? {
        check(isRestored) {
            ("You can consumeRestoredStateForKey " +
                    "only after super.onCreate of corresponding component")
        }
        if (restoredState != null) {
            val result = restoredState!![key]
            restoredState?.remove(key)
            if (restoredState?.isEmpty() == true) {
                restoredState = null
            }
            return result
        }
        return null
    }

    @SuppressLint("RestrictedApi")
    @MainThread
    fun registerNonConfigStateProvider(
        key: String,
        provider: NonConfigStateProvider
    ) {
        val previous = components.putIfAbsent(key, provider)
        require(previous == null) {
            ("NonConfigStateProvider with the given key is" +
                    " already registered")
        }
    }

    fun getNonConfigStateProvider(key: String): NonConfigStateProvider? {
        var provider: NonConfigStateProvider? = null
        for ((k, value) in components) {
            if (k == key) {
                provider = value
                break
            }
        }
        return provider
    }
    
    @SuppressLint("RestrictedApi")
    @MainThread
    fun unregisterNonConfigStateProvider(key: String) {
        components.remove(key)
    }

    interface NonConfigAutoRecreated {
        fun onRecreated(owner: NonConfigStateRegistryOwner)
    }

    @MainThread
    fun runOnNextRecreation(clazz: Class<out NonConfigAutoRecreated>) {
        check(isAllowingSavingState) { "Can not perform this action after onSaveInstanceState" }
        recreatorProvider = recreatorProvider ?: NonConfigRecreator.NonConfigStateProvider(this)
        try {
            clazz.getDeclaredConstructor()
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(
                "Class ${clazz.simpleName} must have " +
                        "default constructor in order to be automatically recreated", e
            )
        }
        recreatorProvider?.add(clazz.name)
    }

    @MainThread
    internal fun performAttach(lifecycle: Lifecycle) {
        check(!attached) { "NonConfigStateRegistry was already attached." }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isAllowingSavingState = true
            } else if (event == Lifecycle.Event.ON_STOP) {
                isAllowingSavingState = false
            }
        })
        attached = true
    }

    @MainThread
    internal fun performRestore(
        nonConfigState: MutableMap<String, Any?>?
    ) {
        check(attached) {
            ("You must call performAttach() before calling " +
                    "performRestore")
        }
        check(!isRestored) { "SavedStateRegistry was already restored." }

        @Suppress("UNCHECKED_CAST")
        restoredState = nonConfigState?.get(SAVED_COMPONENTS_KEY) as MutableMap<String, MutableMap<String, Any?>?>?
        isRestored = true
    }

    @SuppressLint("RestrictedApi")
    @MainThread
    fun performSave(outState: MutableMap<String, Any?>) {
        val components = mutableMapOf<String, Any?>()
        if (restoredState != null) {
            components.putAll(restoredState!!)
        }
        val it: Iterator<Map.Entry<String, NonConfigStateProvider>> =
            this.components.iteratorWithAdditions()
        while (it.hasNext()) {
            val (key, value) = it.next()
            components[key] = value.provideState()
        }
        if (components.isNotEmpty()) {
            outState[SAVED_COMPONENTS_KEY] = components
        }
    }

    fun interface NonConfigStateProvider {
        // deserved a better name. "saveState" was confusing because the provider
        // is not doing the saving -- it's just providing it to the registry
        fun provideState(): MutableMap<String, Any?>
    }

    private companion object {
        private const val SAVED_COMPONENTS_KEY =
            "com.forsyth.novm.NonConfigStateRegistry.key"
    }
}
