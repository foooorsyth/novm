package com.forsyth.novm


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateDestroyingEvent.PROCESS_DEATH
import java.io.Serializable

data class SerializableData(
    val str: String,
    val myInt: Int
): Serializable

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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}