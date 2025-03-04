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

    @Retain(across = [CONFIGURATION_CHANGE])
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
    var serializableTest: SerializableData = SerializableData("foo", 5)

    val attachListener = FragmentOnAttachListener { fragmentManager, fragment ->
        Log.d(TAG, "frag attach: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        fragment.lifecycle.addObserver(lifecycleObserver)
    }

    val lifecycleObserver = object: DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "frag onCreate: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        }

        override fun onStart(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "frag onStart: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        }

        override fun onResume(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "frag onResume: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        }

        override fun onPause(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "frag onPause: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        }

        override fun onStop(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "frag onStop: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        }

        override fun onDestroy(owner: LifecycleOwner) {
            val fragment = owner as Fragment
            Log.d(TAG, "frag onDestroy: (class: ${fragment.javaClass}, id: ${fragment.id}, tag: ${fragment.tag}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportFragmentManager.addFragmentOnAttachListener(attachListener)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<TestFragment>(R.id.fragment_container, tag = "1234")
            }
        }
    }
}