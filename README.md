# AdBlock Test: Ad Blocker Check

Android app that tests whether the user's ad blocker works by attempting test-ad
loads through real ad-network SDKs.

- Play title: **AdBlock Test: Ad Blocker Check**
- Package: `com.dips.adblocktest`
- Company: `boofus productions`

## Import into Android Studio

1. Clone or download this repo.
2. Open Android Studio → **Open** → select the repo root folder.
3. Let Gradle sync (requires internet for dependencies).
4. Run on a device or emulator.

**Note:** The `credentials/` folder is NOT in git (see `.gitignore`). Publisher IDs
are already in `app/src/com/dips/adblocktest/TestConfig.java` (these are public
publisher identifiers, not secrets). All placements use test mode.

## Manual build (no Gradle)

If Gradle is unavailable, use the manual pipeline:

```bash
bash app/build.sh
```

This uses `app/tools/fetch_deps.py` to download AARs from Maven Central,
then compiles with `javac` → `aapt2` → `d8` → `zipalign` → `apksigner`.
Requires the Android SDK (see `~/workspace/android-toolchain/ENV.sh`).

## Ad networks

| Network | SDK | Test mode |
|---------|-----|-----------|
| AdMob | play-services-ads 25.5.0 | Demo ad units |
| Unity Ads | unity-ads 4.21.0 | `testMode=true` |
| ironSource LevelPlay | mediationsdk 9.2.0 | — |
| InMobi | inmobi-ads 10.1.4 | Global test mode |
| Chartboost | chartboost-sdk 9.2.1 | Developer mode |
| Start.io | inapp-sdk 5.3.2 | `setTestAdsEnabled(true)` |
| Liftoff (Vungle) | vungle-ads 7.7.9 | App-level test mode |

## Project structure

- `app/src/` — Java sources (no Kotlin)
- `app/res/` — Android resources
- `app/AndroidManifest.xml` — Manifest with SDK providers/activities
- `app/build.gradle` — Gradle module config
- `app/build.sh` — Manual build script (Gradle alternative)
- `app/tools/fetch_deps.py` — Maven dependency resolver
