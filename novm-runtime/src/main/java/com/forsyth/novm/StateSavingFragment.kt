package com.forsyth.novm

import android.os.Bundle
import androidx.`annotation`.CallSuper
import androidx.fragment.app.Fragment


open class StateSavingFragment : Fragment(), NonConfigStateRegistryOwner{
  val stateSaver: StateSaver = provideStateSaver()

  open var identificationStrategy: FragmentIdentificationStrategy =
      FragmentIdentificationStrategy.TAG

  override val nonConfigStateRegistry: NonConfigStateRegistry
    get() = TODO("Not yet implemented")

  @CallSuper
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      stateSaver.restoreStateBundle(this, savedInstanceState)
    }
  }

  @CallSuper
  override fun onSaveInstanceState(outState: Bundle) {
    stateSaver.saveStateBundle(this, outState)
    super.onSaveInstanceState(outState)
  }
}
