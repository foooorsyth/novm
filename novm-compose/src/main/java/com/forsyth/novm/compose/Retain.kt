package com.forsyth.novm.compose

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import com.forsyth.novm.StateDestroyingEvent
import com.forsyth.novm.StateSavingActivity

/**
 *
 * Mirrors [androidx.compose.runtime.saveable.rememberSaveable], but for non config state
 */
@Composable
fun <T: Any> retain(
    vararg inputs: Any?,
    across: StateDestroyingEvent = StateDestroyingEvent.PROCESS_DEATH,
    key: String? = null,
    init: () -> T
): T {

    /*
     Their setup flow
     - in AndroidComposeView.android.kt (androidx.compose.ui), viewTreeOwners are set in #onAttachedToWindow
     - in AndroidCompositionLocal.android.kt (androidx.compose.ui), the composable function ProvideAndroidCompositionLocals
     provides viewTreeOwners.savedStateRegistry
        - note the DisposableSavedStateRegistry here -- we need to unregister // TODO
    -  ProvideAndroidCompositionLocals is called in WrappedComposition (Wrapper.android.kt, androidx.compose.ui)
    - WrappedComposition is used in doSetContent (same file), and doSetContent is called in AbstractComposeView.setContent
    - Then, this AbstractComposeView.setContent is called in the ComposeView.

     */
    Log.d("ComposeActivity", "is LNCSRO an SSA?: ${(LocalNonConfigStateRegistryOwner.current is StateSavingActivity)}")
    Log.d("ComposeActivity", "is LSSRO an SSA?: ${(LocalSavedStateRegistryOwner.current is StateSavingActivity)}")
    Log.d("ComposeActivity", "is LSSR non-null?: ${(LocalNonConfigStateRegistry.current != null)}")

    val compositeKey = currentCompositeKeyHash
    // key is the one provided by the user or the one generated by the compose runtime
    val finalKey = if (!key.isNullOrEmpty()) {
        key
    } else {
        compositeKey.toString(MaxSupportedRadix)
    }
    val nonConfigRegistry = LocalNonConfigStateRegistry.current
    val holder = remember {
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

private const val MaxSupportedRadix = 36


