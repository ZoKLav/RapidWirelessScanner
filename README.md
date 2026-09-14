# Rapid Wireless Scanner

**A local, accountless, cloudless wireless scanner for Android.**

Rapid Wireless Scanner is what happens when a Bluetooth scanner develops trust issues.

It scans what Android can actually expose, inventories what the phone actually contains, and refuses to pretend that every radio can magically be turned into a broadband intelligence platform because somebody saw the word "tactical" on a product listing.

There is no account. There is no cloud dashboard. There is no telemetry backend. There is no analytics SDK. There is no ad network. There is no remote configuration service waiting to decide that the button you liked should move three centimeters to the left. The application does not request Internet permission or storage permission. It runs on the phone, shows you what the phone can see, and otherwise keeps its mouth shut.

If that sounds unusually hostile to modern software design, good.

## What it does

Rapid Wireless Scanner is a collection of radio-specific scanners and hardware inspection tools built around Android's public platform APIs.

The Bluetooth module scans BLE continuously while open and can optionally run Classic Bluetooth discovery. It exposes names, addresses, RSSI, transport type, bond state, service UUIDs, manufacturer data, service data, raw BLE advertisement bytes, Tx power where available, sightings, timestamps, pinning, filtering, stale-device removal, sorting, and CSV export.

The Wi-Fi module scans visible access points and reports SSID, BSSID, RSSI, frequency, channel/band information, security capabilities, Wi-Fi generation information where Android exposes it, and Wi-Fi RTT capability. Compatible 802.11mc/FTM access points can be ranged through Android's RTT API.

The Wi-Fi Direct module enumerates nearby P2P peers exposed by Android.

The NFC module runs as a foreground tag reader and inspects the tag technologies Android exposes, including NFC-A, NFC-B, NFC-F, NFC-V, ISO-DEP, NDEF, MIFARE Classic, MIFARE Ultralight, and related metadata when available.

The cellular module displays serving and neighboring cell information exposed by Android for supported GSM, WCDMA, LTE, NR/5G, and related radio technologies.

The GNSS module shows visible navigation satellites and their reported constellation, SVID, C/N0, elevation, azimuth, fix usage, ephemeris/almanac state, carrier frequency, and other measurements when the device and Android version provide them. GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, and NavIC/IRNSS are handled where supported.

The RF / Hardware Inventory checks the device for Bluetooth, BLE, BLE Channel Sounding, Wi-Fi, Wi-Fi Direct, Wi-Fi Aware, Wi-Fi RTT, NFC, UWB, Thread, GNSS, satellite telephony, consumer IR, USB host/accessory support, vendor radio feature strings, attached USB devices, and several classes of external radio hardware.

It also performs conservative heuristic detection for LoRa/Meshtastic-class hardware and common SDR hardware. Obvious LoRa-family identifiers such as SX126x/SX127x/SX128x, Meshtastic, Heltec, RAK, LilyGO, T-Beam, T-Echo, T-Deck, and RFM9-class devices are flagged strongly. Generic serial bridges such as CP210x, CH340, FTDI, CDC serial, and Espressif interfaces are **not** magically declared to be LoRa radios just because they might be attached to one.

## What it does not do

This is not a Hollywood spectrum analyzer.

It does not crack Wi-Fi passwords. It does not decrypt Bluetooth traffic. It does not read arbitrary NFC cards that require keys the phone does not have. It does not turn a Wi-Fi chipset into a general-purpose SDR. It does not make UWB perform blind discovery when Android requires a configured ranging session. It does not invent Wi-Fi Aware peers without a service name. It does not receive infrared through a transmitter-only Android API. It does not claim that every USB serial adapter is a clandestine sub-GHz modem.

If Android, the chipset vendor, the OEM, or physics refuses to expose something, Rapid Wireless Scanner will tell you that instead of fabricating a reassuring little green checkmark.

A depressing amount of consumer software could benefit from this principle.

## Privacy policy

Here is the privacy policy:

**There is no server.**

The application does not request Android's Internet permission. It does not request storage access. It contains no analytics SDK, advertising SDK, crash-reporting SaaS, account system, cloud synchronization, tracking pixel, remote-control backend, or "anonymous diagnostics" pipeline whose definition of anonymous becomes increasingly philosophical when lawyers arrive.

Some radio observations require Android location or Nearby Devices permissions because Android treats those observations as potentially location-sensitive. That is an operating-system policy, not an excuse to upload them somewhere.

Data displayed by the scanner stays on the device unless **you** deliberately export something using a feature that explicitly gives it to you.

Your phone already reports enough about you to corporations, carriers, operating-system vendors, advertisers, data brokers, app developers, government agencies with paperwork, government agencies without particularly convincing paperwork, and whatever poorly secured marketing database gets breached next Tuesday. Rapid Wireless Scanner has no interest in joining the queue.

## Anti-spyware design

Modern apps have developed the charming habit of treating the person holding the phone as the least trusted participant in the system.

Rapid Wireless Scanner takes the opposite position.

You do not need to create an identity to inspect your own hardware. You do not need to agree to behavioral advertising to see an RSSI value. You do not need a cloud subscription to ask your own Bluetooth radio what it can hear. You do not need a vendor account to learn whether your phone contains UWB, Thread, NFC, or a suspiciously proprietary radio feature.

The application is intentionally local-first because "we need to send it to our server first" is not a technical requirement for displaying information that already exists in your hand.

No login screen will be added for your convenience.

## A brief note for authorities, enterprise device managers, enthusiastic compliance departments, and other people who enjoy clipboards

The existence of a receiver does not imply criminal intent. The existence of a scanner does not imply criminal intent. The existence of technical curiosity does not imply criminal intent.

Showing a user information their own Android device is already permitted to observe is not an invitation to construct a mythology around the Scan button.

Use the software lawfully. Respect property rights, communications law, access-control law, and whatever rules legitimately apply where you live. Beyond that, the developer is not interested in helping transform ordinary technical capability into another excuse for treating competent users as suspicious by default.

If your security model requires ordinary people to remain ignorant of the radios around them, your security model is embarrassing.

## Permissions

Rapid Wireless Scanner asks only for permissions needed by its scanner modules.

Bluetooth scanning uses the appropriate Bluetooth permissions for the Android version in use. Modern Android versions use Nearby Devices permissions; older Android versions may require location permission for Bluetooth discovery.

Wi-Fi scanning and cellular cell information may require precise location permission because Android classifies those observations as location-sensitive. Wi-Fi Direct uses the appropriate Nearby Wi-Fi permission on newer Android versions.

NFC uses the standard NFC permission.

The application does **not** request Internet permission and does **not** request broad storage permission.

If a future feature requires a new permission, it should have a technical reason that survives being asked "why?" more than once.

## Settings

The app includes a dedicated settings page because apparently giving users options is now considered an advanced feature.

You can change the accent color, BLE scan mode, Classic Bluetooth discovery, paired-device visibility, unnamed-device visibility, RSSI cutoff, Wi-Fi scan interval, NFC discovery behavior, result sorting, stale-device timeout, UI refresh interval, new-device vibration, and keep-screen-awake behavior.

Settings save immediately. There is no account to sync them to because your color preference does not require a multinational data-processing agreement.

## Android support

Minimum Android version: **Android 7.0 / API 24**.

Current target API: **36**.

The project is written for **Java 8 source compatibility**. The Android Gradle Plugin itself runs on a modern JDK because Google's build tooling requires one; this does not make the application Java 17 source and does not change the minimum Android version.

Newer radio APIs are runtime-gated. A phone does not acquire UWB, Thread, BLE Channel Sounding, satellite hardware, NFC, or divine intervention merely because the APK was compiled against an SDK that knows those features exist.

## Why this exists

The original version was a fast BLE scanner built for an ESP32-S3 IFF tag experiment. The IFF project moved on from BLE, but the scanner itself remained useful.

So the project was generalized instead of being thrown away.

Then Wi-Fi was added.

Then NFC.

Then cellular.

Then GNSS.

Then hardware inventory.

Then LoRa and SDR heuristics.

This is how software becomes a problem.

At least this problem does not contain ads.

## Source available does not mean community property

This repository uses the **NO FAULTS SOFTWARE LICENSE Version 1.0** by ZoeyKL:

https://github.com/ZoKLav/no_fault_license

The short version is deliberately unfriendly: the software is licensed, not sold; lawful use and reasonable backups are allowed; unmodified redistribution with the license is allowed; modification and derivative-work rights are heavily restricted; and the warranty/liability terms are aggressively not your friend.

The license file is the authority. This README is not a substitute for reading it.

If you came here expecting "MIT but please leave my name somewhere," you have taken a wrong turn.

If you dislike restrictive source-available licenses, there is an extraordinarily advanced technical solution available: **do not use this software.**

No committee meeting is required.

## Warranty

Absolutely not.

The software talks to hardware, firmware, vendor drivers, Android framework APIs, OEM modifications, radios, USB devices, and whatever imaginative interpretation of the Android Compatibility Definition your phone manufacturer shipped this quarter.

Things can fail. Things can report nonsense. OEM APIs can lie. Drivers can crash. Radios can disappear. Android can throttle scans. Vendor firmware can behave like vendor firmware.

The actual license contains the controlling warranty and liability terms. Read it.

If the application correctly identifies every radio on your device, excellent.

If your $1,900 "Tactical Sovereign Operator Pro Max Xtreme" handset advertises seventeen antennas and Android reports three of them, direct your spiritual questions to the manufacturer.

## Contributions

This is not a democracy.

Bug reports are useful. Reproducible technical information is useful. Exact Android versions, device models, radio chipsets, logs, and clear descriptions of broken behavior are useful.

Demands that the app gain an account system, analytics, advertisements, mandatory cloud sync, remote feature flags, social features, engagement metrics, gamification, subscription tiers, or a web portal will be preserved only as evidence that software development was a mistake.

Before submitting modified code, read the license. Then read it again, more slowly.

## Final statement

Your hardware is yours.

Your local radio environment is not a corporate user-engagement opportunity.

A utility should perform its function, disclose what it is doing, avoid lying about what it can detect, and refrain from reporting home like a nervous informant every time you press a button.
