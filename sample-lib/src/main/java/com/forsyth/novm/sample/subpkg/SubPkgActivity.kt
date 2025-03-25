package com.forsyth.novm.sample.subpkg

import androidx.appcompat.app.AppCompatActivity
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent

class SubPkgActivity : AppCompatActivity() {
    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    var bleh: Boolean = true
}