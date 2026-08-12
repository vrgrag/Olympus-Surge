# START HERE — Cursor / AI Agent Entry Point (Olympus Surge)

> **Read this file first. Every time you are asked to work on this
> project, read this file, then read every file in `.cursor/rules/`
> before writing any code.**

Olympus Surge is a **native Android (Kotlin + Jetpack Compose + libGDX)**
gray-part flow app. The gray-flow architecture is documented in full in
`.cursor/rules/oracle_gray_guide.md` — that document is the API
contract, the state machine, and the setup manual. This file is the
short "where to look" index that points into it.

The template this project was ported from is **Flutter/Dart**
(`D:\flutter_proj\gray_part_flow\`). Every concept from that template has
been rewritten in Kotlin with **different class names, folder structure
and library choices** for fingerprint safety. Do not copy names or file
layouts from the Flutter template — use the *Oracle*-prefixed native
Kotlin equivalents documented here.

---

## 1. What "gray flow" means (30-second version)

A dual-mode Android app:

- **Gray mode (Oracle Gate)** — full-screen `WebView` hosted by
  `com.olympussurge.olympussurgegame.oracle.OracleGateActivity`, loading
  a URL from the remote config endpoint. Non-organic (paid) users see
  this.
- **White mode (Native Game)** — a native Kotlin + libGDX game
  (`SplashActivity` → `MainActivity` → `BattleActivity`). Organic users
  see this. Also what store reviewers see.

The routing decision is made ONCE per install by the backend, from
AppsFlyer attribution data. It cannot be spoofed client-side.

The process entry point is `oracle/OracleRuntime.kt` (the
`Application`), which primes Firebase and AppsFlyer before any Activity
exists. The launcher Activity is `oracle/OraclePortalActivity.kt`, which
runs the decision and forwards the user either into the WebView shell or
into the game's `SplashActivity`.

Detailed docs: `.cursor/rules/oracle_launch_flow.md` for the state
machine and the reasoning behind each branch;
`.cursor/rules/oracle_gray_guide.md` for the config contract and screen
specs.

### Component map

| File | Role |
|---|---|
| `oracle/OracleRuntime.kt` | `Application`. Firebase init + AppsFlyer `prime`. |
| `oracle/OraclePortalActivity.kt` | Launcher. The whole routing state machine. |
| `oracle/OracleGateActivity.kt` | WebView shell. |
| `oracle/OracleInviteActivity.kt` | Push opt-in, between router and shell. |
| `oracle/OracleOfflineActivity.kt` | No-connection screen, owns its retry loop. |
| `oracle/gateway/OracleAttribution.kt` | AppsFlyer: `prime` / `ignite` / `retrace`. |
| `oracle/gateway/OracleGate.kt` | Config endpoint POST. |
| `oracle/gateway/OracleLink.kt` | Adapter state + real TCP reachability probe. |
| `oracle/gateway/OracleVault.kt` | Plain + encrypted persistence. |
| `oracle/gateway/OracleBeacon.kt` | FCM channel, token, service, tap routing. |
| `oracle/gateway/OracleRelay.kt` | Warm push hand-off to a live shell. |
| `oracle/gateway/OracleUaForge.kt` | Forged UA + shared OkHttp client. |
| `oracle/util/OracleUrlGuard.kt` | http(s) shape check on every inbound URL. |

---

## 2. Where to look for what

| You need to… | Read |
|---|---|
| Understand the whole architecture end-to-end | `.cursor/rules/oracle_gray_guide.md` (top-to-bottom) |
| Change anything about routing, attribution or push | `.cursor/rules/oracle_launch_flow.md` — **read before editing** |
| Know the exact config-endpoint request / response | `oracle_gray_guide.md` §"Config Request Contract" |
| Know the boot sequence + state transitions | `oracle_gray_guide.md` §"Oracle Flow State Machine" |
| Handle the OneLink + offline install scenario | `oracle_gray_guide.md` §"First-Launch UX Contract" |
| Style the loading screen | `oracle_gray_guide.md` §"Screen Layout: OracleLoadingScreen" |
| Style the push-permission screen | `oracle_gray_guide.md` §"Screen Layout: OracleInviteScreen" |
| Style the no-internet screen | `oracle_gray_guide.md` §"Screen Layout: OracleOfflineScreen" |
| Avoid the known Android bugs | `oracle_gray_guide.md` §"Android-Specific Bugs & Fixes" + `.cursor/rules/oracle_pitfalls.md` |
| Wire the WebView safe-area CSS | `.cursor/rules/oracle_webview_safe_area.md` |
| Set the User-Agent suffix (slot theme) | `.cursor/rules/oracle_user_agent.md` |
| Verify a release is ready to ship | `.cursor/FINAL_CHECKLIST.md` |

---

## 3. Order of operations for a change / new project fork

1. **Parse the brief against §"Inputs You Need Before Generating"** in
   `oracle_gray_guide.md`. If any starred (★) input is missing (config
   endpoint, AppsFlyer key, Firebase config, privacy URL, codec seed,
   icons, screen backgrounds), STOP and ask the user in one batched
   question.
2. **Refresh the fingerprint.** Every symbol marked `[FINGERPRINT]` in
   the Kotlin source must be re-diversified for a fork. See §4 below.
3. **Fill config layer.** In this order:
   - `app/src/main/kotlin/.../oracle/config/OracleFacade.kt`
     → identity (`packageId` / `marketId` / `displayName`)
   - `app/src/main/kotlin/.../oracle/config/OracleLegal.kt`
     → privacy + support URLs + config endpoint plaintext
   - `app/src/main/kotlin/.../oracle/crypt/OracleCodec.kt`
     → change `SEED_PHRASE` + `STREAM_LEN` to fresh unique values
   - Regenerate the encoded byte arrays via the packer note at the
     bottom of `oracle_gray_guide.md` §"Setup Checklist"
4. **Sync Android identity.** All three MUST match
   `OracleFacade.PACKAGE_ID`:
   - `app/build.gradle.kts` → `applicationId` + `namespace`
   - `app/src/main/kotlin/**/MainActivity.kt` (and all Kotlin files)
     `package` line + folder path
   - `google-services.json` → `package_name`
5. **Replace assets.** See `assets/README.md` if present. Rename the
   drawable prefix (`oracle_*` today) and swap the four screen files
   (`oracle_offline_landscape.webp`, `oracle_offline_portrait.webp`,
   `oracle_invite_landscape.webp`, `oracle_invite_portrait.webp`) plus
   the loading pair (`splash_landscape.webp` /
   `splash_portrait.webp`).
6. **OneLink** — update the `<data android:host="…"/>` inside the
   `OraclePortalActivity` intent-filter in `AndroidManifest.xml`.
7. **Build & smoke-test.**
   - Debug: `./gradlew.bat :app:assembleDebug` — verify loading bar
     reaches ~90 % during network wait, hits 100 % at the route
     switch, no black frames.
   - Release: `./gradlew.bat :app:bundleRelease`.
8. **QA against `FINAL_CHECKLIST.md`.** Every point must pass on a
   real device before shipping.

---

## 4. Fingerprint — mandatory per-project changes

Everything below MUST differ between projects. Grep for the marker
`[FINGERPRINT]` in the Kotlin source to find every location — this
list mirrors them:

- `oracle/crypt/OracleCodec.kt` → `SEED_PHRASE` + `STREAM_LEN`
- `oracle/config/OracleFacade.kt` → `PACKAGE_ID` / `MARKET_ID` /
  `DISPLAY_NAME`
- `oracle/config/OracleLegal.kt` → privacy / support / home URLs
  (unique per project) and the encoded config-endpoint bytes
- `app/build.gradle.kts` → `applicationId` + `namespace` + `versionCode`
- `app/src/main/kotlin/**/…` — package renaming across the module tree
- `oracle/gateway/OracleBeacon.kt` → `CHANNEL_ID` + `CHANNEL_NAME`
- `AndroidManifest.xml` →
  `com.google.firebase.messaging.default_notification_channel_id`
  value (must match `OracleBeacon.CHANNEL_ID`), OneLink
  `android:host`, `android:label`
- `res/drawable/ic_oracle_beacon.xml` → new monochrome-safe vector
  (flame or equivalent — see `oracle_pitfalls.md` §15)
- Launcher icon PNGs in `res/mipmap-*`

**Also** — vary a few dependency minor versions in
`gradle/libs.versions.toml` (see `oracle_gray_guide.md` §"Library
Versions Reference"). Do not copy any version pin exactly from a
previous project.

---

## 5. Things that must NEVER break (invariants)

If your changes threaten any of the following, STOP and reconsider —
these are the load-bearing behaviours of the gray flow:

1. **Non-organic + offline install boot** must show the No-Wi-Fi
   screen on FRAME ONE from `OraclePortalActivity`. Retry after
   enabling Wi-Fi must reach the WebView through the normal pipeline
   (attribution → gate → OracleGateActivity). No black screen, no game
   screen, no loop.
2. **Loading bar** starts at 0, monotonically increases, hits 1.0 at
   the exact frame we launch the next `Activity`. Never freezes at
   100 %, never jumps back.
3. **"Loading…" caption** cycles dots on a stable 1200 ms
   `LaunchedEffect` loop. Both orientations show the correct
   portrait / landscape background.
4. **`OracleMode.pending` never commits to `native` on a network
   failure.** `native` is written only when the endpoint actually
   answered (`OracleReply.answered`) AND the AppsFlyer conversion map
   was non-empty. Otherwise the user gets the game for this launch and
   the decision stays open. Getting this wrong traps paid users in the
   game forever on a first offline install.
5. **On `native` commit, no further config requests may be sent** for
   the lifetime of the install, and no push may route to the WebView.
   Reinstall is the only reset.
6. **`push_token` + `firebase_project_id` are omitted from the config
   body when FCM is not initialised** — never sent as empty strings
   or `null`.
7. **WebView back gesture / system back** returns one page inside the
   WebView. Back-from-first-page does NOT close the WebView.
8. **File upload input** opens the native chooser (camera + gallery)
   without a filesystem permission dialog.
9. **The AppsFlyer conversion data payload is forwarded verbatim** to
   the config endpoint — no field is renamed, dropped, or added
   except the seven device-side fields defined in the contract.
10. **User-Agent looks like a real Chrome on a real Android device** —
    no `wv/` or `Version/` tokens that would leak the WebView
    identity.
11. **The white part (native game) must launch and be playable
    without internet.** This is a Play-review requirement — no
    network calls on the game path.
12. **Landscape safe-zone is stripped on `OracleOfflineScreen` and
    `OracleInviteScreen`** so the primary buttons stay perfectly
    centered on the tablet (see `oracle_pitfalls.md` §14 and §18).
    The `OracleGateActivity` DOES respect the cutout — that rule
    only applies to the two artwork-based promo screens.
13. **AppsFlyer `init` stays in `OracleRuntime`**, never in an
    Activity, and `start` is only called after a connection is
    confirmed. See `oracle_launch_flow.md` §3.
14. **`OracleBeacon.extractUrl` reads the raw FCM extras as well as
    our own.** A notification drawn by the Firebase SDK never runs our
    service and delivers `url` / `link` as plain string extras.
15. **A push URL never opens the WebView for a `native` install**, and
    a warm URL handed to a live shell is never persisted.

For every invariant there is a matching item in `FINAL_CHECKLIST.md`.

---

## 6. When the user asks something you cannot solve here

- The user is on Windows / PowerShell (paths with spaces are common).
  Always use PowerShell syntax when running commands.
- Prefer `./gradlew.bat` over ad-hoc PowerShell loops.
- iOS is out of scope — this project is Android-only.
- If the user asks you to "make it work like project X", first grep
  project X's `[FINGERPRINT]` markers to know what MUST diverge.
