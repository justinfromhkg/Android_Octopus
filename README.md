# Octopus Reader for Android

A local, native Android application that reads a **physical** Hong Kong Octopus
card over NFC-F (FeliCa). It checks system code `8008`, requests block 0 from
service `0117`, and displays the card ID plus a community-decoded balance
estimate.

Source repository: [justinfromhkg/Android_Octopus](https://github.com/justinfromhkg/Android_Octopus)

The initial GitHub release workflow builds an installable, debug-signed APK for
direct testing. It is not a Google Play production-signed package.

## Important limitations

- This is an independent educational prototype, not an official Octopus Cards
  Limited application.
- The balance calculation uses a community-documented card format rather than a
  generally available official Octopus API: `(raw - 500) / 10` HKD.
- Verify important balances with the official Octopus app or a supported reader.
- The application performs only a FeliCa **Read Without Encryption** command. It
  contains no write, top-up, payment, or transaction code.
- Scan only cards you own or have permission to inspect.
- A real NFC-capable Android phone is required. Android emulators cannot scan a
  physical Octopus card.

## Privacy

The manifest requests only the Android NFC permission. It does **not** request
internet access. The most recent result is held only in memory and disappears
when Android ends the app process.

## Open and run after Android Studio finishes downloading

1. Install and start Android Studio.
2. Choose **Open** and select this folder:
   `/Users/jus-mac/Documents/OctopusReaderAndroid`
3. Allow the first Gradle sync to finish. If prompted, install Android SDK 37
   and SDK Build Tools 36.0.0. Android Studio's bundled JDK 17 is suitable.
4. On the Android phone, enable **Developer options** and **USB debugging**.
5. Connect the phone to the Mac by USB and approve the debugging prompt on the
   phone. Make sure NFC is turned on.
6. Select the phone in Android Studio's device menu and click the green **Run**
   triangle.
7. In the app, tap **Scan Octopus Card** and move the physical card around the
   back of the phone until it is detected. NFC antenna placement varies by
   device.

No Google Play developer account or Android “team” is needed to install and run
your own debug build on your own phone.

## Build and test from Terminal

After Android Studio and its SDK are installed:

```bash
cd /Users/jus-mac/Documents/OctopusReaderAndroid
./gradlew test
./gradlew assembleDebug
```

The debug APK will be created under
`app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected phone
with Android Studio, or with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

- `MainActivity.kt` enables Android NFC reader mode for NFC-F cards.
- `nfc/OctopusProtocol.kt` builds and validates the FeliCa command and response.
- `nfc/OctopusTagReader.kt` performs the physical card read off the UI thread.
- `ui/` contains the Compose screen and view model.
- `OctopusProtocolTest.kt` tests command bytes, balance decoding, and error
  handling without requiring a phone.

## Apple version

An Android APK cannot run on an iPhone. The separate native iPhone project is
already stored at `/Users/jus-mac/Documents/OctopusReader`. Its `README.md`
explains how to choose an Apple development team, sign the app, and run it on a
physical iPhone from Xcode.

## Technical references

- [Android NFC overview](https://developer.android.com/develop/connectivity/nfc)
- [Android NFC-F API](https://developer.android.com/reference/android/nfc/tech/NfcF)
- [Android advanced NFC guide](https://developer.android.com/develop/connectivity/nfc/advanced-nfc)
- [Android Gradle plugin 9.2 compatibility](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [Jetpack Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
