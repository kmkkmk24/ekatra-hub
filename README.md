<div align="center">

# Ekatra Hub

### Every message. One place.

**The unified inbox BlackBerry fans have been missing since BB10, rebuilt for modern Android.**

*Ekatra (एकत्र) is Sanskrit for "together, in one place".*

[**⬇ Download the latest APK**](https://github.com/kmkkmk24/ekatra-hub/releases/latest/download/Ekatra.apk) · [Release notes](https://github.com/kmkkmk24/ekatra-hub/releases/latest) · [Getting started](docs/GETTING_STARTED.md) · 100% on-device · Free

</div>

---

## You remember the Hub.

You picked up your Z10, Q10 or Passport, swiped up and to the right, and **everything was there.**
Email, texts, BBM, WhatsApp, calendar invites, in one calm timeline. You peeked at it mid-video
without leaving the app. You read it, replied to it and cleared it, all in one place. And that
little **red LED** told you, from across the room, that something was waiting.

Then the Hub went away, and Android gave us a notification shade that forgets everything the
moment you swipe it.

**Ekatra Hub brings that workflow back.**

---

## ✨ What it does

### 🗂 The Hub: one timeline for everything
- **Every notification from every app** (WhatsApp, Gmail, Telegram, SMS, Slack, banking, all of them) lands in a single, fast, scrollable timeline.
- **History that doesn't vanish.** Swipe a notification away in Android and it's still safe in Ekatra.
- **The icon rail.** App icons down the left side with **unread counts**, just like the Hub's account list. Tap one to filter.
- **Search** across everything you've ever received: that OTP, that address, that link from last Tuesday.

### 👀 Peek: the Hub over any app
- A slim handle sits on the edge of your screen. **Swipe in and the Hub slides over whatever you're doing**, following your finger.
- **Let go early** and it slides away; you never left your app. **Keep going** and it opens fully.
- Swipe it back to the edge to dismiss. Tap the logo to jump into the full app.
- Choose the edge, its height and its length.

### 💬 Act in place
- **Tap** a notification to open the exact chat or email.
- **⌄ Expand** to read the full message, or the whole conversation, without opening the app.
- **Reply inline.** Type your answer right in the Hub; WhatsApp, Telegram or Messages sends it.
- **Swipe right** to mark read (and Ekatra presses the app's own *Mark as read* for you). Swipe all the way to remove.
- **Bottom swipe bar:** clear an app's entire backlog in one gesture, *mark all read* or *remove all*.
- Read a message in the app itself? Ekatra notices and marks it read in the Hub too.

### 🔴 The LED is back
The feature every BlackBerry user still misses.
- A **sharp, laser-point LED** blinks at the top of your screen while you have unread messages. Put it right next to the camera, where a real LED would sit.
- **Per-app colours:** pick up to **5 apps**, give each its own colour (red, orange, yellow, green, blue, purple, white), and know *who* it is before you even pick up the phone. With several unread, it takes turns showing each colour.
- **On the sleeping screen too:** your coloured dots appear on the Always On Display and blink at the interval you choose.

### 🧘 Hub-only mode (optional)
Let Ekatra be the **one place** your notifications live. Once saved, they're cleared from Android's
notification shade, so no more clutter. Calls, alarms, timers, navigation and music are never touched.

### ⚡ Fast and smooth
Built with Jetpack Compose and tuned for 120 Hz: paged loading, GPU-only animations, background
icon loading and an optimized release build. The APK is about **1.3 MB**.

---

## 🔒 Privacy is the whole point

A notification hub sees your most private messages, so Ekatra was designed so it **can't** leak them.

- **No internet permission. At all.** Ekatra physically cannot send anything off your phone.
- **No account, no ads, no analytics, no tracking.**
- Your history lives in Ekatra's private storage, **excluded from cloud backups and phone-to-phone transfers**.
- **Uninstall = everything is erased.**
- Every sensitive permission comes with a plain-language explanation first, and you can switch it off anytime.

---

## BB10 Hub → Ekatra Hub

| On your BlackBerry 10 | In Ekatra Hub |
|---|---|
| The Hub: all accounts in one list | ✅ Unified timeline + icon rail with unread counts |
| Peek & Flow | ✅ Peek: swipe in from the edge handle, let go or flow in |
| Act in place: read, reply, delete | ✅ Expand, inline reply, swipe to read or remove |
| The red notification LED | ✅ On-screen LED with per-app colours, plus the Always On Display |
| Filter by account | ✅ Tap an app in the rail |
| Priority Hub | 🔜 Priority senders (coming) |

> Why a handle instead of BB10's swipe-up-and-right? On modern Android, swiping up is *Home* and
> swiping in from the sides is *Back*. Taking those over would break your phone's navigation.
> BlackBerry's own Android phones used the same edge-handle idea.

---

## 📲 Install

1. On your Android phone (**Android 8.0+**), tap **[⬇ Download Ekatra.apk](https://github.com/kmkkmk24/ekatra-hub/releases/latest/download/Ekatra.apk)**. The download starts straight away.
2. Open it and allow your browser to **install unknown apps** if asked. If Play Protect warns about an unknown developer, choose **More details › Install anyway**.
3. Open Ekatra and tap **Allow notification access**.
   *Android 13+:* if the switch is greyed out ("Restricted setting"), go to **Settings › Apps › Ekatra › ⋮ › Allow restricted settings**.
4. In **⚙ Settings**, switch on **Peek** and the **Notification LED**.

**Samsung tip:** set **Settings › Apps › Ekatra › Battery › Unrestricted** so the Hub keeps running in the background.

---

## 🗺 Roadmap

- **Priority senders:** a Priority Hub for the people who matter
- **Privacy mode:** auto-mask OTPs and bank alerts
- **"Who else can read your notifications?"**, a screen that lists every app with notification access
- Google Play Store release

Ideas and bug reports are welcome in **[Issues](https://github.com/kmkkmk24/ekatra-hub/issues)**.

---

## 🛠 Build from source

Kotlin · Jetpack Compose · Room · Coroutines/Flow · `NotificationListenerService`

Open the project in Android Studio and press ▶. New to Android development? **[docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)** walks you through the PC and phone setup from zero.

---

<sub>Ekatra Hub is an independent project. It is not affiliated with, endorsed by, or connected to BlackBerry Limited. BlackBerry, BB10 and related names are trademarks of their respective owners and are used here only to describe what inspired this app.</sub>
