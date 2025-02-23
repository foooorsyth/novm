package com.forsyth.novm

import android.content.Intent
import android.os.Bundle
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE

class MainActivity : ComponentActivity() {

    @State(retainAcross = CONFIGURATION_CHANGE)
    var isToggled = false

    @State(retainAcross = CONFIGURATION_CHANGE)
    var someNullableDouble: Double? = null

    @State(retainAcross = CONFIGURATION_CHANGE)
    var myText: String? = null

    @State(retainAcross = CONFIGURATION_CHANGE)
    var myIntent: Intent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}