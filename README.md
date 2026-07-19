# Disco Icons (+ Liquid Glass Launcher experiment)

**Disco Icons** is a "discomorphism" icon pack for the Nothing Phone 3a:
every app icon rebuilt as a glittering mirror-ball mosaic — disco tiles,
sparkles, and glossy rounded squares. All artwork is generated from scratch
by [tools/generate_icons.py](tools/generate_icons.py) during the build;
nothing is copied from any brand.

All building happens automatically on GitHub's servers (GitHub Actions).
No root required. Nothing needs to be installed on a computer.

## Using the icon pack

1. Download **DiscoIcons.apk** from the
   [latest release](https://github.com/buildwclaude/liquid-glass-launcher/releases/latest)
   and install it (same steps as any APK — see below).
   The release also contains **preview.png**, a picture of the whole set.
2. Make sure your home screen is the **Nothing Launcher**
   (Settings → Apps → Default apps → Home app).
3. Long-press an empty spot on the home screen →
   **Customisation** (or **Home settings**) → **Icon pack** → **Disco Icons**.
4. About 20 popular apps get hand-made disco icons; every other app is
   automatically framed in a silver mirror tile so the whole screen matches.

If your Nothing Launcher version has no icon-pack option, install a free
launcher that does (Nova Launcher or Lawnchair from the Play Store), set it
as the Home app, and pick Disco Icons in its settings.

---

The repository also contains the earlier **Liquid Glass Launcher**
experiment (`LiquidGlassLauncher.apk` in releases) — a custom home screen
inspired by translucent glass design. It's parked, not the main project.

---

## How to get the app on your phone

You do this **on the phone itself** — no cables, no computer.

### 1. Download the APK

1. On your phone, open the browser and go to:
   **https://github.com/buildwclaude/liquid-glass-launcher/releases/latest**
2. Under **Assets**, tap **`LiquidGlassLauncher.apk`**.
3. If the browser warns "this type of file can harm your device" — that is a
   standard warning for *any* APK downloaded outside the Play Store.
   Tap **Download anyway**.

### 2. Install it

1. Open the downloaded file (pull down the notification shade and tap it,
   or open the **Files** app → **Downloads** → tap it).
2. The **first time only**, Android says your browser is
   *"not allowed to install unknown apps"*:
   - Tap **Settings** on that popup.
   - Turn on **Allow from this source**.
   - Tap the back arrow — the install screen returns.
3. Tap **Install**, then **Open** (or find **Liquid Glass** in your app list).
4. If Play Protect asks whether to scan the app, that's fine — let it.

### 3. Make it your home screen

1. Open phone **Settings**.
2. Tap **Apps** → **Default apps** → **Home app**.
3. Choose **Liquid Glass**.
4. Press the home gesture/button — you're in the new launcher.

**To go back to the normal Nothing launcher at any time:** same path —
Settings → Apps → Default apps → Home app → **Nothing Launcher**.
Nothing is lost or changed; it's just a switch.

### Updating to a newer build

Repeat step 1 and 2 whenever a new build is published. The new version
installs **over** the old one (same signature), keeping your setup.
No need to uninstall first.

---

## How the automatic building works

- The app's source code lives in this repository.
- Every time code is pushed, **GitHub Actions** (see
  `.github/workflows/build.yml`) builds the APK on GitHub's servers —
  takes roughly 5–10 minutes.
- The finished APK is published on the **Releases** page under the tag
  `latest`, replacing the previous build.
- Build progress is visible under the **Actions** tab of the repository.

### About the signing key

Every Android app must be digitally "signed". A development signing key is
stored in `signing/debug.keystore` so all builds carry the same signature —
that's what lets updates install over each other. This is a throwaway
development key, **not** a secret that protects anything: don't reuse it for
apps you'd publish to an app store.

---

## Important: the launcher only "sticks" once it is the default

If you just open Liquid Glass like a normal app, Android treats it like a
normal app. To make it behave like a real home screen — always there,
home gesture returns to it — you must set it as the **default Home app**
(step 3 above). Back and home never exit it after that.

## Project roadmap

- **Stage 0 (done):** plain grid of apps, tap to open — proved the
  build-and-install pipeline works.
- **Stage 1+2 (this build):** swipe-up glass app drawer with search,
  swipeable home pages, 4-app glass dock, long-press menus
  (add/remove/uninstall/app info), glass icon tiles with tilt-reactive
  highlights and rim light, real blur (RenderEffect) behind the drawer,
  system wallpaper blur where the device allows it.
- **Next:** drag-and-drop icon rearranging, folders, icon theming,
  more refraction effects.
