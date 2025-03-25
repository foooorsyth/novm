package com.forsyth.novm.sample

import androidx.appcompat.app.AppCompatActivity
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent

class IntentionalNameCollisionActivity : AppCompatActivity() {
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var thisCollides: Boolean = true
}