package com.forsyth.novm.compose

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.forsyth.novm.StateSavingActivity
import com.forsyth.novm.findViewTreeNonConfigStateRegistryOwner
import com.forsyth.novm.setViewTreeNonConfigStateRegistryOwner

fun StateSavingActivity.setContent(
    parent: CompositionContext? = null,
    content: @Composable () -> Unit
) {
    val existingComposeView =
        window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? ComposeView

    if (existingComposeView != null)
        with(existingComposeView) withScope@ {
            setParentCompositionContext(parent)
            setContent {
                val nonConfigRegistryOwner = this.findViewTreeNonConfigStateRegistryOwner()!!
                val nonConfigRegistry = remember {
                    DisposableNonConfigStateRegistryCompose(existingComposeView, nonConfigRegistryOwner)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        nonConfigRegistry.dispose()
                    }
                }
                CompositionLocalProvider(
                    LocalNonConfigStateRegistryOwner provides nonConfigRegistryOwner,
                    LocalNonConfigStateRegistry provides nonConfigRegistry
                ) {
                    content()
                }
            }
        }
    else
        ComposeView(this).apply {
            // Set content and parent **before** setContentView
            // to have ComposeView create the composition on attach
            setParentCompositionContext(parent)
            setOwners()
            setContent activityScope@ {
                val nonConfigRegistryOwner = this.findViewTreeNonConfigStateRegistryOwner()!!
                val nonConfigRegistry = remember {
                    DisposableNonConfigStateRegistryCompose(this, nonConfigRegistryOwner)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        nonConfigRegistry.dispose()
                    }
                }
                CompositionLocalProvider(
                    LocalNonConfigStateRegistryOwner provides nonConfigRegistryOwner,
                    LocalNonConfigStateRegistry provides nonConfigRegistry
                ) {
                    content()
                }
            }
            // Set the view tree owners before setting the content view so that the inflation
            // process
            // and attach listeners will see them already present
            setContentView(this, DefaultActivityContentLayoutParams)
        }
}



private fun StateSavingActivity.setOwners() {
    val decorView = window.decorView
    if (decorView.findViewTreeLifecycleOwner() == null) {
        decorView.setViewTreeLifecycleOwner(this)
    }
    if (decorView.findViewTreeViewModelStoreOwner() == null) {
        decorView.setViewTreeViewModelStoreOwner(this)
    }
    if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }
    // new stuff below
    if (decorView.findViewTreeNonConfigStateRegistryOwner() == null) {
        decorView.setViewTreeNonConfigStateRegistryOwner(this)
    }
}

private val DefaultActivityContentLayoutParams =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)