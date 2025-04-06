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
import androidx.compose.runtime.mutableStateOf
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
import java.io.Serializable

class NonSerializableClass()

class SerializableClass() : Serializable

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

                        // survives recomposition -- equivalent to `remember`
                        var foo = retainAcrossRecomposition { mutableIntStateOf(1) }
                        // survives config change (can be `Any?`) without a ViewModel
                        var bar = retainAcrossConfigChange { mutableStateOf(NonSerializableClass()) }
                        // survives process death (must be Bundle type) -- equivalent to `rememberSaveable`
                        var baz = retainAcrossProcessDeath { mutableStateOf(SerializableClass()) }



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
                                    lhs.value.intValue += 1
                                    result.intValue = lhs.value.intValue + rhs.intValue
                                    Log.d("ComposeActivity", "lhs: ${lhs.value.intValue}")
                                },
                            ) {
                                Text(lhs.value.intValue.toString())
                                Spacer(Modifier)
                            }
                            Column(Modifier
                                .weight(1f)
                                .background(PurpleM)
                                .fillMaxHeight()
                                .clickable {
                                    rhs.intValue += 1
                                    result.intValue = lhs.value.intValue + rhs.intValue
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
