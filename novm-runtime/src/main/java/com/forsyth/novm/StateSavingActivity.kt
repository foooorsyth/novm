package com.forsyth.novm

import android.os.Bundle
import androidx.`annotation`.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.Any
import kotlin.Suppress

open class StateSavingActivity : AppCompatActivity(), NonConfigStateRegistryOwner {
    companion object {
        private const val TAG = "StateSavingActivity"
    }

    val stateSaver: StateSaver = provideStateSaver()
    private var stateHolder: StateHolder? = null
    override val nonConfigStateRegistry: NonConfigStateRegistry
        get() = TODO("Not yet implemented")

    private val fragmentAttachListener = FragmentOnAttachListener { fragmentManager, fragment ->
        fragment.lifecycle.addObserver(fragmentLifecycleObserver)
    }

    private val fragmentLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            @Suppress("DEPRECATION")
            (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
                stateSaver.restoreStateConfigChange(owner, retainedState)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            if (this@StateSavingActivity.isChangingConfigurations) {
                val sh = stateSaver.saveStateConfigChange(owner, stateHolder)
                if (stateHolder == null) {
                    stateHolder = sh
                }
            }
        }
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        // NOTE: this listener MUST be setup before super.onCreate is called
        // otherwise, after configuration change, fragments will re-attach
        // BEFORE onAttachListener is added, making the listener useless
        supportFragmentManager.addFragmentOnAttachListener(fragmentAttachListener)
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
            stateSaver.restoreStateConfigChange(this, retainedState)
        }
        if (savedInstanceState != null) {
            stateSaver.restoreStateBundle(this, savedInstanceState)
        }
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        stateSaver.saveStateBundle(this, outState)
        super.onSaveInstanceState(outState)
    }

    @CallSuper
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRetainCustomNonConfigurationInstance(): Any? {
        val sh = stateSaver.saveStateConfigChange(this, stateHolder)
        if (stateHolder == null) {
            stateHolder = sh
        }
        return stateHolder
    }
}
