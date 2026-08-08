#!/usr/bin/env bash
# Loads Sonatype tokens from .gradle/gradle.properties into the environment so
# Gradle's providers.gradleProperty("mavenCentralUsername") can see them.
set -euo pipefail
cd "$(dirname "$0")/.."

PROPS_FILE=".gradle/gradle.properties"
if [[ ! -f "$PROPS_FILE" ]]; then
  echo "Missing $PROPS_FILE — copy gradle.properties.example and fill in credentials." >&2
  exit 1
fi

read_prop() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "$PROPS_FILE" | tail -1 || true)"
  if [[ -z "$line" ]]; then
    echo "Missing $key in $PROPS_FILE" >&2
    exit 1
  fi
  echo "${line#*=}"
}

export ORG_GRADLE_PROJECT_mavenCentralUsername="$(read_prop mavenCentralUsername)"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$(read_prop mavenCentralPassword)"

# gpg via useGpgCmd() honors signing.gnupg.keyName only; default gpg key is used if this is unset.
signing_key_id="$(read_prop signing.keyId)"
gnupg_key_name="$(grep -E '^signing\.gnupg\.keyName=' "$PROPS_FILE" | tail -1 | cut -d= -f2- || true)"
if [[ -z "$gnupg_key_name" ]]; then
  gnupg_key_name="$signing_key_id"
fi
export ORG_GRADLE_PROJECT_signing_keyId="$signing_key_id"
export ORG_GRADLE_PROJECT_signing_gnupg_keyName="$gnupg_key_name"

exec ./gradlew publishToMavenCentral "$@"
