# Installation & setup

Turn an old phone or tablet into a dedicated OpenPets companion. Install the app, connect it to the same trusted network as your agent, and point the agent at the device.

## Requirements

- **Device:** Android 7.0+ (minSdk 24). It works well on an old device.
- **Build host:** JDK 17, Android SDK platform 34 + build-tools 34, and a `local.properties` file with `sdk.dir=<your SDK path>`.

## Build the APK

- **Debug:** `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **Release:** `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (signed with the debug key so it sideloads).
- **Tests:** `./gradlew :protocol:test`
- **CI:** GitHub Actions builds the release APK on push. Pushing a `v*` tag attaches it to a GitHub Release; open the repo's Releases page to download it.

## Install on the device

Install the APK with `adb install -r app-debug.apk`. Launch the app: the pet appears and a foreground service starts the two TCP servers.

## First-run configuration

- **Open Settings:** Long-press the pet.
- **Settings cover:** bind address; IPC server (enable, port, token); Remote server (enable, port, token); pet picker; per-reaction animation mapping; `waiting` duration; sound (beep or custom picker); haptics; TTS; system notifications; "Require lease"; and start-on-boot.
- **Defaults:** bind `0.0.0.0`, IPC port `18420`, Remote port `18421`, both servers enabled, start-on-boot enabled, and pet `tux`.
- **Tokens:** Tokens are generated once and stay stable across restarts (IPC = 16 random bytes hex; Remote = 32 random bytes base64url). Copy them into your agent config; you can edit them in Settings.
- **Device IP:** Note the LAN IP shown as "Device IP"—agents connect to it.

## Prepare the device for always-on use

- Grant notifications permission (Android 13+).
- Allow the app to run in the background / disable battery optimization for it.
- Keep the screen on: the app already holds a wake lock and requests keep-screen-on.
- **Optional:** Enable start-on-boot and immersive full screen.

## Networking

- The device and the agent must be on the same trusted LAN (or VPN).
- Traffic is unencrypted—use it only on a trusted private network; IPC accepts private/local IPv4 only.
- If nothing connects, check the phone firewall, that both devices are on the same subnet, and that ports `18420`/`18421` are reachable.

## Verify it works

```bash
node tools/send-reaction.mjs <DEVICE_IP> 18420 <IPC_TOKEN> react editing
node tools/send-reaction.mjs <DEVICE_IP> 18420 <IPC_TOKEN> say "Hello from my agent" success
node tools/send-media.mjs <DEVICE_IP> 18420 <IPC_TOKEN> ./screenshot.png "build finished" 8000
```

## Troubleshooting

- **Connection refused / port:** Check that the server is enabled and the port is reachable.
- **Wrong token or IP:** Copy the current token from Settings and use the device's shown LAN IP.
- **Different network:** Put the agent and device on the same trusted LAN or VPN.
- **Phone killed the app:** Disable battery optimization for it.
- **`unknown_method`:** The client may be newer than the app.
- **Media too large:** Media is limited to about 10 MB.
