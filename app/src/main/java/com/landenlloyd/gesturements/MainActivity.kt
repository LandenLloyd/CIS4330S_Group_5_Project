package com.landenlloyd.gesturements

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.landenlloyd.gesturements.ui.theme.GesturementsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GesturementsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting()
                }
            }
        }
    }
}

@Composable
fun Greeting() {
    val image = painterResource(id = R.drawable.instrument)

    Surface(modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background) {
                Box {
                    Image(
                        painter = image,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentHeight(align = Alignment.Top),
                        contentScale = ContentScale.FillWidth,
                        alpha = 0.75f
                    )
                    Surface (
                            color = MaterialTheme.colors.primary,
                            shape = RoundedCornerShape(8.dp),
                            elevation = 8.dp,
                            modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically)
                                .wrapContentWidth(align = Alignment.CenterHorizontally)) {
                        Text(modifier = Modifier.padding(16.dp),
                            text = "Gesturements")
                    }
                }
            }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GesturementsTheme {
        Greeting()
    }
}