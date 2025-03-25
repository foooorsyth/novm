package com.forsyth.novm.sample2

import androidx.appcompat.app.AppCompatActivity
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent

class IntentionalNameCollisionActivity : AppCompatActivity() {
    // TODO need to prevent collisions in key generation
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var thisCollides: Boolean = true
}