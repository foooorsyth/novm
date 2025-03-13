package com.forsyth.novm

import android.os.Bundle
import androidx.`annotation`.CallSuper
import androidx.appcompat.app.AppCompatActivity
import kotlin.Any
import kotlin.Suppress

open class StateSavingActivity : AppCompatActivity() {
  val stateSaver: StateSaver = provideStateSaver()

  @CallSuper
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    @Suppress("DEPRECATION")
    (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->
      stateSaver.restoreStateConfigChange(this, retainedState)
    }
  }

  @CallSuper
  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
    super.onRestoreInstanceState(savedInstanceState)
    stateSaver.restoreStateBundle(this, savedInstanceState)
  }

  @CallSuper
  override fun onSaveInstanceState(outState: Bundle) {
    stateSaver.saveStateBundle(this, outState)
    super.onSaveInstanceState(outState)
  }

  @CallSuper
  @Suppress("OVERRIDE_DEPRECATION")
  override fun onRetainCustomNonConfigurationInstance(): Any? {
    return stateSaver.saveStateConfigChange(this)
  }
}
