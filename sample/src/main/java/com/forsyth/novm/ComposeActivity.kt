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

class ComposeActivity : StateSavingActivity() {

    companion object {
        val COLORS: Array<Color> = arrayOf( BlueM, PurpleM, RedM )
    }

    @Retain(across = [StateDestroyingEvent.CONFIGURATION_CHANGE])
    lateinit var imagePainter: ImagePainter

    @Retain(across = [StateDestroyingEvent.PROCESS_DEATH])
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
                            val leftColor = retainAcrossRecomposition { mutableIntStateOf(0) }
                            val middleColor = retainAcrossConfigChange { mutableIntStateOf(1) }
                            val rightColor = retainAcrossProcessDeath { mutableIntStateOf(2) }
                            Column(Modifier
                                .weight(1f)
                                .background(COLORS[leftColor.intValue % 3])
                                .fillMaxHeight()
                                .clickable {
                                    leftColor.intValue += 1
                                },
                            ) {
                                Text(leftColor.intValue.toString())
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(COLORS[middleColor.intValue % 3])
                                .fillMaxHeight()
                                .clickable {
                                    middleColor.intValue += 1
                                }
                            ) {
                                Text(middleColor.intValue.toString(), color = Color.White)
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(COLORS[rightColor.intValue % 3])
                                .fillMaxHeight()
                                .clickable {
                                    rightColor.intValue += 1
                                }
                            ) {
                                Text(rightColor.intValue.toString())
                                Spacer(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}
