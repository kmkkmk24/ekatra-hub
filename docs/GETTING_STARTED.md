# Getting started: from zero to Ekatra on your phone

This guide assumes you have never built an Android app. Follow the parts in order.
Expect the first time to take about an hour, most of it downloads.

- **Part A**: set up your PC (one time)
- **Part B**: set up your phone (one time)
- **Part C**: run Ekatra on your phone
- **Part D**: make it butter smooth (release build)
- **Part E**: keep it running in the background
- **Part F**: troubleshooting

---

## Part A: set up your PC (one time)

### A1. Install Android Studio
Android Studio is the official tool for building Android apps. It includes everything:
the code editor, the Android SDK (the libraries), the Java runtime and the build tool (Gradle).

1. Go to <https://developer.android.com/studio> and download Android Studio for Windows.
2. Run the installer and keep all the default options (make sure **Android Virtual Device** stays ticked).
3. On first launch, the **Setup Wizard** opens. Choose **Standard**, accept the licences, and
   let it download the SDK. This takes 10 to 20 minutes.
   - Short on space on `C:`? Choose **Custom** instead and set **Android SDK Location** to
     a folder on a bigger drive, e.g. `E:\Android\Sdk`. If you'll test on a real phone only,
     you can untick **Android Virtual Device** (the emulator) to save several GB.

### A2. Open the project
1. In Android Studio choose **File > Open** (or **Open** on the welcome screen).
2. Select the folder `E:\Projects\Android-Development\ekatra-hub` (the folder that contains `settings.gradle.kts`).
3. If asked "Trust project?", choose **Trust Project**.
4. Android Studio now runs a **Gradle sync**: it downloads the libraries the app needs.
   Watch the progress bar at the bottom. The first sync takes several minutes.
5. When it finishes you will see **BUILD SUCCESSFUL** / "Sync finished" in the **Build** tab at the bottom.

> If Android Studio offers to upgrade the "Android Gradle Plugin" or other versions, it is
> safe to accept with the **AGP Upgrade Assistant**. If anything fails, copy the red error text
> and ask Claude about it.

### What's in the project (so the folders make sense)
| Path | What it is |
|---|---|
| `app/src/main/AndroidManifest.xml` | The app's ID card: its name, icon, screens, services and permissions |
| `app/src/main/java/.../service/HubListenerService.kt` | Receives every notification and saves it |
| `app/src/main/java/.../data/` | The on-device database (Room) |
| `app/src/main/java/.../ui/` | The screens, written in Jetpack Compose |
| `app/build.gradle.kts` | Build settings and the list of libraries |
| `gradle/libs.versions.toml` | The versions of those libraries |

---

## Part B: set up your phone (one time)

Your phone must allow a computer to install apps on it. This is called **USB debugging**.

### B1. Turn on Developer options
1. Open **Settings > About phone**.
2. Find **Build number** and tap it **7 times**. You'll see "You are now a developer!"
   (enter your PIN if asked).
   - Samsung: **Settings > About phone > Software information > Build number**
   - Xiaomi / Redmi / POCO: **Settings > About phone > OS version** (tap 7 times)
   - OnePlus / Oppo / Realme: **Settings > About device > Version > Build number**
   - Pixel / Motorola / Nothing: **Settings > About phone > Build number**

### B2. Turn on USB debugging
1. Open **Settings > System > Developer options** (Samsung: at the bottom of Settings;
   Xiaomi: **Settings > Additional settings > Developer options**).
2. Switch on **USB debugging**.
3. Xiaomi only: also switch on **Install via USB** and **USB debugging (Security settings)**.
   These need a Mi account and SIM inserted.

### B3. Connect the phone to the PC
1. Connect with a USB cable that supports data (some cheap cables only charge).
2. On the phone, a pop-up asks **"Allow USB debugging?"**. Tick **Always allow from this computer**
   and tap **Allow**.
3. If the phone asks what the USB connection is for, choose **File transfer** if nothing happens.
4. In Android Studio, the device dropdown at the top (next to the green ▶ Run button)
   should now show your phone's name.

> **No cable?** Use Wi-Fi instead (Android 11+): in Android Studio open the device dropdown >
> **Pair Devices Using Wi-Fi**, and on the phone go to **Developer options > Wireless debugging >
> Pair device with QR code**. PC and phone must be on the same Wi-Fi.

---

## Part C: run Ekatra on your phone

1. In Android Studio, pick your phone in the device dropdown.
2. Click the green **▶ Run** button (or press **Shift+F10**).
3. The first build takes a few minutes. Then Ekatra opens on your phone automatically.
   The icon "Ekatra" also appears in your app drawer.

### C1. Grant notification access (on the phone)
1. Ekatra shows a screen explaining what it reads and that nothing leaves the phone.
   Tap **Allow notification access**.
2. Android opens the **Notification access** settings page. Switch on **Ekatra Hub**.
3. Android shows a serious-looking warning ("Ekatra will be able to read all notifications...").
   That warning is normal for this kind of app: it is exactly what Ekatra does. Tap **Allow**.
4. Press **Back** to return to Ekatra. You'll now see the timeline, already filled with whatever
   notifications are currently in your notification shade.

### C2. Test it
- Ask someone to send you a WhatsApp/SMS, or send yourself an email.
- The message appears at the top of Ekatra's timeline instantly.
- **Tap** a notification to open it in its app (the exact chat or email). It's marked read.
- **Swipe right** on a notification: about a third of the way (blue, *Mark read*) marks it read,
  swipe again to mark unread. Swipe almost **all the way** (turns red, *Remove*) to remove it
  from Ekatra and the notification shade.
- At the bottom of the notification list is a **swipe bar** with a **›** button. It acts on what's
  showing: the selected app, or everything when 🔔 All is selected. Drag the › right: past a
  third (blue) **marks them all read**, almost all the way (red) **removes them all**. The bar
  fills and tells you what will happen before you let go.
- In Peek, tap the **Ekatra logo** (top left) to open the full app. If the app
  offers a "Mark as read" button (WhatsApp, Telegram, Gmail...), Ekatra presses it for you, so
  it's read in the app too. Unread ones are **bold** with a dot.
- Read a chat in the app itself? Ekatra notices and marks it read here too.
  Read ones turn **grey** (faded text, black-and-white app icon).
- Tap **⌄** on a notification (or long-press it) to expand it: the **full message**, plus a
  **Reply** box when the app supports replies. Type and tap ➤: the app (WhatsApp,
  Telegram, SMS...) sends the reply, exactly like replying from the notification shade. Reply
  shows only for notifications that offer it, and only until the phone restarts.
- The **icon rail** on the left filters by app: the bell shows everything, each app icon shows
  only that app. The small number is how many are **unread**.
- Tap **🔍** at the top to search. Closing search clears it.
- The **bin icon** at the top clears Ekatra's history (the selected app, or everything).
  It never touches your actual messages in the apps.

### C2b. Hub-only mode (optional): notifications only in Ekatra
In **⚙ Settings > Hub-only mode**, Ekatra saves each new notification and then removes it from
the phone's notification shade, status bar and lock screen. Calls, alarms, timers, navigation,
music players and system alerts are never removed. Sounds and vibration still play.

To also stop the banner that **pops down from the top** (it can flash briefly before Ekatra
removes it), turn off pop-ups on the phone:
- **Samsung:** **Settings > Notifications > App notifications > [app, e.g. WhatsApp]**, then under
  **Notification types** untick **Pop-up** (keep it ticked for your phone/dialer app).
- **Other phones:** **Settings > Apps > [app] > Notifications**, open each category and turn off
  **Pop on screen**.

### C3. Turn on Peek (open Ekatra from any app)
Peek is Ekatra's take on the BB10 Hub's "Peek & Flow". A slim pink handle sits on the edge of the
screen over every app.

1. In Ekatra, tap **Set up** on the "Open Ekatra from any app" card (or the ⚙ icon at the top).
2. Switch on **Peek handle**. A card explains the extra permission; tap **Allow**.
3. Android opens **Display over other apps** (Samsung: "Appear on top"). Find **Ekatra** in the
   list, switch it on, then press **Back** twice to return to Ekatra.
4. The handle appears on the left edge. Choose **Left/Right**, then drag the sliders to move it
   up or down and change its length. The handle moves live as you slide.
5. Samsung/Xiaomi/OnePlus: tap **Open battery settings > Battery > Unrestricted** so the handle
   isn't stopped in the background.

How to use it, from any app or the home screen:
- **Peek:** put your thumb on the handle and slide inward slowly. The Hub follows your finger.
  Let go before halfway and it slides away again; you never left your app.
- **Flow:** keep sliding past halfway (you'll feel a light tick) or flick inward, and the Hub
  opens fully. You can scroll, search and filter.
- **Tap** the handle to open the Hub straight away.
- **Close:** **swipe left** anywhere on the Hub to push it back to the edge (it follows your
  finger), or press Back, tap the dimmed area, tap **✕**, or press Home.
  (With the handle on the right edge, directions are mirrored: swipe right to close, left to mark read.)

> Why a handle and not BB10's swipe-up-and-right? On modern Android, swiping up from the bottom
> is Home and swiping in from the sides is Back. Taking those over would break your phone's
> navigation. BlackBerry's own Android phones used the same edge-handle idea (the Productivity Tab).

---

## Part D: make it butter smooth (release build)

**Important:** the ▶ Run button installs a *debug* build by default. Debug builds of Compose apps
are deliberately unoptimized so you can debug them, and they **will stutter** when scrolling.
This is normal and says nothing about the real app. To feel the real speed, install a *release* build:

1. In Android Studio open **Build > Select Build Variant...** (or the **Build Variants** tab on the left edge).
2. Change **app** from `debug` to `release`.
3. Click **▶ Run** again. It installs over the debug version.
   (If it complains about a different signature, uninstall Ekatra from the phone first.)

What the release build does for smoothness:
- **R8** shrinks and optimizes the code (`isMinifyEnabled = true`).
- **Profile Installer** precompiles the important code so even the first launches are fast.

What the code itself does for smoothness:
- The list loads **50 rows at a time** (Paging) instead of the whole history.
- Each row has a **stable key**, so Compose reuses rows instead of rebuilding them.
- **App icons** load on a background thread and are cached in memory.
- **Search waits 200 ms** after you stop typing before querying the database.
- All database writes happen **off the main thread**.

> Switch back to `debug` when you want to develop and debug. Use `release` to judge smoothness.

---

## Part E: keep it running in the background

Ekatra only captures notifications while Android lets its listener run. Most phones allow this
automatically, but some brands kill background apps aggressively to save battery.
If notifications stop appearing after a while, do this:

- **All phones**: **Settings > Apps > Ekatra > Battery** and choose **Unrestricted**
  (or "Don't optimize").
- **Xiaomi / Redmi / POCO**: **Settings > Apps > Manage apps > Ekatra**: turn on **Autostart**,
  and set **Battery saver** to **No restrictions**.
- **Samsung**: **Settings > Battery > Background usage limits**: make sure Ekatra is **not** in
  "Sleeping apps" or "Deep sleeping apps".
- **OnePlus / Oppo / Realme**: **Settings > Battery > App battery management > Ekatra**: allow
  background activity and auto-launch.

<https://dontkillmyapp.com> has step-by-step instructions for every brand.

---

## Part F: troubleshooting

| Problem | Fix |
|---|---|
| Phone doesn't appear in Android Studio | Try another cable/USB port; re-plug and accept the "Allow USB debugging" pop-up; check USB debugging is still on |
| "Restricted setting" / the Ekatra switch is greyed out | Happens on Android 13+ when you install the APK **file** manually (not via Android Studio). Go to **Settings > Apps > Ekatra**, tap the **⋮** menu top-right > **Allow restricted settings**, then try again |
| Gradle sync fails | Make sure you're online; **File > Invalidate Caches > Restart**; copy the first red error and ask Claude |
| Scrolling stutters | You're on the debug build, see Part D |
| Nothing shows after granting access | Turn the Ekatra switch off and on again in Notification access settings, then reopen Ekatra |
| Some notifications are missing | Ongoing ones (music player, downloads) and group summaries are skipped on purpose |

## Privacy recap
- No internet permission: Ekatra cannot send anything off the phone.
- History is excluded from Google backups and phone-to-phone transfers.
- Uninstalling Ekatra deletes everything.
