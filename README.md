# quik

Patch set that adds full D-pad / keypad navigation to
[quik-sms/quik](https://github.com/quik-sms/quik), applied automatically on top of upstream.

## What's in the patch
- `patches/0001-dpad-support.patch` — focus selector drawable, `nextFocus*` wiring across layouts,
  D-pad key handling in `QkActivity` / `QkEditText`, drawer + inbox + compose + conversation-info
  navigation, menu-key handling, media parts open on D-pad click.
- Derived from the `JMTDI/quik` fork (40 commits), minus build/release/app-ID/translation changes.

## Use
```sh
./apply.sh                       # clones upstream, applies patches (UPSTREAM_REF=v4.3.7 to pin)
cd upstream && ./gradlew assembleDebug
```
Set `APP_ID=com.jmtditech.quik` to install side by side with stock QUIK.

## CI
`.github/workflows/build.yml` is manual-only (Actions tab → Run workflow). Leave the ref blank for upstream `master`, or enter a branch/tag. It applies the patches, builds, and publishes a `<ref>-dpad-<timestamp>` release.
If a patch stops applying, the run fails; rebase it (`git am --3way`, fix, `git format-patch`).

## Notes
- Release APKs are signed with your keystore from the repo secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
- Upstream moved on after the fork diverged (e.g. removed the QKSMS+ drawer rows); `0001` is already
  rebased onto upstream `02542049`.
