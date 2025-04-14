package com.forsyth.novm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

class ColorFragment : StateSavingFragment() {

    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    var colorResInt: Int = 0

    override var identificationStrategy: FragmentIdentificationStrategy = FragmentIdentificationStrategy.BY_TAG

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (savedInstanceState == null) {
            arguments?.let { args ->
                colorResInt = args.getInt("color")
            }
        }
        val root = inflater.inflate(R.layout.fragment_color, container, false)
        if (colorResInt != 0) {
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