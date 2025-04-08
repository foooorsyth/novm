package com.forsyth.novm

import android.os.Bundle
import androidx.`annotation`.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.Any
import kotlin.Suppress

open class StateSavingActivity :
    AppCompatActivity(),
    NonConfigStateRegistryOwner,
    RetainedScopeOwner {
    companion object {
        private const val TAG = "StateSavingActivity"
    }

    private lateinit var _retainedScope: CoroutineScope
    override val retainedScope: CoroutineScope
        get() = _retainedScope
    val stateSaver: StateSaver = provideStateSaver()
    private var stateHolder: StateHolder? = null
    private val _nonConfigStateRegistry = NonConfigStateRegistry()
    override val nonConfigStateRegistry: NonConfigStateRegistry
        get() = _nonConfigStateRegistry
    @Suppress("LeakingThis")
    private val nonConfigRegistryController = NonConfigStateRegistryController.create(this)

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

    init {
        nonConfigRegistryController.performAttach()
    }
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        // NOTE: this listener MUST be setup before super.onCreate is called
        // otherwise, after configuration change, fragments will re-attach
        // BEFORE onAttachListener is added, making the listener useless
        supportFragmentManager.addFragmentOnAttachListener(fragmentAttachListener)
        @Suppress("DEPRECATION")
        val retainedState = lastCustomNonConfigurationInstance as? StateHolder
        if (retainedState != null) {
            _retainedScope = retainedState.retainedScope!! // always non-null, set in onRetainCNCI
            stateSaver.restoreStateConfigChange(this, retainedState)
        } else {
            if (!::_retainedScope.isInitialized) {
                _retainedScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
            }
        }
        // performRestore MUST be called before onCreate, even if retainedState is null
        // registry is stateful, needs internal 'isRestored' to be true
        nonConfigRegistryController.performRestore(retainedState?.nonConfigRegistryState)
        if (savedInstanceState != null) {
            stateSaver.restoreStateBundle(this, savedInstanceState)
        }
        super.onCreate(savedInstanceState)
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
        sh.retainedScope = this@StateSavingActivity.retainedScope // keep outside of generated code, must work even if no GeneratedStateSaver
        if (sh.nonConfigRegistryState == null) {
            sh.nonConfigRegistryState = mutableMapOf()
        }
        nonConfigRegistryController.performSave(sh.nonConfigRegistryState!!)
        if (stateHolder == null) { // possible? not unless there's a race
            stateHolder = sh
        }
        return stateHolder
    }
}
