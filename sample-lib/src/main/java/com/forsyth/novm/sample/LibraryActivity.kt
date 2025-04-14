package com.forsyth.novm.sample

import com.forsyth.novm.Retain
import com.forsyth.novm.StateDestroyingEvent
import com.forsyth.novm.StateSavingActivity

open class LibraryActivity : StateSavingActivity() {

    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    var libraryState: Boolean = false
}