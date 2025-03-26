package com.forsyth.novm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.forsyth.novm.ui.theme.BlueM
import com.forsyth.novm.ui.theme.NoVMTheme
import com.forsyth.novm.ui.theme.PurpleM
import com.forsyth.novm.ui.theme.RedM

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
                            ) {
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(PurpleM)
                                .fillMaxHeight()
                            ) {
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(RedM)
                                .fillMaxHeight()
                            ) {
                                Spacer(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}
