package com.forsyth.novm


import android.os.Bundle
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateDestroyingEvent.PROCESS_DEATH

class MainActivity : StateSavingActivity() {

    @State(retainAcross = CONFIGURATION_CHANGE)
    var isToggled = false

    @State(retainAcross = CONFIGURATION_CHANGE)
    var someNullableDouble: Double? = null

    @State(retainAcross = PROCESS_DEATH)
    var myText: String? = null

    @State(retainAcross = PROCESS_DEATH)
    var intArray: IntArray = intArrayOf(0)

    @State(retainAcross = PROCESS_DEATH)
    var primTest: Int = 4

    @State(retainAcross = PROCESS_DEATH)
    var stringArraytest: Array<String>? = null

    @State(retainAcross = PROCESS_DEATH)
    var intArrayListTest: ArrayList<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}