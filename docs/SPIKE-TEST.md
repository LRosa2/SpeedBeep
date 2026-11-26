# SpeedBeep spike: phone and car test

Throwaway test app. It checks whether Android starts watching on its own when the car's
Bluetooth connects, with the app closed and the phone locked.

1. Install `SpeedBeep-spike.apk` from the release (see [IN-CAR-TEST.md](IN-CAR-TEST.md)).
   With USB debugging instead: `adb install -r SpeedBeep-spike.apk`.
2. Open **SpeedBeep spike** and tap, in this order:
   - **1** → allow "While using the app"
   - **2** → on the page that opens, choose **"Allow all the time"**
   - **3** → allow notifications
3. With the car's Bluetooth on and connected, tap **4. Link car** and pick the car in
   Android's list.
   - If the car is **not in the list**, note that. It is a key result.
4. **Manual test:** tap *Start watching*. A notification "Spike: … km/h" appears.
   Tap *Stop watching*.
5. **Tile test:**
   - Add the **Spike watch** tile to quick settings.
   - Swipe the app away from recents.
   - Tap the tile: the notification should appear. Tap it again: it should go away.
6. **Car test:**
   - Swipe the app away and lock the phone.
   - Turn the car off and on again, or switch the phone's Bluetooth off and on.
   - Within about 10 s the notification should appear and show the speed while driving.
   - Turn the car off: the notification should go away.
7. Open the app, tap **Share log**, and send the text back.

Optional pre-check before going to the car: use any paired Bluetooth headset in place of
the car for steps 3 and 6. **Only the car result decides.**
