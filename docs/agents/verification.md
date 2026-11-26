# Verification and device testing

How an agent proves a ticket is done. Build and test commands are in [README.md](../../README.md).

## Definition of done

Work up this ladder and stop at the first rung that doesn't apply to the ticket:

1. `.\gradlew.bat :domain:test` is green. Domain logic is tested on the JVM, never on a device.
2. `.\gradlew.bat :app:connectedDebugAndroidTest` is green on the attached device. Needed when storage, migrations or anything in `app/src/androidTest` is touched.
3. `.\gradlew.bat :app:installDebug` puts the fresh debug build on the device, then the owner checks it by hand. Needed for any UI or device-visible change.

Rungs 2 and 3 need a device. If none is attached, finish the JVM work, say the device steps are pending, and hand over. Don't stall or ask whether the phone is plugged in.

## Handing over

End every ticket that touches the app with a **Verify on device** checklist: the exact screens, taps and expected results for the owner to try. Keep it short and specific to the change. Use the debug menu entry "Add sample data (debug)" to seed an empty Collection instead of asking the owner to type cards in.

Commit locally, then wait for the owner's go-ahead before `git push` and `gh issue close`. Post the closing reason on the issue before closing it.

Tickets labeled `ready-for-human` need the owner's hands (for example measuring scan hit-rate). Prepare everything that can be prepared (install, checklist, results template), hand over with a clear "your turn" list, and leave the ticket open until the owner reports results.

## Devices

`adb` is not on PATH. Call `<sdk.dir>\platform-tools\adb.exe`, taking `sdk.dir` from `local.properties` (it is written with escaped backslashes, so unescape them). Read only that key; never print the rest of the file.

1. Run `adb devices` at the start of any device-touching ticket.
2. Pick the device by role from the [Test devices](../../README.md#test-devices) table. Pixel 10 Pro is the primary target for functionality; the moto g play is the low-end check for performance and 32-bit issues. Use whichever is attached.
3. Always pass `-s <serial>` to `adb`. Gradle installs on every attached device.
4. If the screen is off, wake the moto g play with `adb -s <serial> shell input keyevent KEYCODE_WAKEUP` then `adb -s <serial> shell wm dismiss-keyguard`.
5. `connectedDebugAndroidTest` uninstalls the app when it finishes. Finish every device ticket with `:app:installDebug`, then `adb -s <serial> shell am start -n tcg.app/.MainActivity` and confirm the app is running, so the owner is handed a phone with the fresh build.

## Never

- Never stage `local.properties`, and never print or log `catalog.token`.
- Run `git status` before every commit and check nothing unexpected is staged.
