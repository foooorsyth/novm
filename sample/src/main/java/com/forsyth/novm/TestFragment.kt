package com.forsyth.novm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class TestFragment : StateSavingFragment() {
    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
    var foo: Boolean = false
    // TODO bug: nullable prims for PROCESS_DEATH end up in serializable?
    // TODO investigate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_test, container, false)
    }
    companion object {
        const val TAG = "TestFragment"
        @JvmStatic
        fun newInstance() = TestFragment()
    }
}