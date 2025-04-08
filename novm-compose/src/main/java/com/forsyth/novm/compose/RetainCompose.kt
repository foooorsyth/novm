package com.forsyth.novm.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
inline fun <T> retainAcrossRecomposition(crossinline init: @DisallowComposableCalls () -> T): T =
    remember { init() }

@Composable
inline fun <T> retainAcrossRecomposition(
    input1: Any?,
    crossinline init: @DisallowComposableCalls () -> T
): T = remember(key1 = input1) { init() }

@Composable
inline fun <T> retainAcrossRecomposition(
    input1: Any?,
    input2: Any?,
    crossinline init: @DisallowComposableCalls () -> T
): T = remember(key1 = input1, key2 = input2) { init() }

@Composable
inline fun <T> retainAcrossRecomposition(
    input1: Any?,
    input2: Any?,
    input3: Any?,
    crossinline init: @DisallowComposableCalls () -> T
): T = remember(key1 = input1, key2 = input2, key3 = input3) { init() }

@Composable
inline fun <T> retainAcrossRecomposition(
    vararg inputs: Any?,
    crossinline init: @DisallowComposableCalls () -> T
): T = remember(keys = inputs) { init() }

@Composable
fun <T> retainAcrossConfigChange(
    vararg inputs: Any?,
    key: String? = null,
    init: () -> T
): T {
    /*
         Their setup flow
         - in AndroidComposeView.android.kt (androidx.compose.ui), viewTreeOwners are set in #onAttachedToWindow
         - in AndroidCompositionLocal.android.kt (androidx.compose.ui), the composable function ProvideAndroidCompositionLocals
         provides viewTreeOwners.savedStateRegistry
            - note the DisposableSavedStateRegistry here -- we need to unregister
        -  ProvideAndroidCompositionLocals is called in WrappedComposition (Wrapper.android.kt, androidx.compose.ui)
        - WrappedComposition is used in doSetContent (same file), and doSetContent is called in AbstractComposeView.setContent
        - Then, this AbstractComposeView.setContent is called in the ComposeView.
     */
    val compositeKey = currentCompositeKeyHash
    // key is the one provided by the user or the one generated by the compose runtime
    val finalKey = if (!key.isNullOrEmpty()) {
        key
    } else {
        compositeKey.toString(MaxSupportedRadix)
    }
    val nonConfigRegistry = LocalNonConfigStateRegistry.current
    val holder = retainAcrossRecomposition {
        // value is restored using the registry or created via [init] lambda
        val restored = nonConfigRegistry?.consumeRestored(finalKey)
        val finalValue = restored ?: init()
        NonConfigHolder(nonConfigRegistry, finalKey, finalValue, inputs)
    }

    val value = holder.getValueIfInputsDidntChange(inputs) ?: init()
    SideEffect {
        holder.update(nonConfigRegistry, finalKey, value, inputs)
    }
    @Suppress("UNCHECKED_CAST")
    return value as T
}

@Composable
fun <T : Any> retainAcrossProcessDeath(
    vararg inputs: Any?,
    saver: Saver<T, out Any> = autoSaver(),
    key: String? = null,
    init: () -> T
): T = rememberSaveable(inputs = inputs, saver = saver, key = key) { init() }

private const val MaxSupportedRadix = 36




