package com.forsyth.novm

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

class ColorFragment : StateSavingFragment() {

    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    var colorResInt: Int = 0

    override var identificationStrategy: FragmentIdentificationStrategy = FragmentIdentificationStrategy.TAG

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (savedInstanceState == null) {
            arguments?.let { args ->
                colorResInt = args.getInt("color")
            }
        }
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_color, container, false)
        Log.d(TAG, "Ready to restore color $tag. Is it retained?")
        if (colorResInt != 0) {
            Log.d(TAG, "Yes it is")
            root.findViewById<FrameLayout>(R.id.bg).setBackgroundColor(ContextCompat.getColor(requireActivity(), colorResInt))
        }
        return root
    }

    companion object {
        private const val TAG = "TestFragment"

        @JvmStatic
        fun newInstance() = ColorFragment()
    }
}