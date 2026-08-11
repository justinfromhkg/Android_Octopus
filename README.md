# Multi Transit Card Reader for Android

A native, read-only Android NFC application for inspecting physical transit
cards from Hong Kong, Taiwan, Singapore, Japan, mainland China, the San
Francisco Bay Area, Macau, and Malaysia.

Source repository: [justinfromhkg/Android_Octopus](https://github.com/justinfromhkg/Android_Octopus)

## Supported card profiles

| Card profile | What this version can read | Important limitation |
| --- | --- | --- |
| Octopus (Hong Kong) | NFC identifier and community-decoded balance estimate | The estimate is unofficial; verify important amounts with an official reader |
| EasyCard (Taiwan) | NFC identifier and technology metadata | Balance sectors require issuer MIFARE Classic keys |
| iPASS (Taiwan) | NFC identifier and technology metadata | Stored-value data varies by generation and is not decoded here |
| EZ-Link / CEPAS (Singapore) | Card number and balance on compatible legacy CEPAS cards | SimplyGo/account-based cards may not keep a readable local balance |
| Suica / PASMO / ICOCA (Japan) | NFC identifier and latest stored balance | These interoperable cards share system code `0003`; the exact brand is not always distinguishable |
| Yangchengtong (Guangdong) | Balance on compatible CPU/City Union/T-Union cards; identifier fallback | Older MIFARE variants may require keys |
| Shenzhentong (Guangdong) | Balance on compatible CPU/T-Union cards; identifier fallback | Older MIFARE variants may require keys |
| China T-Union | Positive or debt-adjusted balance from compatible public PBOC/T-Union purses | Issuer and card-generation behavior can vary |
| Clipper (San Francisco Bay Area) | Balance from the public application on compatible classic DESFire cards | Newer account-based products may use a different layout |
| Macau Pass | NFC identifier and technology metadata | Protected stored-value data is not decoded |
| Touch 'n Go (Malaysia) | NFC identifier and technology metadata | Balance sectors require issuer MIFARE Classic keys |

“Identifier only” is intentional. The app does not contain issuer secrets,
guess authentication keys, bypass access control, or claim a balance when a
card does not expose one publicly.

## How to use it

1. Use a real NFC-capable Android phone and turn NFC on.
2. Open **Multi Transit Card Reader**.
3. In the required **Select your card** section, choose the exact card profile.
   This tells the app which read-only protocol to use when a card or phone
   supports several NFC technologies.
4. Tap **Scan**.
5. Hold the physical card still against the phone's NFC antenna until the
   result appears. Antenna position varies by phone.

Android emulators cannot scan a physical transit card. This app reads physical
cards only; it does not read cards stored inside Apple Wallet or Google Wallet.

Each selection shows both the international product name and its native name
where one exists, for example `EasyCard · 悠遊卡`, `Octopus · 八達通`, and
`Suica · スイカ`.

## Application languages

The interface includes English, Traditional Chinese, Simplified Chinese,
Japanese, Korean, and Malay. Android 13 and newer can change the language for
this app independently under **Settings > Apps > Multi Transit Card Reader >
Language**. Older Android versions follow the phone language.

All user-interface labels, scan instructions, status messages, balance labels,
regions, and balance-availability explanations are localized. Technical NFC
protocol names and raw card data remain in their standard form.

## Balance availability

Version 0.3.0 corrects Chinese transit balances to use the card format's signed
31-bit value. For compatible T-Union cards, it also reads both public purse 0
and public debt purse 1 and combines them according to the documented parser.

Some cards still cannot expose a balance to a general Android NFC app:

- EasyCard and Touch 'n Go store balance data in MIFARE Classic sectors that
  require issuer keys.
- iPASS and Macau Pass stored-value formats are protected or not publicly
  decoded in this project.
- SimplyGo, newer Clipper cards, and some card generations are account-based or
  use formats different from their legacy public purse.

For those cards, the app displays the NFC identifier plus a localized,
card-specific explanation. It does not guess keys or show a fabricated zero
balance.

## Privacy and safety

- The manifest requests only Android's NFC permission and has no internet
  permission.
- Results remain in memory and disappear when Android ends the app process.
- Every implemented card command is read-only. There is no top-up, payment,
  card-write, or key-guessing code.
- Scan only cards you own or have permission to inspect.
- This is an independent educational prototype and is not affiliated with any
  transit-card issuer.

## Install the APK from GitHub

1. Open the repository's [Releases](https://github.com/justinfromhkg/Android_Octopus/releases)
   page on the Android phone.
2. Download the newest `Multi_Transit_Card_Reader-v*.apk` file.
3. Allow the browser or file manager to install unknown apps if Android asks,
   then open the APK.

Release APKs are debug-signed for direct testing. They are not Google Play
production-signed packages.

## Open and run your own build

1. Install and start Android Studio.
2. Choose **Open** and select
   `/Users/jus-mac/Documents/OctopusReaderAndroid`.
3. Let Gradle sync finish. If prompted, install Android SDK 36 and Build Tools
   36.0.0. Android Studio's bundled JDK 17 is suitable.
4. On the Android phone, enable **Developer options** and **USB debugging**.
5. Connect the phone by USB, approve the debugging prompt, and turn NFC on.
6. Select the phone in Android Studio and click the green **Run** triangle.

No Google Play developer account or Android “team” is needed to run your own
debug build on your own phone.

From Terminal, after Android Studio and its SDK are installed:

```bash
cd /Users/jus-mac/Documents/OctopusReaderAndroid
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

- `MainActivity.kt` enables Android reader mode for NFC-A, NFC-B, and NFC-F.
- `nfc/FelicaProtocol.kt` handles Octopus and Japanese FeliCa reads.
- `nfc/Iso7816TransitProtocol.kt` handles CEPAS and Chinese PBOC/T-Union APDUs.
- `nfc/DesfireTransitProtocol.kt` handles the public classic Clipper application.
- `nfc/TransitCardReader.kt` selects the reader for the chosen profile and
  provides safe identifier-only fallbacks.
- `ui/` contains the Jetpack Compose screen and state holder.
- `app/src/test/` covers command bytes, response parsing, signed balances, and
  the complete profile list without requiring a phone.

The application ID remains `com.example.octopusreader` so this release installs
as an update over earlier versions of this project.

The Gradle project is named `TransitCardReaderAndroid`. The existing local
folder and GitHub repository names are retained so links and checkout paths do
not break.

## Technical references

- [Android advanced NFC guide](https://developer.android.com/develop/connectivity/nfc/advanced-nfc)
- [Android `IsoDep` API](https://developer.android.com/reference/android/nfc/tech/IsoDep)
- [Android `NfcF` API](https://developer.android.com/reference/android/nfc/tech/NfcF)
- [Metrodroid Suica-family constants](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/suica/SuicaConsts.kt)
- [Metrodroid CEPAS protocol](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/card/cepas/CEPASProtocol.kt)
- [Metrodroid China transit-card protocol](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/card/china/ChinaCard.kt)
- [Metrodroid T-Union balance parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/china/TUnionTransitData.kt)
- [Metrodroid EasyCard parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/easycard/EasyCardTransitData.kt)
- [Metrodroid Touch 'n Go parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/touchngo/TouchnGoTransitData.kt)
- [Metrodroid Clipper parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/clipper/ClipperTransitData.kt)

## Apple version

An Android APK cannot run on an iPhone. The separate native iPhone project is
stored at `/Users/jus-mac/Documents/OctopusReader`; its README explains Apple
development-team signing and running the app on a physical iPhone.
