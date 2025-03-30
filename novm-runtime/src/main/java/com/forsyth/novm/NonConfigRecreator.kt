package com.forsyth.novm

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Mirrors androidx.savedstate.Recreator (internal) but for non config state
 */
internal class NonConfigRecreator(
    private val owner: NonConfigStateRegistryOwner
) : LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_CREATE) {
            throw AssertionError("Next event must be ON_CREATE")
        }
        source.lifecycle.removeObserver(this)
        val nonConfigStateMap = owner.nonConfigStateRegistry
            .consumeRestoredStateForKey(COMPONENT_KEY) ?: return
        @Suppress("UNCHECKED_CAST")
        val classes: MutableList<String> = (nonConfigStateMap[CLASSES_KEY] as? MutableList<String>)
            ?: throw IllegalStateException(
                "Non config restored state for the component " +
                        "\"$COMPONENT_KEY\" must contain list of strings by the key " +
                        "\"$CLASSES_KEY\""
            )
        for (className: String in classes) {
            reflectiveNew(className)
        }
    }

    private fun reflectiveNew(className: String) {
        val clazz: Class<out NonConfigStateRegistry.NonConfigAutoRecreated> =
            try {
                Class.forName(className, false, NonConfigRecreator::class.java.classLoader)
                    .asSubclass(NonConfigStateRegistry.NonConfigAutoRecreated::class.java)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException("Class $className wasn't found", e)
            }
        val constructor =
            try {
                clazz.getDeclaredConstructor()
            } catch (e: NoSuchMethodException) {
                throw IllegalStateException(
                    "Class ${clazz.simpleName} must have " +
                            "default constructor in order to be automatically recreated", e
                )
            }
        constructor.isAccessible = true
        val newInstance: NonConfigStateRegistry.NonConfigAutoRecreated =
            try {
                constructor.newInstance()
            } catch (e: Exception) {
                throw RuntimeException("Failed to instantiate $className", e)
            }
        newInstance.onRecreated(owner)
    }

    internal class NonConfigStateProvider(registry: NonConfigStateRegistry) :
        NonConfigStateRegistry.NonConfigStateProvider {

        private val classes: MutableSet<String> = mutableSetOf()

        init {
            registry.registerNonConfigStateProvider(COMPONENT_KEY, this)
        }

        override fun provideState(): MutableMap<String, Any?> {
            return mutableMapOf(CLASSES_KEY to classes.toMutableList())
        }

        fun add(className: String) {
            classes.add(className)
        }
    }

    companion object {
        const val CLASSES_KEY = "classes_to_restore"
        const val COMPONENT_KEY = "androidx.savedstate.Restarter"
    }
}
