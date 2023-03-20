package com.landenlloyd.gesturements

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                    GesturementsApp()
                }
            }
        }
    }
}

enum class GesturementsScreen {
    Title, Instrument
}

@Composable
fun TitleText(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colors.background,
        shape = RoundedCornerShape(8.dp),
        elevation = 8.dp,
        modifier = modifier
            .wrapContentSize(align = Alignment.Center)
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(id = R.string.app_name),
            style = Typography().h5
        )
    }
}

@Composable
fun InstrumentButton(modifier: Modifier = Modifier, onInstrumentButtonClicked: () -> Unit = {}) {
    Button(
        onClick = { onInstrumentButtonClicked() },
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.elevation(8.dp),
        modifier = modifier.wrapContentSize(align = Alignment.Center)
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(id = R.string.instrument_button_text)
        )
    }
}

@Composable
fun TitleColumn(modifier: Modifier = Modifier, onInstrumentButtonClicked: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(align = Alignment.Center)
    ) {
        TitleText()
        InstrumentButton(onInstrumentButtonClicked = onInstrumentButtonClicked)
    }
}

@Composable
fun TitleScreen(modifier: Modifier = Modifier, onInstrumentButtonClicked: () -> Unit = {}) {
    val image = painterResource(id = R.drawable.instrument)

    Box(modifier = modifier) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentHeight(align = Alignment.Top),
            contentScale = ContentScale.FillWidth,
            alpha = 0.75f
        )
        TitleColumn(onInstrumentButtonClicked = onInstrumentButtonClicked)
    }
}

@Composable
fun InstrumentReadingScreen(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = stringResource(id = R.string.instrument_reading_title)
    )
}

@Composable
fun GesturementsApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold {
        NavHost(
            navController = navController,
            startDestination = GesturementsScreen.Title.name,
            modifier = modifier.padding(it)
        ) {
            composable(route = GesturementsScreen.Title.name) {
                TitleScreen(onInstrumentButtonClicked = {
                    navController.navigate(
                        GesturementsScreen.Instrument.name
                    )
                })
            }

            composable(route = GesturementsScreen.Instrument.name) {
                InstrumentReadingScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GesturementsTheme {
        InstrumentReadingScreen()

    }
}