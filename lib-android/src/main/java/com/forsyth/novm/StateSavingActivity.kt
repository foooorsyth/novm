package com.forsyth.novm

import android.os.Bundle
import android.util.Log
import androidx.`annotation`.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.Any
import kotlin.Suppress

open class StateSavingActivity : AppCompatActivity() {
    companion object {
        const val TAG = "StateSavingActivity"
    }

    val stateSaver: StateSaver = provideStateSaver()

    private val attachListener = FragmentOnAttachListener { fragmentManager, fragment ->
        Log.d(
            TAG,
            "fraglistener attach: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}"
        )
        fragment.lifecycle.addObserver(lifecycleObserver)
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(
                TAG,
                "fraglistener onCreate: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}"
            )
            Log.d(
                TAG,
                "fraglistener: isChangingConfig?: ${this@StateSavingActivity.isChangingConfigurations}"
            )
            @Suppress("DEPRECATION")
            (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
                stateSaver.restoreStateConfigChange(this, retainedState)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(
                TAG,
                "fraglistener onDestroy: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}"
            )
            Log.d(
                TAG,
                "fraglistener: isChangingConfig?: ${this@StateSavingActivity.isChangingConfigurations}"
            )
            if (this@StateSavingActivity.isChangingConfigurations) {
                stateSaver.saveStateConfigChange(this)
            }
            fragment.lifecycle.removeObserver(this)
        }
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate before attach frag listener")
        // NOTE: this listener MUST be setup before super.onCreate is called
        // otherwise, after configuration change, fragments will re-attach
        // BEFORE onAttachListener is added, making the listener useless (it won't get hit)
        supportFragmentManager.addFragmentOnAttachListener(attachListener)
        Log.d(TAG, "onCreate after attach frag listener")
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
        Log.d(TAG, "onSaveInstanceState")
        stateSaver.saveStateBundle(this, outState)
        Log.d(TAG, "${outState.size()}")
        super.onSaveInstanceState(outState)
    }

    @CallSuper
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return stateSaver.saveStateConfigChange(this)
    }
}
