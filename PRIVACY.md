# Privacy policy

**Freedoom Live Wallpaper collects nothing.**

That is the whole policy, and it is a property of the build rather than a promise. What
follows is how you can check it.

## No data leaves the device

The application requests **no permission that grants access to anything** — not internet,
not storage, not location, not the camera, not identifiers. Android would refuse a network
connection it never asked for, so there is no analytics, no crash reporting service, no
advertising identifier and no telemetry: there is no channel any of those could use.

The manifest in this repository declares no permissions whatsoever. The built package
contains exactly one entry, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which is not a system
permission and is not a request for anything: it is defined by the AndroidX libraries inside
this application's own namespace, and its only effect is that a broadcast receiver registered
at runtime is not left open to other applications. It is a lock, not a key.

That distinction is written down because it is the sort of thing a policy should not gloss
over: the claim here is checkable with `aapt2 dump permissions` on the APK, and this is what
that command reports.

There is no account, no sign-in and no server. Nothing is uploaded, and nothing is
downloaded after installation.

## What stays on the device

Two kinds of file, both written by you and both inside the application's private storage,
where no other application can read them:

- **Your settings** — frame rate, which background, god mode, and how many times the wave
  table has been finished. A handful of values in the application's own preferences.
- **Files you import** — a WAD or a photograph you choose yourself, through the system
  picker. They are copied in because the wallpaper reads them constantly and a document
  permission can be revoked between one launch and the next. The originals are untouched
  where they live.

Both are removed when you uninstall the application, and *Reset everything* in the settings
deletes them immediately.

## Children

Nothing here is directed at children, and since nothing is collected, nothing about a child
could be collected either.

## Changes

This file lives in the source repository alongside the code it describes, so any change to
it is a commit with a date and a diff.

## Contact

Open an issue on the project's repository.
