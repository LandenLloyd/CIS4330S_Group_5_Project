# CIS4330S_Group_5_Project

Developed By:

- Landen Lloyd
- Shayan Hussain

For Dr. Wang's "Smart Sensing and Devices"

## Project Proposal

Sensors:
· Accelerometer
· Gyrometer

Abstract: Many people say they would like to pick up an instrument, but the barrier of entry to
get started with instruments is often too high for potential musicians. However, almost everyone
nowadays has a smartphone on hand. Gesturements is a smartphone app that allows the user
to make music with gestures while holding their smartphone. Gesturements use a combination
of the accelerometer and gyrometer to synthesize music based on a selected instrument. For
example, users could play the tambourine by moving their device back and forth and adjusting
the pitch by rotating it.

Scenario: For many instruments, the costs of purchasing an instrument are high enough to
prevent many from picking up an instrument. Cheaper alternatives, such as GarageBand, exist
for making music, but many apps use a touchscreen interface that does not come close to the
experience of playing an instrument. Our gesture-based app is cheaper than physical
instruments, and the gesture interface is more engaging than a flat touchscreen. Similarly, some
users may have more control over the sound using our gesture-based system than a
touchscreen system.

## Project Directory Structure

All the Kotlin code written for this project can be found
in app/src/main/java/com.landenlloyd.gesturements.
MainActivity.kt acts as the entry point for Jetpack Compose into this app. The MainActivity class is
responsible for constructing the app tree, registering sensor listeners, providing connections
between the four steps of the sensing pipeline, and managing the Firebase
connection during debugging by providing convenient write functions. InstrumentReading Screen.kt is
the Composable found under the "Instrument" button on the main screen. The sensing pipeline is
separated into four Kotlin files corresponding to the four steps of smart sensing:
DataExtraction.kt, Preprocessing.kt, FeatureExtraction.kt, Classification.kt.

- DataExtraction.kt: The "Sensor3DViewModel" provides callback functions for sensors when a new read
  comes in, separating reads into separate frames of a set size. The content of each frame is stored
  in a "SensorFrame". An instance of "FrameSync" is used to synchronize the time on two
  corresponding frames from the accelerometer and gyroscope.
- Preprocessing.kt: A wrapper over JDSP's signal processing API for transform data to the frequency
  domain, a low-pass filter, and a moving average smooth.

## Third-party Dependencies

- [Apache Commons Math 4](https://commons.apache.org/proper/commons-math/): used for cubic spline
  interpolation and descriptive statistic calculation
- [JDSP](https://github.com/psambit9791/jdsp): a digital signal processing library for Java. Used
  for the Fourier transform and Butterworth low-pass filter
- [JSyn](https://github.com/philburk/jsyn): an audio synthesizer library for Java.
- JSynAndroidAudioDevice.java:
  this Java file was not written by us, but rather provided by the JSyn
  maintainers [here](http://www.softsynth.com/jsyn/beta/jsyn_on_android.php) to allow JSyn to play
  audio over Android devices.
