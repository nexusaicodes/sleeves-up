# Security policy

## Reporting a vulnerability

Please report security problems privately, through GitHub's
[private vulnerability reporting](https://github.com/nexusaicodes/sleeves-up/security/advisories/new),
not as a public issue. A report reaches the maintainer alone, and the fix can be discussed and
prepared before anything is public.

Include what you found, the app version (Settings → About) and Android version, and the steps
that reproduce it.

## Supported versions

Only the version currently on Google Play receives fixes. A fix ships as a new Play release.

## Scope

Sleeves Up has no backend and holds no `INTERNET` permission: the record stays on the device and
travels only in Android's own backup. Reports that matter most are ones that break that, or that
let another app or person:

- check in or out without passing the presence check (camera face detection or device unlock);
- read or change the session record, through an exported component, the `FileProvider`, an
  intent, a notification action or the home-screen widget;
- make the app reach the network.

Out of scope: problems that need a rooted or already-compromised device, and the behaviour of
Android, Google Play or the device's camera itself.
