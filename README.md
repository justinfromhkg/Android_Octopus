# Multi Transit Card Reader for Android

A native, read-only Android NFC application for inspecting physical transit
cards from Hong Kong, Taiwan, Singapore, South Korea, Japan, mainland China,
the San Francisco Bay Area, Macau, Malaysia, and the United Kingdom.

Source repository: [justinfromhkg/Android_Octopus](https://github.com/justinfromhkg/Android_Octopus)

## Supported card profiles

| Card profile | What this version can read | Important limitation |
| --- | --- | --- |
| Octopus (Hong Kong) | NFC identifier, FeliCa details, and community-decoded balance estimate with a selectable HK$35/HK$50 card-generation basis; explicitly selects system `8008` on Octopus + Shenzhen/T-Union multi-system cards | The card does not publicly identify the correct balance offset; select the matching issuance type and verify important amounts with an official reader |
| EasyCard (Taiwan) | NFC identifier and technology metadata | Balance sectors require issuer MIFARE Classic keys |
| iPASS (Taiwan) | NFC identifier and technology metadata | Stored-value data varies by generation and is not decoded here |
| EZ-Link / CEPAS (Singapore) | Card number and balance on compatible legacy CEPAS cards | SimplyGo/account-based cards may not keep a readable local balance |
| T-money / 티머니 (South Korea) | Public purse balance, card details, and up to 10 recent public transaction records on compatible KS X 6924 cards | Human-readable route/station names are not reliably available in the public record; some generations may not expose the same application |
| Suica / PASMO / ICOCA (Japan) | NFC identifier and latest stored balance | These interoperable cards share system code `0003`; the exact brand is not always distinguishable |
| Yangchengtong (Guangdong) | Balance on compatible CPU/City Union/T-Union cards; identifier fallback | Older MIFARE variants may require keys |
| Shenzhentong (Guangdong) | Balance on compatible CPU/T-Union cards; identifier fallback | Older MIFARE variants may require keys |
| China T-Union | Positive or debt-adjusted balance, card number, validity, and up to 10 public transaction records | Route/station records contain issuer-specific numeric codes rather than a reliable nationwide name list |
| Clipper (San Francisco Bay Area) | Balance from the public application on compatible classic DESFire cards | Newer account-based products may use a different layout |
| Macau Pass | NFC identifier and technology metadata | Protected stored-value data is not decoded |
| Touch 'n Go (Malaysia) | NFC identifier and technology metadata | Balance sectors require issuer MIFARE Classic keys |
| Oyster (London) | MIFARE generation and safe Classic/DESFire chip metadata | First-generation sector data requires operator keys; newer DESFire Oyster cards do not expose a public balance file |

“Identifier only” is intentional. The app does not contain issuer secrets,
guess authentication keys, bypass access control, or claim a balance when a
card does not expose one publicly.

## How to use it

1. Use a real NFC-capable Android phone and turn NFC on.
2. Open **Multi Transit Card Reader**.
3. In the required **Select your card** section, choose the exact card profile.
   This tells the app which read-only protocol to use when a card or phone
   supports several NFC technologies.
4. For Octopus, choose the matching card generation: **HK$35** for an On-Loan
   physical Octopus issued before 1 October 2017, or **HK$50** for a newer
   physical or mobile Octopus. The older physical-card choice is the default.
5. Tap **Scan**.
6. Hold the physical card still against the phone's NFC antenna until the
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

## Octopus physical-card balance and multi-system cards

Version 0.4.0 removed the single hard-coded Octopus offset. Octopus Cards
Limited documents a HK$35 convenience limit for On-Loan cards issued before
1 October 2017 and HK$50 for newer On-Loan and mobile Octopus. That limit is
also the offset used by the public community-decoded balance record, and the
card does not expose a dependable public generation flag.

The Octopus selector therefore asks which generation is being scanned. The
result also displays the selected basis, raw HK$0.10 units, FeliCa PMm,
system code, IDm, and raw balance block so a reading can be checked without
hiding the conversion. Important balances should still be verified with an
official Octopus reader.

Version 0.5.0 also fixes Octopus + Shenzhentong/T-Union multi-system cards.
Android may initially discover system `8005` on these cards, which is not the
Octopus purse. The reader now requests the available FeliCa systems, explicitly
polls Octopus system `8008`, and uses the Octopus-specific IDm returned by that
poll before reading service `0117`. The result shows both Android's discovery
system and the available FeliCa system codes for troubleshooting.

## T-money and Oyster

For compatible T-money cards, version 0.5.0 selects the public KS X 6924
application `D4100000030001`, reads the public purse balance in Korean won,
decodes available card information, and reads up to ten public 46-byte history
records from SFI 4. The history can show top-up or transit type, amount, balance
after the transaction, time, sequence, and terminal code. The app does not
invent route or station names when the public record does not contain them.

Oyster support is intentionally metadata-only. Older Oyster cards use MIFARE
Classic sectors that require operator keys, and newer cards use DESFire without
a freely readable Oyster balance file. The app identifies the available MIFARE
generation and, for DESFire, sends only the standard read-only `GetVersion`
command. It never guesses sector keys or presents an unavailable balance as
zero.

## T-Union card and transaction details

On compatible China T-Union CPU cards, version 0.4.0 reads only public,
read-only PBOC data:

- positive and debt purses;
- card number, application version, issuer code, and validity dates from
  public SFI 21 when available; and
- up to ten 23-byte transaction records from public SFI 24, including time,
  amount, top-up/fare type, sequence, overdraft, and terminal identifiers.

Common Shenzhen-style records can identify bus and metro/rail modes. For a
bus event the app shows the stored bus/route code. For metro/rail it shows the
stored exit-station and gate codes and explicitly marks the boarding station
as unavailable when the record does not contain it.

T-Union interoperability does not mean all local operators use one public
nationwide route/station-name table. The card history stores compact issuer-
specific numeric identifiers, and generic records normally do not contain
both a boarding and alighting station name. This app preserves those raw codes
instead of presenting an unverified bus number or station name. A future
release can add issuer-specific names when a redistributable, authoritative
mapping is available.

## Balance availability

Version 0.3.0 corrected Chinese transit balances to use the card format's signed
31-bit value. For compatible T-Union cards, it also reads both public purse 0
and public debt purse 1 and combines them according to the documented parser.

Some cards still cannot expose a balance to a general Android NFC app:

- EasyCard and Touch 'n Go store balance data in MIFARE Classic sectors that
  require issuer keys.
- Oyster balance and journey data requires operator-controlled access; only
  safe MIFARE chip metadata is shown.
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
- `nfc/TMoneyProtocol.kt` handles the public T-money application, balance,
  card information, and transaction records.
- `nfc/DesfireTransitProtocol.kt` handles the public classic Clipper application.
  It also parses standard DESFire version metadata for Oyster identification.
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
- [Android `MifareClassic` API](https://developer.android.com/reference/android/nfc/tech/MifareClassic)
- [Official Octopus convenience-limit guidance](https://www.octopus.com.hk/en/consumer/customer-service/faq/get-your-octopus/about-octopus.html)
- [Metrodroid Octopus offset history](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/octopus/OctopusData.kt)
- [Metrodroid Suica-family constants](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/suica/SuicaConsts.kt)
- [Metrodroid CEPAS protocol](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/card/cepas/CEPASProtocol.kt)
- [Metrodroid China transit-card protocol](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/card/china/ChinaCard.kt)
- [Metrodroid T-Union balance parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/china/TUnionTransitData.kt)
- [Metrodroid China transaction record parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/china/ChinaTrip.kt)
- [NFCard T-Union public-file reader](https://github.com/sinpolib/nfcard/blob/master/src/com/sinpo/xnfc/nfc/reader/pboc/TUnion.java)
- [Metrodroid EasyCard parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/easycard/EasyCardTransitData.kt)
- [Metrodroid Touch 'n Go parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/touchngo/TouchnGoTransitData.kt)
- [Metrodroid Clipper parser](https://github.com/metrodroid/metrodroid/blob/04a603ba639f7a270b7bdbf24158c7d601087c29/src/commonMain/kotlin/au/id/micolous/metrodroid/transit/clipper/ClipperTransitData.kt)
- [Metrodroid South Korea / T-money protocol notes](https://github-wiki-see.page/m/metrodroid/metrodroid/wiki/South-Korea)
- [Metrodroid Oyster card-generation notes](https://github-wiki-see.page/m/metrodroid/metrodroid/wiki/Oyster)

## Apple version

An Android APK cannot run on an iPhone. The separate native iPhone project is
stored at `/Users/jus-mac/Documents/OctopusReader`; its README explains Apple
development-team signing and running the app on a physical iPhone.
