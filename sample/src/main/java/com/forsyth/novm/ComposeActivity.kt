package com.forsyth.novm

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.forsyth.novm.ui.theme.BlueM
import com.forsyth.novm.ui.theme.NoVMTheme
import com.forsyth.novm.ui.theme.PurpleM
import com.forsyth.novm.ui.theme.RedM
import com.forsyth.novm.compose.setContent
import com.forsyth.novm.compose.retainAcrossConfigChange
import com.forsyth.novm.compose.retainAcrossProcessDeath
import com.forsyth.novm.compose.retainAcrossRecomposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeActivity : StateSavingActivity() {

    companion object {
        const val TAG = "ComposeActivity"
        val COLORS: Array<Color> = arrayOf( BlueM, PurpleM, RedM )
    }

    @Retain(across = StateDestroyingEvent.CONFIG_CHANGE)
    lateinit var imagePainter: ImagePainter

    @Retain(across = StateDestroyingEvent.PROCESS_DEATH)
    lateinit var computedHash: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            imagePainter = ImagePainter(ImageBitmap.imageResource(resources, R.mipmap.oxide_grey_g80))
            computedHash = fakeSha256()
        }
        enableEdgeToEdge()
        setContent {
            NoVMTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ){
                        Row(Modifier
                            .weight(1f)
                            .fillMaxWidth()
                        ){
                            Column (Modifier.fillMaxSize()) {
                                Text(computedHash)
                                Image(painter = imagePainter,
                                    contentDescription = "G80 M3 Oxide Grey",
                                    Modifier.fillMaxWidth())
                            }
                        }
                        Row(Modifier
                            .weight(1f)
                            .fillMaxWidth()
                        ){
                            var leftColor by retainAcrossRecomposition { mutableIntStateOf(0) }
                            var middleColor by retainAcrossConfigChange { mutableIntStateOf(1) }
                            var rightColor by retainAcrossProcessDeath { mutableIntStateOf(2) }
                            Column(Modifier
                                .weight(1f)
                                .background(COLORS[leftColor % 3])
                                .fillMaxHeight()
                                .clickable {
                                    leftColor += 1
                                },
                            ) {
                                Text(leftColor.toString())
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(COLORS[middleColor % 3])
                                .fillMaxHeight()
                                .clickable {
                                    middleColor += 1
                                }
                            ) {
                                Text(middleColor.toString(), color = Color.White)
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(COLORS[rightColor % 3])
                                .fillMaxHeight()
                                .clickable {
                                    rightColor += 1
                                    // retainedScope survives config change
                                    // has same lifetime as viewModelScope
                                    retainedScope.launch {
                                        withContext(Dispatchers.IO) {
                                            Log.d(TAG, "started 10 second computation that will survive config change. rotate the screen now!")
                                            delay(10_000)
                                            Log.d(TAG, "finished 10 second computation")
                                        }
                                    }
                                }
                            ) {
                                Text(rightColor.toString())
                                Spacer(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}
