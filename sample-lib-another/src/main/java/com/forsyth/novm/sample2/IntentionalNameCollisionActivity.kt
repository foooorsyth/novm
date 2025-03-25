package com.forsyth.novm.sample2

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent

class IntentionalNameCollisionActivity : AppCompatActivity() {
    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    var thisCollides: Boolean = true
}