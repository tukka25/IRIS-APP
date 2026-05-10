#!/bin/bash
# Automated RETO Workflow Test Runner v2 — uses clipboard + paste for reliable text entry
# Runs all test cases from workflow_category_tester.md automatically

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_DIR="$SCRIPT_DIR/results/$TIMESTAMP"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
LOGFILE="$RESULTS_DIR/full_logcat.txt"

mkdir -p "$RESULTS_DIR"

# ── UI coordinates ──
INPUT_X=540
INPUT_Y=543
GEN_X=540
GEN_Y=843

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# ── Test cases ──
TEST_CASES=(
  "1|send_message|Text Maya that I will be there in 10 minutes."
  "2|make_call|Call Mom now."
  "3|create_event|Add a calendar event for dentist appointment next Tuesday at 3pm at Dubai Marina Dental."
  "4|set_reminder|Remind me tomorrow morning to submit the hackathon pitch deck."
  "5|set_alarm|Set an alarm for 7:15 tomorrow morning called gym."
  "6|open_app|Open YouTube."
  "7|search|Search the web for Android common intents documentation."
  "8|share|Share the text I will join the meeting in five minutes with another app."
  "9|navigate|Navigate to Dubai Mall."
  "10|play_media|Play my focus playlist on Spotify."
  "11|open_file|Open the budget spreadsheet from my downloads."
  "12|take_note|Create a note called Groceries with milk eggs coffee and bananas."
  "13|check_notification|Check if I missed any WhatsApp notifications from Maya."
  "14|get_info|Find out whether it will rain in Dubai tomorrow morning."
  "15|other|Make my phone more productive for the hackathon."
  "C1|chained_msg_evt|send message to Maya saying hi and invite him to meeting on 6 oclock on next friday and then add it to my calendar."
  "C2|chained_call_msg|Call Mom then text her that I booked dinner for Friday at 8pm."
  "C3|chained_evt_rem|Create a calendar event for team demo next Monday at 10am then remind me 30 minutes before."
  "C4|chained_nav_msg|Navigate to Dubai Mall and share my ETA with Maya."
  "C5|chained_app_media|Open Spotify and play my workout playlist then set a 45 minute timer."
  "C6|chained_file_note|Open my budget spreadsheet and make a note that I need to update May expenses."
  "C7|chained_srch_nav|Search for the nearest pharmacy open now and navigate there."
  "C8|chained_rem_app|Remind me tomorrow at 9am to call the dentist then open the phone app."
  "E1|edge_ambiguous|Schedule coffee with Sara at 6 next Friday."
  "E2|edge_missingct|Text my dentist that I am running late."
  "E3|edge_missingapp|Play my focus playlist on Spotify."
  "E4|edge_unsupported|When I receive a WhatsApp from Maya summarize it."
  "E5|edge_vague|Help me study better every day."
)

echo ""
echo -e "${CYAN}══════════════════════════════════════════${NC}"
echo -e "${CYAN}  Auto RETO Tester v2 (clipboard paste)    ${NC}"
echo -e "${CYAN}══════════════════════════════════════════${NC}"
echo ""
echo -e "Results: ${YELLOW}$RESULTS_DIR${NC}"
echo -e "${#TEST_CASES[@]} cases"
echo ""

# ── Clear and start logcat ──
$ADB logcat -c
$ADB logcat -v time > "$LOGFILE" 2>&1 &
LOGCAT_PID=$!

cleanup() {
  kill $LOGCAT_PID 2>/dev/null || true
  echo ""
  echo -e "${YELLOW}Parsing results...${NC}"
  python3 "$SCRIPT_DIR/parse_ret_results.py" "$LOGFILE" "$RESULTS_DIR/parsed_report.md" 2>/dev/null || true
  echo -e "${GREEN}Done: $RESULTS_DIR${NC}"
}
trap cleanup EXIT

sleep 1

TOTAL=${#TEST_CASES[@]}
CURRENT=0

for case in "${TEST_CASES[@]}"; do
  CURRENT=$((CURRENT + 1))
  IFS='|' read -r num category query <<< "$case"

  echo ""
  echo -e "${CYAN}[${CURRENT}/${TOTAL}]${NC} ${YELLOW}#${num} ${category}${NC}"
  echo -e "  \"${query:0:70}...\""

  # ── Clear and set clipboard via Android 13+ cmd clipboard ──
  # Try Android 13+ API first
  $ADB shell cmd clipboard set "$query" 2>/dev/null && CLIP_OK=1 || CLIP_OK=0

  if [ "$CLIP_OK" = "0" ]; then
    # Fallback: use service call (older API levels)
    $ADB shell "am broadcast -a clipper.set -e text '$query'" 2>/dev/null || true
    # Or use input text with escaping as last resort
    $ADB shell input text "$query" 2>/dev/null || true
    echo -e "  ${YELLOW}⚠ Using fallback text entry${NC}"
    sleep 1
    $ADB shell input tap $GEN_X $GEN_Y
    sleep 1
  else
    # Clipboard set successfully — now paste it
    # Tap text field, select all, paste from clipboard
    $ADB shell input tap $INPUT_X $INPUT_Y
    sleep 0.3
    # Select all: long-press equivalent via keyevents
    $ADB shell input keyevent 29   # KEYCODE_CTRL_LEFT down
    $ADB shell input keyevent 31   # A
    sleep 0.1
    $ADB shell input keyevent 30   # KEYCODE_CTRL_LEFT up
    sleep 0.2
    # Paste
    $ADB shell input keyevent 29   # KEYCODE_CTRL_LEFT down
    $ADB shell input keyevent 50   # V
    sleep 0.1
    $ADB shell input keyevent 30   # KEYCODE_CTRL_LEFT up
    sleep 0.3
    # Tap Generate
    $ADB shell input tap $GEN_X $GEN_Y
  fi

  echo -e "  ${GREEN}▶ Submitted${NC}"

  # ── Wait for completion ──
  WAIT=0
  MAX_WAIT=180
  while [ $WAIT -lt $MAX_WAIT ]; do
    STATUS=$($ADB logcat -d -s WorkflowGeneration:D 2>/dev/null | grep -oE "stage = [A-Za-z ]+" | tail -1 | tr -d '\r' || true)
    if echo "$STATUS" | grep -qiE "Done|Validation|Failed"; then
      echo -e "  ${GREEN}✓ Done (${WAIT}s)${NC}"
      break
    fi
    sleep 3
    WAIT=$((WAIT + 3))
  done

  if [ $WAIT -ge $MAX_WAIT ]; then
    echo -e "  ${RED}⚠ Timeout${NC}"
  fi

  sleep 2
done

echo ""
echo -e "${GREEN}All ${TOTAL} tests submitted${NC}"
sleep 5
