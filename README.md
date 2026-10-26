# HideMe

HideMe closes the official YouTube app during quiet hours you choose. It is developed by PrimeByte Pvt Ltd and distributed as a sideload, outside the Play Store.

While HideMe is on, opening YouTube during quiet hours sends you straight to the home screen. Nothing else is shown. The YouTube icon stays in the launcher.

## Quiet hours

Each day of the week has its own switch, hide time, and allow time.

The first install uses weekdays from 10:30 PM until 6:00 AM the next morning. Saturday and Sunday are off. Change any day from the main screen.

If the hide time is later than the allow time, that block runs through midnight and ends the next morning. A night belongs to the evening it starts on: Friday can stay hidden until Saturday morning, while Sunday evening stays open, including early Monday, unless you turn Sunday on.

## First launch

1. Install the app and open HideMe.
2. Choose a 4-digit PIN, then enter it again.
3. Tap **Turn HideMe on**.
4. On the system screen, allow the HideMe accessibility service.

Android shows this description before the service starts: HideMe, by PrimeByte Pvt Ltd, notices when YouTube opens and leaves it during the hours you choose. It does not read what is on screen.

HideMe only watches `com.google.android.youtube`.

## Pause for a banking app

Some finance apps refuse to open while an accessibility service is on. Pausing inside the schedule is not enough. HideMe has to turn its accessibility service off.

1. Open HideMe.
2. Enter your 4-digit PIN.
3. Tap **Pause HideMe**.
4. Open the banking app.
5. When you are done, open HideMe, enter the PIN, and tap **Turn HideMe on**.

There is no reset inside the app. Clearing HideMe’s storage, or reinstalling it, removes the PIN. Five wrong entries make the pad wait 30 seconds.

YouTube stays available for the whole time HideMe is paused, including during quiet hours.

On this project’s own phone, **Turn HideMe on** switches the service back on from inside the app. Android does not allow that for a normal install. After sideloading onto another phone, **Pause HideMe** still turns the service off in one tap. **Turn HideMe on** opens the HideMe switch in system settings, and you turn it on there.

To make **Turn HideMe on** work inside the app on a phone you own, connect it with USB debugging and run this once after install:

```bash
adb shell pm grant com.tonystarklabs.hideme android.permission.WRITE_SECURE_SETTINGS
```

That grant applies only to HideMe’s own accessibility switch. It does not remove other accessibility services.

## Build

Open this folder in Android Studio, or from a terminal:

```bash
./gradlew :app:assembleDebug
```

The sideload package is `app/build/outputs/apk/debug/app-debug.apk`.

With a phone connected:

```bash
./gradlew :app:installDebug
```

The app needs Android 8.0 or newer. The app code is Java.

## License

Copyright (C) 2026 PrimeByte Pvt Ltd

HideMe is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).
