#!/usr/bin/env bash
set -euo pipefail

# Create/use a medium-phone AVD, start it with lighter settings, build/install
# the debug app, launch IrisApp, then stream Logcat.
#
# Usage:
#   scripts/run_medium_emulator_app.sh
#
# Useful overrides:
#   AVD_NAME=Gemma_Medium_API_35 MEMORY_MB=2048 scripts/run_medium_emulator_app.sh
#   LOG_ALL=1 scripts/run_medium_emulator_app.sh
#   LOG_FILTER="AndroidRuntime|FATAL EXCEPTION|OutOfMemory" scripts/run_medium_emulator_app.sh

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
AVDMANAGER="$(
    for candidate in \
        "$SDK/cmdline-tools/latest/bin/avdmanager" \
        "$SDK"/cmdline-tools/*/bin/avdmanager \
        "$SDK/tools/bin/avdmanager"
    do
        if [ -x "$candidate" ]; then
            echo "$candidate"
            break
        fi
    done
)"
SDKMANAGER="$(
    for candidate in \
        "$SDK/cmdline-tools/latest/bin/sdkmanager" \
        "$SDK"/cmdline-tools/*/bin/sdkmanager \
        "$SDK/tools/bin/sdkmanager"
    do
        if [ -x "$candidate" ]; then
            echo "$candidate"
            break
        fi
    done
)"

AVD_NAME="${AVD_NAME:-Gemma_Medium_API_35}"
API_LEVEL="${API_LEVEL:-35}"
DEVICE_ID="${DEVICE_ID:-medium_phone}"
MEMORY_MB="${MEMORY_MB:-4096}"
GPU_MODE="${GPU_MODE:-host}"
APP_ID="${APP_ID:-com.irisapp}"
DEFAULT_LOG_FILTER="WorkflowGeneration|WorkflowRunner|InferenceManager|ToolInitializer|ToolRegistry|ToolAwareGenerator|Tool call|Tool result|TOOL|get_contact|lookup_contact|Available tools|Installed app list sent to AI|Full capabilities sent to AI|AI output|IntentDiscovery"
LOG_FILTER="${LOG_FILTER:-$DEFAULT_LOG_FILTER}"
LOG_ALL="${LOG_ALL:-0}"
EMULATOR_LOG="${EMULATOR_LOG:-/tmp/iris-emulator.log}"
LOCAL_MODEL_PATH="${LOCAL_MODEL_PATH:-local-models/gemma-4-E2B-it.litertlm}"
DEVICE_MODEL_DIR="${DEVICE_MODEL_DIR:-/sdcard/Android/data/$APP_ID/files/models}"
DEVICE_MODEL_NAME="${DEVICE_MODEL_NAME:-gemma-4-E2B-it.litertlm}"
DEVICE_MODEL_PATH="$DEVICE_MODEL_DIR/$DEVICE_MODEL_NAME"
SEED_CONTACTS="${SEED_CONTACTS:-1}"
DEMO_CONTACTS=(
    "Maya Chen|+15550101001|maya.chen@example.com"
    "Omar Haddad|+15550101002|omar.haddad@example.com"
    "Lina Patel|+15550101003|lina.patel@example.com"
)

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

if [ ! -x "$EMULATOR" ]; then
    echo "emulator not found at $EMULATOR" >&2
    exit 1
fi

if [ ! -x "$ADB" ]; then
    echo "adb not found at $ADB" >&2
    exit 1
fi

if [ "$(uname -m)" = "arm64" ]; then
    ABI="${ABI:-arm64-v8a}"
else
    ABI="${ABI:-x86_64}"
fi

seed_contact() {
    local name="$1"
    local phone="$2"
    local email="$3"
    local raw_id

    if "$ADB" -e shell "content query --uri content://com.android.contacts/contacts --projection display_name --where \"display_name='$name'\" 2>/dev/null | grep -q \"$name\"" >/dev/null 2>&1; then
        echo "Contact already seeded: $name"
        return 0
    fi

    "$ADB" -e shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s: --bind account_name:s:" >/dev/null 2>&1 || return 1
    raw_id="$("$ADB" -e shell "content query --uri content://com.android.contacts/raw_contacts --projection _id 2>/dev/null | sed -n 's/.*_id=\\([0-9][0-9]*\\).*/\\1/p' | tail -n 1" | tr -d '\r')"

    if [ -z "$raw_id" ]; then
        return 1
    fi

    "$ADB" -e shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw_id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:\"$name\"" >/dev/null 2>&1 || return 1
    "$ADB" -e shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw_id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:\"$phone\" --bind data2:i:2" >/dev/null 2>&1 || return 1
    "$ADB" -e shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw_id --bind mimetype:s:vnd.android.cursor.item/email_v2 --bind data1:s:\"$email\" --bind data2:i:1" >/dev/null 2>&1 || return 1

    echo "Seeded contact: $name"
}

seed_demo_contacts() {
    if [ "$SEED_CONTACTS" != "1" ]; then
        echo "Skipping demo contact seeding because SEED_CONTACTS=$SEED_CONTACTS"
        return 0
    fi

    echo "Seeding demo contacts into emulator contact book..."
    for contact in "${DEMO_CONTACTS[@]}"; do
        IFS='|' read -r name phone email <<< "$contact"
        if ! seed_contact "$name" "$phone" "$email"; then
            echo "Warning: could not seed contact '$name'. The Contacts provider may be unavailable on this image." >&2
        fi
    done
}

wait_for_shared_storage() {
    echo "Waiting for emulator shared storage..."

    for _ in $(seq 1 60); do
        if "$ADB" -e shell "test -d /sdcard" >/dev/null 2>&1; then
            return 0
        fi

        if "$ADB" -e shell "test -d /storage/emulated/0" >/dev/null 2>&1; then
            if [[ "$DEVICE_MODEL_DIR" == /sdcard/* ]]; then
                DEVICE_MODEL_DIR="/storage/emulated/0/${DEVICE_MODEL_DIR#/sdcard/}"
                DEVICE_MODEL_PATH="$DEVICE_MODEL_DIR/$DEVICE_MODEL_NAME"
                echo "Using shared storage fallback: $DEVICE_MODEL_DIR"
            fi
            return 0
        fi

        # Some images report boot_completed before credential-encrypted storage
        # and the /sdcard symlink are fully usable. Unlock again and keep waiting.
        "$ADB" -e shell input keyevent 82 >/dev/null 2>&1 || true
        sleep 1
    done

    echo "Shared storage is not available on this emulator." >&2
    echo "Debug info:" >&2
    "$ADB" -e shell "ls -ld /sdcard /storage /storage/emulated /storage/emulated/0 2>&1 || true" >&2 || true
    "$ADB" -e shell "sm list-volumes all 2>&1 || true" >&2 || true
    echo "" >&2
    echo "This usually means the currently running emulator is still mounting storage," >&2
    echo "or it is not a normal phone/tablet image with shared external storage." >&2
    echo "Close other emulators or wipe/recreate the AVD, then rerun this script." >&2
    return 1
}

ensure_device_model_dir() {
    wait_for_shared_storage

    if ! "$ADB" -e shell "mkdir -p '$DEVICE_MODEL_DIR' && test -d '$DEVICE_MODEL_DIR'" >/dev/null 2>&1; then
        echo "Could not create model directory: $DEVICE_MODEL_DIR" >&2
        echo "Storage debug info:" >&2
        "$ADB" -e shell "ls -ld /sdcard /sdcard/Android /sdcard/Android/data 2>&1 || true" >&2 || true
        "$ADB" -e shell "df -h /sdcard 2>&1 || true" >&2 || true
        return 1
    fi
}

if [ -z "${SYSTEM_IMAGE:-}" ]; then
    SYSTEM_IMAGE="system-images;android-${API_LEVEL};google_apis;${ABI}"
    if [ -n "$AVDMANAGER" ]; then
        INSTALLED_IMAGE="$("$AVDMANAGER" list target 2>/dev/null \
            | sed -n 's/^.*Tag\/ABI[[:space:]]*:[[:space:]]*\\([^/]*\\)\\/\\([^[:space:]]*\\).*$/\\1\\/\\2/p' \
            | awk -F/ -v abi="$ABI" '$2 == abi { print $1 "/" $2; exit }' || true)"
        if [ -n "$INSTALLED_IMAGE" ]; then
            TAG="${INSTALLED_IMAGE%%/*}"
            IMAGE_ABI="${INSTALLED_IMAGE##*/}"
            TARGET_ID="$("$AVDMANAGER" list target 2>/dev/null \
                | awk '/^id: / { id=$2 } /Tag\\/ABI[[:space:]]*:[[:space:]]*'"$TAG"'\\/'"$IMAGE_ABI"'/ { print id; exit }' \
                | tr -d '\"' || true)"
            if [ -n "$TARGET_ID" ]; then
                SYSTEM_IMAGE="system-images;android-${TARGET_ID};${TAG};${IMAGE_ABI}"
            fi
        fi
    fi
fi

echo "Using SDK: $SDK"
echo "Using AVD: $AVD_NAME"
echo "Using image: $SYSTEM_IMAGE"
echo "Using memory: ${MEMORY_MB}MB"

AVD_LIST="$("$EMULATOR" -list-avds 2>/dev/null || true)"

if ! printf "%s\n" "$AVD_LIST" | grep -Fxq "$AVD_NAME"; then
    if [ -n "$AVDMANAGER" ]; then
        if ! "$AVDMANAGER" list device -c | grep -qx "$DEVICE_ID"; then
            echo "Device id '$DEVICE_ID' not found; falling back to pixel_5."
            DEVICE_ID="pixel_5"
        fi

        echo "Creating AVD '$AVD_NAME' as device '$DEVICE_ID'..."
        if ! printf "no\n" | "$AVDMANAGER" create avd \
            -n "$AVD_NAME" \
            -k "$SYSTEM_IMAGE" \
            -d "$DEVICE_ID" \
            --force; then
            echo "" >&2
            echo "Failed to create AVD. The system image may not be installed." >&2
            echo "Install it with Android Studio SDK Manager, or with:" >&2
            if [ -n "$SDKMANAGER" ]; then
                echo "  $SDKMANAGER \"$SYSTEM_IMAGE\"" >&2
            else
                echo "  Install Android SDK Command-line Tools, then run sdkmanager \"$SYSTEM_IMAGE\"" >&2
            fi
            exit 1
        fi
    else
        FALLBACK_AVD="$(printf "%s\n" "$AVD_LIST" | sed '/^$/d' | head -n 1)"
        if [ -n "$FALLBACK_AVD" ]; then
            echo "avdmanager not found, so I cannot create '$AVD_NAME'."
            echo "Reusing existing AVD '$FALLBACK_AVD'."
            echo "To create the medium AVD later, install Android SDK Command-line Tools from Android Studio > SDK Manager."
            AVD_NAME="$FALLBACK_AVD"
        else
            echo "No AVD named '$AVD_NAME' exists, and avdmanager was not found." >&2
            echo "Install Android SDK Command-line Tools from Android Studio > SDK Manager." >&2
            exit 1
        fi
    fi
fi

if "$ADB" devices | grep -qE '^emulator-[0-9]+[[:space:]]+device$'; then
    echo "An emulator is already running; reusing it."
else
    echo "Starting emulator. Full emulator log: $EMULATOR_LOG"
    nohup "$EMULATOR" @"$AVD_NAME" \
        -gpu "$GPU_MODE" \
        -memory "$MEMORY_MB" \
        -no-boot-anim \
        -no-audio \
        -netfast \
        >"$EMULATOR_LOG" 2>&1 &
fi

echo "Waiting for emulator boot..."
"$ADB" -e wait-for-device
until [ "$("$ADB" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
done

"$ADB" -e shell input keyevent 82 >/dev/null 2>&1 || true
wait_for_shared_storage

echo "Building and installing debug app..."
./gradlew :app:installDebug

echo "Granting READ_CONTACTS for emulator smoke tests..."
if ! "$ADB" -e shell pm grant "$APP_ID" android.permission.READ_CONTACTS >/dev/null 2>&1; then
    echo "Warning: could not grant READ_CONTACTS. Contact tools will report permission errors until permission is granted." >&2
fi

seed_demo_contacts

# ══════════════════════════════════════════════════════════
# Pre-flight setup — permissions, test data, app installs
# ══════════════════════════════════════════════════════════
echo ""
echo "Setting up emulator environment for all actions and tools..."

# ── Grant all runtime permissions ──
PERMISSIONS=(
    "android.permission.READ_CONTACTS"
    "android.permission.WRITE_CONTACTS"
    "android.permission.READ_CALENDAR"
    "android.permission.WRITE_CALENDAR"
    "android.permission.READ_SMS"
    "android.permission.SEND_SMS"
    "android.permission.POST_NOTIFICATIONS"
    "android.permission.READ_MEDIA_AUDIO"
    "android.permission.READ_EXTERNAL_STORAGE"
    "android.permission.WRITE_EXTERNAL_STORAGE"
)
for perm in "${PERMISSIONS[@]}"; do
    if "$ADB" -e shell pm grant "$APP_ID" "$perm" >/dev/null 2>&1; then
        echo "  Granted: ${perm##*.}"
    else
        echo "  Skipped (unavailable): ${perm##*.}"
    fi
done

# ── Seed demo calendar events (for get_calendar_events / calendar.create_event tests) ──
echo ""
echo "Seeding demo calendar events..."
SEED_CALENDAR="${SEED_CALENDAR:-1}"
if [ "$SEED_CALENDAR" = "1" ]; then
    NOW_MS=$(date +%s)000
    TOMORROW_MS=$(date -v+1d +%s)000 2>/dev/null || TOMORROW_MS=$((NOW_MS + 86400000))
    NEXT_WEEK_MS=$((NOW_MS + 7 * 86400000))

    for event in \
        "Dentist appointment|2026-05-15T14:00:00|2026-05-15T15:00:00|Dr. Smith Office|Regular checkup" \
        "Team demo|2026-05-12T10:00:00|2026-05-12T11:00:00|Conference Room A|Show hackathon progress" \
        "Lunch with Maya|2026-05-16T12:30:00|2026-05-16T13:30:00|Cafe Nero||"
    do
        IFS='|' read -r title start end location desc <<< "$event"
        "$ADB" -e shell "content insert --uri content://com.android.calendar/events \
            --bind title:s:'$title' \
            --bind dtstart:i:$(( $(date -jf "%Y-%m-%dT%H:%M:%S" "$start" +%s 2>/dev/null || echo 1778000000) * 1000 )) \
            --bind dtend:i:$(( $(date -jf "%Y-%m-%dT%H:%M:%S" "$end" +%s 2>/dev/null || echo 1778003600) * 1000 )) \
            --bind eventLocation:s:'$location' \
            --bind description:s:'$desc' \
            --bind calendar_id:i:1" >/dev/null 2>&1 && echo "  Seeded event: $title" || echo "  Skipped event: $title (Calendar provider unavailable)"
    done
else
    echo "  Skipping calendar seeding (SEED_CALENDAR=$SEED_CALENDAR)"
fi

# ── Seed demo SMS messages (for search_sms tests) ──
echo ""
echo "Seeding demo SMS messages..."
SEED_SMS="${SEED_SMS:-1}"
if [ "$SEED_SMS" = "1" ]; then
    for sms in \
        "15555215554|Hey, are we still on for the meeting tomorrow?|$((NOW_MS - 3600000))" \
        "15555215555|Don't forget to bring the documents!|$((NOW_MS - 7200000))" \
        "+15550101001|Hi! Just confirming our lunch on Friday.|$((NOW_MS - 86400000))"
    do
        IFS='|' read -r address body date <<< "$sms"
        "$ADB" -e shell "content insert --uri content://sms/inbox \
            --bind address:s:'$address' \
            --bind body:s:'$body' \
            --bind date:i:$date \
            --bind read:i:0" >/dev/null 2>&1 && echo "  Seeded SMS: $address" || echo "  Skipped SMS: $address (SMS provider unavailable)"
    done
else
    echo "  Skipping SMS seeding (SEED_SMS=$SEED_SMS)"
fi

# ── Install app APKs for app-specific actions ──
echo ""
echo "Checking app-specific action dependencies..."
INSTALL_APPS="${INSTALL_APPS:-1}"
APK_DIR="${APK_DIR:-local-models/apks}"

if [ "$INSTALL_APPS" = "1" ]; then
    # WhatsApp — needed for whatsapp.send_text
    if "$ADB" -e shell pm list packages com.whatsapp | grep -q whatsapp; then
        echo "  WhatsApp already installed"
    else
        if [ -f "$APK_DIR/whatsapp.apk" ]; then
            echo "  Installing WhatsApp..."
            "$ADB" -e install "$APK_DIR/whatsapp.apk" >/dev/null 2>&1 && echo "  WhatsApp installed" || echo "  WhatsApp install failed (APK may be incompatible)"
        else
            echo "  WhatsApp APK not found at $APK_DIR/whatsapp.apk — skip"
        fi
    fi

    # Spotify — needed for spotify.search_and_play
    if "$ADB" -e shell pm list packages com.spotify.music | grep -q spotify; then
        echo "  Spotify already installed"
    else
        if [ -f "$APK_DIR/spotify.apk" ]; then
            echo "  Installing Spotify..."
            "$ADB" -e install "$APK_DIR/spotify.apk" >/dev/null 2>&1 && echo "  Spotify installed" || echo "  Spotify install failed (APK may be incompatible)"
        else
            echo "  Spotify APK not found at $APK_DIR/spotify.apk — skip"
        fi
    fi

    echo "  (Place APKs in $APK_DIR/ to auto-install)"
else
    echo "  Skipping app installs (INSTALL_APPS=$INSTALL_APPS)"
fi

echo ""
echo "Pre-flight setup complete."
echo ""

# Append RETO pipeline tags to log filter
LOG_FILTER="${LOG_FILTER}|TaskDecomposer|CapabilityBinder|SlotGroundingPlanner|RequirementBuilder|ResolverRegistry|RetoOrchestrator|RetoLayerExecutor|RetoLayerPlanner|RetoWorkflowPlanner|CoverageValidator|WorkflowValidator|WorkflowJsonParser"

if [ -f "$LOCAL_MODEL_PATH" ]; then
    LOCAL_MODEL_SIZE="$(wc -c < "$LOCAL_MODEL_PATH" | tr -d ' ')"
    DEVICE_MODEL_SIZE="$("$ADB" -e shell "stat -c %s '$DEVICE_MODEL_PATH' 2>/dev/null || echo missing" | tr -d '\r')"

    if [ "$DEVICE_MODEL_SIZE" = "$LOCAL_MODEL_SIZE" ]; then
        echo "Model already present on emulator: $DEVICE_MODEL_PATH ($DEVICE_MODEL_SIZE bytes)"
    else
        echo "Pushing model to emulator..."
        echo "  local:  $LOCAL_MODEL_PATH ($LOCAL_MODEL_SIZE bytes)"
        echo "  device: $DEVICE_MODEL_PATH"
        ensure_device_model_dir
        "$ADB" -e push "$LOCAL_MODEL_PATH" "$DEVICE_MODEL_PATH"
    fi
else
    echo "Local model not found at $LOCAL_MODEL_PATH; app may show MissingModel." >&2
    echo "Set LOCAL_MODEL_PATH=/path/to/model.litertlm to push a different file." >&2
fi

echo "Launching IrisApp..."
"$ADB" -e shell am start -n "$APP_ID/.ui.MainActivity"

echo ""
echo "Streaming Logcat. Press Ctrl+C to stop logs."
echo ""
if [ "$LOG_ALL" = "1" ] || [ -z "$LOG_FILTER" ]; then
    echo "Showing all logs."
    echo "Equivalent command:"
    echo "  $ADB -e logcat"
    echo ""
    "$ADB" -e logcat
else
    echo "Filtering logs with: $LOG_FILTER"
    echo "Equivalent command:"
    echo "  $ADB -e logcat | grep -Ei \"$LOG_FILTER\""
    echo ""
    "$ADB" -e logcat | grep -Ei "$LOG_FILTER"
fi
