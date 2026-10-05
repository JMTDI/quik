#!/usr/bin/env bash
# Apply every patch in patches/ onto an upstream QUIK checkout.
# Usage: ./apply.sh [target-dir]   (default: ./upstream)
# Env:   UPSTREAM_REF  branch/tag to build from (default: master)
#        APP_ID        optional applicationId override (e.g. com.jmtditech.quik)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TARGET="${1:-$HERE/upstream}"
REF="${UPSTREAM_REF:-master}"

if [[ ! -d "$TARGET/.git" ]]; then
  git clone https://github.com/quik-sms/quik.git "$TARGET"
fi
cd "$TARGET"
git fetch --tags origin
git checkout -q -f "$REF"
[[ "$REF" == "master" ]] && git reset -q --hard origin/master
git clean -fdq

git config user.name  "quik-dpad-patch"
git config user.email "noreply@users.noreply.github.com"

for p in "$HERE"/patches/*.patch; do
  echo ">> applying $(basename "$p")"
  if ! git am --3way --quiet "$p"; then
    echo "::error::$(basename "$p") no longer applies cleanly to $REF"
    git am --abort || true
    exit 1
  fi
done

if [[ -n "${APP_ID:-}" ]]; then
  echo ">> overriding applicationId -> $APP_ID"
  sed -i "s/applicationId '[^']*'/applicationId '$APP_ID'/" presentation/build.gradle
  sed -i "s/android:targetPackage=\"[^\"]*\"/android:targetPackage=\"$APP_ID\"/; s/android:action=\"[^\"]*\.START\"/android:action=\"$APP_ID.START\"/" \
      presentation/src/main/res/xml/shortcuts.xml
fi
echo ">> patched $(git rev-parse --short HEAD) ready in $TARGET"
