package com.forsyth.novm

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/*
class MainActivityState {
    var isToggled = false
}
class StateHolder {
    var mainActivityState: MainActivityState? = null
}

 */
/*
class StateSaver {
    companion object {
        const val KEY_MAINACTIVITY_MYTEXT = "MainActivity_myText"
    }
    fun saveStateConfigChange(activity: ComponentActivity) : StateHolder {
        val stateHolder = StateHolder()
        when (activity) {
            is MainActivity -> {
                if (stateHolder.mainActivityState == null) {
                    stateHolder.mainActivityState = MainActivityState()
                }
                stateHolder.mainActivityState!!.isToggled = activity.isToggled
            }
        }
        return stateHolder
    }

    fun restoreStateConfigChange(activity: ComponentActivity, stateHolder: StateHolder) {
        when (activity) {
            is MainActivity -> {
                if (stateHolder.mainActivityState != null) {
                    activity.isToggled = stateHolder.mainActivityState!!.isToggled
                }
            }
        }
    }

    fun saveStateBundle(activity: ComponentActivity, bundle: Bundle) {
        when (activity) {
            is MainActivity -> {
                bundle.putString(KEY_MAINACTIVITY_MYTEXT, activity.myText)
            }
        }
    }

    fun restoreStateBundle(activity: ComponentActivity, bundle: Bundle) {
        when (activity) {
            is MainActivity -> {
                activity.myText = bundle.getString(KEY_MAINACTIVITY_MYTEXT)
            }
        }
    }

}
 */
open class StateSavingActivity : AppCompatActivity() {

    val stateSaver = StateSaver()
    val fragmentMemory = mutableSetOf<Fragment>()
    val fragmentLifecycleObserver = object: DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            fragmentMemory.add(owner as Fragment)
        }

        override fun onDestroy(owner: LifecycleOwner) {
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