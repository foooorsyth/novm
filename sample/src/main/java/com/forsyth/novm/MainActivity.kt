package com.forsyth.novm


import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentOnAttachListener
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateDestroyingEvent.PROCESS_DEATH
import java.io.Serializable

data class SerializableData(
    val str: String,
    val myInt: Int
): Serializable

const val TAG = "MainActivity"

class MainActivity : StateSavingActivity() {

    @Retain(across = [CONFIGURATION_CHANGE])
    var isToggled = false

    @Retain(across = [PROCESS_DEATH])
    var someNullableDouble: Double? = null

    @Retain(across = [PROCESS_DEATH])
    var myText: String? = null

    @Retain(across = [PROCESS_DEATH])
    var intArray: IntArray = intArrayOf(0)

    @Retain(across = [PROCESS_DEATH])
    var primTest: Int = 4

    @Retain(across = [PROCESS_DEATH])
    var stringArraytest: Array<String>? = null

    @Retain(across = [PROCESS_DEATH])
    var intArrayListTest: ArrayList<Int>? = null

    @Retain(across = [PROCESS_DEATH])
    var bundleTest: Bundle? = null

    @Retain(across = [PROCESS_DEATH])
    var serializableTest: SerializableData? = SerializableData("foo", 5)

    val attachListener = FragmentOnAttachListener { fragmentManager, fragment ->
        Log.d(TAG, "fraglistener attach: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        fragment.lifecycle.addObserver(lifecycleObserver)
    }

    val lifecycleObserver = object: DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "fraglistener onCreate: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
            Log.d(TAG, "fraglistener: isChangingConfig?: ${this@MainActivity.isChangingConfigurations}")
            @Suppress("DEPRECATION")
            (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
                stateSaver.restoreStateConfigChange(this, retainedState)
            }
        }
        override fun onDestroy(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "fraglistener onDestroy: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
            Log.d(TAG, "fraglistener: isChangingConfig?: ${this@MainActivity.isChangingConfigurations}")
            if (this@MainActivity.isChangingConfigurations) {
                stateSaver.saveStateConfigChange(this)
            }
            fragment.lifecycle.removeObserver(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate before attach frag listener")
        // NOTE: this listener MUST be setup before super.onCreate is called
        // otherwise, after configuration change, fragments will re-attach
        // BEFORE onAttachListener is added, making the listener useless (it won't get hit)
        supportFragmentManager.addFragmentOnAttachListener(attachListener)
        Log.d(TAG, "onCreate after attach frag listener")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            Log.d(TAG, "onCreate before frag commit")
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<TestFragment>(R.id.fragment_container, tag = "1234")
            }
            Log.d(TAG, "onCreate after frag commit")
        }
        Log.d(TAG, "onCreate exit")
    }
}