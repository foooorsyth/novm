package com.forsyth.novm

import android.content.Intent
import android.os.Bundle
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateDestroyingEvent.PROCESS_DEATH

class MainActivity : StateSavingActivity() {

    @State(retainAcross = CONFIGURATION_CHANGE)
    var isToggled = false

    @State(retainAcross = CONFIGURATION_CHANGE)
    var someNullableDouble: Double? = null

    @State(retainAcross = CONFIGURATION_CHANGE)
    var myText: String? = null

    @State(retainAcross = PROCESS_DEATH)
    var myIntent: Intent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}