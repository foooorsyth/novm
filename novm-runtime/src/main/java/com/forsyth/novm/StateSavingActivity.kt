package com.forsyth.novm

import android.os.Bundle
import androidx.`annotation`.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.Any
import kotlin.Suppress

open class StateSavingActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "StateSavingActivity"
    }

    val stateSaver: StateSaver = provideStateSaver()

    private val attachListener = FragmentOnAttachListener { fragmentManager, fragment ->
        fragment.lifecycle.addObserver(lifecycleObserver)
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            @Suppress("DEPRECATION")
            (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
                stateSaver.restoreStateConfigChange(owner, retainedState)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            if (this@StateSavingActivity.isChangingConfigurations) {
                stateSaver.saveStateConfigChange(owner)
            }
        }
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        // NOTE: this listener MUST be setup before super.onCreate is called
        // otherwise, after configuration change, fragments will re-attach
        // BEFORE onAttachListener is added, making the listener useless (it won't get hit)
        supportFragmentManager.addFragmentOnAttachListener(attachListener)
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
        return stateSaver.saveStateConfigChange(this)
    }
}
