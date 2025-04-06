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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.forsyth.novm.ui.theme.BlueM
import com.forsyth.novm.ui.theme.NoVMTheme
import com.forsyth.novm.ui.theme.PurpleM
import com.forsyth.novm.ui.theme.RedM
import com.forsyth.novm.compose.setContent
//import androidx.activity.compose.setContent
import com.forsyth.novm.compose.retain

class ComposeActivity : StateSavingActivity() {
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
                        var lhs = remember { mutableIntStateOf(1) }
                        var rhs = retain { mutableIntStateOf(1) }
                        var result = retain (across = StateDestroyingEvent.PROCESS_DEATH) {
                            mutableIntStateOf(0)
                        }
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
                            Column(Modifier
                                .weight(1f)
                                .background(BlueM)
                                .fillMaxHeight()
                                .clickable {
                                    lhs.intValue += 1
                                    result.intValue = lhs.intValue + rhs.intValue
                                    Log.d("ComposeActivity", "lhs: ${lhs.intValue}")
                                },
                            ) {
                                Text(lhs.intValue.toString())
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(PurpleM)
                                .fillMaxHeight()
                                .clickable {
                                    rhs.intValue += 1
                                    result.intValue = lhs.intValue + rhs.intValue
                                    Log.d("ComposeActivity", "rhs: ${rhs.intValue}")
                                }
                            ) {
                                Text(rhs.intValue.toString(), color = Color.White)
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(RedM)
                                .fillMaxHeight()
                            ) {
                                Text(result.intValue.toString())
                                Spacer(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}
