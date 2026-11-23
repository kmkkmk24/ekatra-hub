# Ekatra Hub — Project Context

> Context exported from a planning chat (Nov 2026). Drop this file in the repo root as `CLAUDE.md` so Claude Code picks it up automatically.

## About the developer
- Ekatra Hub is the first app to build seriously and publish on the **Google Play Store**.
- Prefers learning-oriented explanations alongside code (explain *why*, especially for security-relevant APIs).

## The app
**Name:** Ekatra Hub (Sanskrit *ekatra* / एकत्र = "in one place, together")
**One-liner:** A BB10 Hub-inspired unified inbox for Android — every notification in one place, reply from one timeline, swipe up to peek. 100% on-device.
**Taglines:** "Every message. One place." / "One swipe. Everything."

### Naming / branding rules (important)
- Do **NOT** use "BlackBerry", "BlackBerry Hub", "Hub+", BlackBerry logos, colours or icon style anywhere in the app name, icon, or store title (trademark + Play impersonation policy).
- OK to say "Inspired by the classic BB10 Hub" in descriptions/README.
- Play Store title: `Ekatra Hub – All Notifications in One Place`
- Launcher label: `Ekatra`
- Package name: `com.<yourname>.ekatrahub` (permanent once published — confirm before first release)
- Repo name: `ekatra-hub`
- Logo idea: lotus, or a single thread gathering app-dots into a circle.

### Inspiration: what BlackBerry Hub did (BB10, 2013)
- Unified list: email (multiple accounts), SMS, BBM/WhatsApp, social, calls, voicemail, calendar invites.
- **Peek & Flow:** swipe up-and-right from any app to peek into the Hub without leaving the app.
- Act in place: reply, archive, delete without opening the source app.
- Filters by account; "Priority Hub" for important senders.
- BlackBerry's Android port (Hub+) is abandoned — no true replacement exists.

### Competitive landscape (gap = our opportunity)
- **Beeper** — unified chat, not notifications/email.
- **NotiPeek, Keeps: Notification Inbox, NotifyFlow, Pinnit, Notification History Log** — notification history/log apps. Keeps is closest (ticket-style reply/done/snooze).
- Android built-in Notification History — hidden, basic.
- **Nobody combines:** single timeline + in-place reply + Peek gesture + priority senders. That's our differentiation.

## Feature plan

### MVP
1. `NotificationListenerService` capturing every notification (app, sender/title, text, timestamp, actions).
2. Persist to **Room** DB — history survives dismissal.
3. Unified timeline UI (Jetpack Compose), filter by app, search.
4. Prominent **in-app disclosure screen** before sending the user to Notification Access settings (Play policy requirement).

### V1 differentiators
5. **In-place reply** using notification `RemoteInput` reply actions (WhatsApp, Telegram, SMS, etc.).
6. **Peek** — swipe-up floating overlay from any app.
7. **Priority senders** view.
8. **Privacy mode** — detect & mask OTPs / bank alerts.
9. **"Who else can read your notifications?"** screen — list other apps holding notification-listener access (security-learning tie-in: banking trojans abuse this permission).

## Tech stack
- Kotlin, Jetpack Compose, Coroutines/Flow
- Room (local only), WorkManager if needed
- `NotificationListenerService`, `RemoteInput`, overlay (`SYSTEM_ALERT_WINDOW`) for Peek
- **No network permission** ideally — no backend, no analytics. This is the core privacy claim.

## Non-negotiable principles
- All data stays on device. No uploads, no account, no ads that look like system notifications.
- Request permissions in context, with clear disclosure.
- Easy "clear history" (per app / all); uninstall wipes everything.
- Never imitate system UI/warnings (Play "Unauthorized imitation of system functionality" policy).

## Google Play publishing notes
- Until polished: distribute APK via GitHub Releases.

## Suggested first task for Claude Code
Set up the Android Studio project (Kotlin + Compose, minSdk ~26), implement the `NotificationListenerService`, store notifications in Room, and show them in a simple timeline list — with the disclosure screen gating the permission request.
