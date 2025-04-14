package com.forsyth.novm.sample2

import androidx.appcompat.app.AppCompatActivity
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent

class AnotherLibraryActivity : AppCompatActivity() {
    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    var foo: Boolean = false
}