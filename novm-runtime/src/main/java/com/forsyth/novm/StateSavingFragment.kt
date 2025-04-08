package com.forsyth.novm

import android.os.Bundle
import androidx.`annotation`.CallSuper
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope

// TODO
// implement NonConfigRegistryOwner? // do we need Frag support?
// (in :novm-compose) implement Compose entry point // again, do we need this?
open class StateSavingFragment : Fragment(), RetainedScopeOwner{
  val stateSaver: StateSaver = provideStateSaver()

  open var identificationStrategy: FragmentIdentificationStrategy =
      FragmentIdentificationStrategy.TAG

  override val retainedScope: CoroutineScope
    get() = (requireActivity() as StateSavingActivity).retainedScope

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
