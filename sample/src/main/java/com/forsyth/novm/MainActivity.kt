package com.forsyth.novm


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateDestroyingEvent.PROCESS_DEATH


fun fakeSha256() : String {
    return "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824"
}

class MainActivity : StateSavingActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    @Retain(across = [CONFIGURATION_CHANGE])
    lateinit var largeImage: Bitmap

    @Retain(across = [PROCESS_DEATH])
    lateinit var computedHash: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            largeImage = BitmapFactory.decodeResource(resources, R.mipmap.oxide_grey_g80)
            computedHash = fakeSha256()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ColorFragment>(R.id.fragment_container_left, tag = "left", args = bundleOf("color" to R.color.blue))
            }
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ColorFragment>(R.id.fragment_container_middle, tag = "middle", args = bundleOf("color" to R.color.purple))
            }
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ColorFragment>(R.id.fragment_container_right, tag = "right", args = bundleOf("color" to R.color.red))
            }
        }
        findViewById<ImageView>(R.id.imageView).setImageBitmap(largeImage)
        findViewById<TextView>(R.id.tv_hash).text = computedHash

    }
}