package com.forsyth.novm

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

// TODO GENERATE THIS (activity stuff only)
// TODO then, figure out how to support fragments
// TODO this needs to be generated and not just included in :lib
// TODO because it has android deps (:lib is a plain kotlin/java lib with no android.jar)
open class StateSavingActivity : AppCompatActivity() {

    val stateSaver = StateSaver()
    val fragmentMemory = mutableSetOf<Fragment>()
    val fragmentLifecycleObserver = object: DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            fragmentMemory.add(owner as Fragment)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            if (this@StateSavingActivity.isChangingConfigurations) {

            }
            fragmentMemory.remove(owner as Fragment)
        }
    }
    val fragmentListener = FragmentOnAttachListener { fragmentManager, fragment ->
        fragment.lifecycle.addObserver(fragmentLifecycleObserver)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.addFragmentOnAttachListener(fragmentListener)

        // Restore config change proof state
        @Suppress("DEPRECATION")
        (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
            stateSaver.restoreStateConfigChange(this, retainedState)
        }

        // Restore process death proof state
        if (savedInstanceState != null) {
            stateSaver.restoreStateBundle(this, savedInstanceState)
        }
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        stateSaver.saveStateBundle(this, outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    @CallSuper
    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return stateSaver.saveStateConfigChange(this)
    }
}