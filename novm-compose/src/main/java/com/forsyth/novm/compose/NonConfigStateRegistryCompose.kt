package com.forsyth.novm.compose

import android.view.View
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import com.forsyth.novm.NonConfigStateRegistryOwner

interface NonConfigStateRegistryCompose {

    fun consumeRestored(key: String): Any?

    fun registerProvider(key: String, valueProvider: () -> Any?): Entry

    fun canBeSaved(value: Any): Boolean

    fun performSave(): Map<String, List<Any?>>

    interface Entry {
        fun unregister()
    }
}

internal class NonConfigHolder<T>(
    private var registry: NonConfigStateRegistryCompose?,
    private var key: String,
    private var value: T,
    private var inputs: Array<out Any?>
) : RememberObserver {
    private var entry: NonConfigStateRegistryCompose.Entry? = null

    private val valueProvider = {
        requireNotNull(value) { "Value should be initialized" }
    }

    fun update(
        registry: NonConfigStateRegistryCompose?,
        key: String,
        value: T,
        inputs: Array<out Any?>
    ) {
        var entryIsOutdated = false
        if (this.registry !== registry) {
            this.registry = registry
            entryIsOutdated = true
        }
        if (this.key != key) {
            this.key = key
            entryIsOutdated = true
        }
        this.value = value
        this.inputs = inputs
        if (entry != null && entryIsOutdated) {
            entry?.unregister()
            entry = null
            register()
        }
    }

    private fun register() {
        val registry = registry
        require(entry == null) { "entry($entry) is not null" }
        if (registry != null) {
            entry = registry.registerProvider(key, valueProvider)
        }
    }

    override fun onRemembered() {
        register()
    }

    override fun onForgotten() {
        entry?.unregister()
    }

    override fun onAbandoned() {
        entry?.unregister()
    }

    fun getValueIfInputsDidntChange(inputs: Array<out Any?>): T? {
        return if (inputs.contentEquals(this.inputs)) {
            value
        } else {
            null
        }
    }
}


// CharSequence.isBlank() allocates an iterator because it calls indices.all{}
private fun CharSequence.fastIsBlank(): Boolean {
    var blank = true
    for (i in 0 until length) {
        if (!this[i].isWhitespace()) {
            blank = false
            break
        }
    }
    return blank
}

class NonConfigStateRegistryComposeImpl(
    restored: Map<String, List<Any?>>?,
    private val canBeSaved: (Any) -> Boolean
) : NonConfigStateRegistryCompose {

    private val restored: MutableMap<String, List<Any?>> =
        restored?.toMutableMap() ?: mutableMapOf()
    private val valueProviders = mutableMapOf<String, MutableList<() -> Any?>>()

    override fun canBeSaved(value: Any): Boolean = canBeSaved.invoke(value)

    override fun consumeRestored(key: String): Any? {
        val list = restored.remove(key)
        return if (list != null && list.isNotEmpty()) {
            if (list.size > 1) {
                restored[key] = list.subList(1, list.size)
            }
            list[0]
        } else {
            null
        }
    }

    override fun registerProvider(key: String, valueProvider: () -> Any?): NonConfigStateRegistryCompose.Entry {
        require(!key.fastIsBlank()) { "Registered key is empty or blank" }
        @Suppress("UNCHECKED_CAST")
        valueProviders.getOrPut(key) { mutableListOf() }.add(valueProvider)
        return object : NonConfigStateRegistryCompose.Entry {
            override fun unregister() {
                val list = valueProviders.remove(key)
                list?.remove(valueProvider)
                if (!list.isNullOrEmpty()) {
                    // if there are other providers for this key return list back to the map
                    valueProviders[key] = list
                }
            }
        }
    }

    override fun performSave(): Map<String, List<Any?>> {
        val map = restored.toMutableMap()
        valueProviders.forEach { (key, list) ->
            if (list.size == 1) {
                val value = list[0].invoke()
                if (value != null) {
                    map[key] = arrayListOf<Any?>(value)
                }
            } else {
                // if we have multiple providers we should store null values as well to preserve
                // the order in which providers were registered. say there were two providers.
                // the first provider returned null(nothing to save) and the second one returned
                // "1". when we will be restoring the first provider would restore null (it is the
                // same as to have nothing to restore) and the second one restore "1".
                map[key] = List(list.size) { index ->
                    val value = list[index].invoke()
                    //if (value != null) {
                    // TODO do we support null? we should, right?
                    //check(canBeSaved(value)) { generateCannotBeSavedErrorMessage(value) }
                    //}
                    value
                }
            }
        }
        return map
    }
}

internal fun DisposableNonConfigStateRegistryCompose (
    view: View,
    owner: NonConfigStateRegistryOwner
): DisposableNonConfigStateRegistryCompose {
    // The view id of AbstractComposeView is used as a key for SavedStateRegistryOwner. If there
    // are multiple AbstractComposeViews in the same Activity/Fragment with the same id(or with
    // no id) this means only the first view will restore its state. There is also an internal
    // mechanism to provide such id not as an Int to avoid ids collisions via view's tag. This
    // api is currently internal to compose:ui, we will see in the future if we need to make a
    // new public api for that use case.
    val composeView = (view.parent as View)
    // TODO need to set this tag somewhere
    // TODO see R.id.compose_view_saveable_id_tag
    // TODO seems to only be used for popups and dialogs?
    val idFromTag = composeView.getTag(com.forsyth.novm.runtime.R.id.compose_view_nonconfig_id_tag) as? String
    val id = idFromTag ?: composeView.id.toString()
    return DisposableNonConfigStateRegistryCompose(id, owner)
}

internal fun DisposableNonConfigStateRegistryCompose(
    id: String,
    nonConfigStateRegistryOwner: NonConfigStateRegistryOwner
): DisposableNonConfigStateRegistryCompose {
    val key = "${NonConfigStateRegistryCompose::class.java.simpleName}:$id"

    val registry = nonConfigStateRegistryOwner.nonConfigStateRegistry
    val consumed = registry.consumeRestoredStateForKey(key)
    val consumedTransformed: Map<String, List<Any?>>? = consumed?.toMapWithListOfAnyValues() // was toMap

    val nonConfigStateRegistryCompose = NonConfigStateRegistryComposeImpl(consumedTransformed) {
        canBeSavedAsNonConfig(it)
    }
    val registered = try {
        registry.registerNonConfigStateProvider(key) {
            nonConfigStateRegistryCompose.performSave().toMapWithAnyValues() // was toBundle
        }
        true
    } catch (ignore: IllegalArgumentException) {
        // this means there are two AndroidComposeViews composed into different parents with the
        // same view id. currently we will just not save/restore state for the second
        // AndroidComposeView.
        // TODO: we should verify our strategy for such cases and improve it. b/162397322
        false
    }
    return DisposableNonConfigStateRegistryCompose(nonConfigStateRegistryCompose) {
        if (registered) {
            registry.unregisterNonConfigStateProvider(key)
        }
    }
}

/**
 * [SaveableStateRegistry] which can be disposed using [dispose].
 */
internal class DisposableNonConfigStateRegistryCompose(
    nonConfigSateRegistryCompose: NonConfigStateRegistryCompose,
    private val onDispose: () -> Unit
) : NonConfigStateRegistryCompose by nonConfigSateRegistryCompose {

    fun dispose() {
        onDispose()
    }
}

private fun canBeSavedAsNonConfig(value: Any): Boolean {
    if (value is SnapshotMutableState<*>) {
        if (value.policy === neverEqualPolicy<Any?>() ||
            value.policy === structuralEqualityPolicy<Any?>() ||
            value.policy === referentialEqualityPolicy<Any?>()
        ) {
            val stateValue = value.value
            return if (stateValue == null) true else canBeSavedAsNonConfig(
                stateValue
            )
        } else {
            return false
        }
    }
    return true
}

private fun MutableMap<String, Any?>.toMapWithListOfAnyValues(): Map<String, List<Any?>> {
    val ret = mutableMapOf<String, List<Any?>>()
    this.keys.forEach { key ->
        val list = get(key) as ArrayList<Any?>
        ret[key] = list
    }
    return ret
}

private fun Map<String, List<Any?>>.toMapWithAnyValues(): MutableMap<String, Any?> {
    val ret = mutableMapOf<String, Any?>()
    this.forEach { (key, list) ->
        val arrayList = if (list is ArrayList<Any?>) list else ArrayList(list) // TODO wtf, is this safe?
        ret[key] = arrayList
    }
    return ret
}