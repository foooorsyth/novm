package com.forsyth.novm

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity

/*
class MainActivityState {
    var isToggled = false
}
*/

// TODO write this next
class StateHolder {
    var mainActivityState: MainActivityState? = null
}
// TODO then this
class StateSaver {
    companion object {
        const val KEY_MAINACTIVITY_MYTEXT = "MainActivity_myText"
    }
    fun saveStateMem(activity: StateSavingActivity) : StateHolder {
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

    fun restoreStateMem(activity: StateSavingActivity, stateHolder: StateHolder) {
        when (activity) {
            is MainActivity -> {
                if (stateHolder.mainActivityState != null) {
                    activity.isToggled = stateHolder.mainActivityState!!.isToggled
                }
            }
        }
    }

    fun saveStateDisk(activity: StateSavingActivity, bundle: Bundle) {
        when (activity) {
            is MainActivity -> {
                bundle.putString(KEY_MAINACTIVITY_MYTEXT, activity.myText)
            }
        }
    }

    fun restoreStateDisk(activity: StateSavingActivity, bundle: Bundle) {
        when (activity) {
            is MainActivity -> {
                activity.myText = bundle.getString(KEY_MAINACTIVITY_MYTEXT)
            }
        }
    }

}

open class StateSavingActivity : AppCompatActivity() {

    val stateSaver = StateSaver()

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore config change proof state
        @Suppress("DEPRECATION")
        (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
            stateSaver.restoreStateMem(this, retainedState)
        }

        // Restore process death proof state
        if (savedInstanceState != null) {
            stateSaver.restoreStateDisk(this, savedInstanceState)
        }
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        stateSaver.saveStateDisk(this, outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    @CallSuper
    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return stateSaver.saveStateMem(this)
    }
}