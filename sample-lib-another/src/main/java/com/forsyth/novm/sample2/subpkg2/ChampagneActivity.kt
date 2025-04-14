package com.forsyth.novm.sample2.subpkg2

import androidx.appcompat.app.AppCompatActivity
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent

class ChampagneActivity : AppCompatActivity() {
    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    var sip = true
}