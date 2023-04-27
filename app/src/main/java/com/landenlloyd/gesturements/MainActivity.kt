package com.landenlloyd.gesturements

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
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
    Title, Instrument, Synth
}

@Composable
fun TitleText(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier
            .wrapContentSize(align = Alignment.Center),
        text = stringResource(id = R.string.app_name),
        style = Typography().h4,
        color = MaterialTheme.colors.onPrimary
    )
}

@Composable
fun GesturementsButton(
    modifier: Modifier = Modifier,
    onButtonClicked: () -> Unit = {},
    text: String = "placeholder"
) {
    Button(
        onClick = { onButtonClicked() },
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.elevation(8.dp),
        modifier = modifier.fillMaxWidth(0.7f)
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = text
        )
    }
}

@Composable
fun TitleColumn(
    modifier: Modifier = Modifier,
    onSynthesizerButtonClicked: () -> Unit = {},
    onInstrumentButtonClicked: () -> Unit = {},
    onSliderButtonClicked: () -> Unit = {},
    detachListener: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxSize(0.8f),
        color = MaterialTheme.colors.primary.copy(0.75f),
        shape = RoundedCornerShape(8.dp),
        elevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TitleText(modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(32.dp))
            GesturementsButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onButtonClicked = onSynthesizerButtonClicked,
                text = stringResource(id = R.string.synth_button_text)
            )
            GesturementsButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onButtonClicked = onInstrumentButtonClicked,
                text = stringResource(id = R.string.instrument_button_text)
            )
            GesturementsButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onButtonClicked = onSliderButtonClicked,
                text = stringResource(id = R.string.slider_button_text)
            )
            // It is helpful to have a button to detach listeners, allowing the network to catch up
            GesturementsButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onButtonClicked = detachListener,
                text = "Stop Sensing"
            )
        }
    }
}

@Composable
fun TitleScreen(
    modifier: Modifier = Modifier,
    onSynthesizerButtonClicked: () -> Unit,
    onInstrumentButtonClicked: () -> Unit = {},
    onSliderButtonClicked: () -> Unit = {},
    detachListener: () -> Unit = {}
) {
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
        TitleColumn(
            modifier = Modifier.align(Alignment.Center),
            onSynthesizerButtonClicked = onSynthesizerButtonClicked,
            onInstrumentButtonClicked = onInstrumentButtonClicked,
            onSliderButtonClicked = onSliderButtonClicked,
            detachListener = detachListener
        )
    }
}

@Composable
fun GesturementsApp(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

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
                }, onSliderButtonClicked = {
                    navigateToSynthSlider(context)
                }, onSynthesizerButtonClicked = {
                    navController.navigate(GesturementsScreen.Synth.name)
                })
            }
            composable(route = GesturementsScreen.Instrument.name) {
                InstrumentReadingScreen()
            }
            composable(route = GesturementsScreen.Synth.name) {
                SynthPage()
            }
        }
    }
}

fun navigateToSynthSlider(context: Context) {
    val intent = Intent(context, SliderTestActivity::class.java)
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GesturementsTheme {
        GesturementsApp()
    }
}