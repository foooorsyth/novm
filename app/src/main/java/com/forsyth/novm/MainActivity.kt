package com.forsyth.novm

import android.os.Bundle
import com.forsyth.novm.StateLossEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateLossEvent.PROCESS_DEATH

class MainActivity : StateSavingActivity() {

    @State(retainAcross = CONFIGURATION_CHANGE)
    var isToggled = false

    @State(retainAcross = CONFIGURATION_CHANGE)
    var someNullableDouble: Double? = null

    @State(retainAcross = PROCESS_DEATH)
    var myText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}