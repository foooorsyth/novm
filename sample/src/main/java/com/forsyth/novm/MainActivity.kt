package com.forsyth.novm


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.forsyth.novm.StateDestroyingEvent.CONFIGURATION_CHANGE
import com.forsyth.novm.StateDestroyingEvent.PROCESS_DEATH

const val TAG = "MainActivity"

fun fakeSha256() : String {
    return "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824"
}

class MainActivity : StateSavingActivity() {
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
                add<TestFragment>(R.id.fragment_container, tag = "uniqueTag")
            }
        }
        findViewById<ImageView>(R.id.imageView).setImageBitmap(largeImage)
        findViewById<TextView>(R.id.tv_hash).text = computedHash
    }
}