#!/bin/bash
# IrisApp RETO Test Runner
# Captures Phase 0-4 + final JSON for each test case from workflow_category_tester.md
#
# Usage:
#   1. Start the app on device/emulator and wait for model to load
#   2. Run: ./run_ret_tests.sh
#   3. For each test case: paste query → tap Generate → press Enter here
#   4. Results saved to docs/testing/results/YYYYMMDD_HHMMSS/
#
# Requirements:
#   - adb in PATH
#   - Device/emulator connected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_DIR="$SCRIPT_DIR/results/$TIMESTAMP"
LOGFILE="$RESULTS_DIR/full_logcat.txt"
SUMMARY="$RESULTS_DIR/summary.md"
ADB="adb"

mkdir -p "$RESULTS_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# RETO pipeline log tags to capture
FILTER="TaskDecomposer|CapabilityBinder|SlotGroundingPlanner|RequirementBuilder|ResolverRegistry|RetoOrchestrator|WorkflowGeneration|ToolRegistry|RetoWorkflowPlanner|CoverageValidator|WorkflowJsonParser|WorkflowValidator"

# ── Test cases extracted from the markdown ──
# Format: "number|category|query"
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
  "12|take_note|Create a note called Groceries with milk, eggs, coffee, and bananas."
  "13|check_notification|Check if I missed any WhatsApp notifications from Maya."
  "14|get_info|Find out whether it will rain in Dubai tomorrow morning."
  "15|other|Make my phone more productive for the hackathon."
)

CHAINED_CASES=(
  "C1|send_message+create_event|send message to Maya saying hi, and invite him to meeting on 6 oclock on next friday and then add it to my calendar."
  "C2|make_call+send_message|Call Mom, then text her that I booked dinner for Friday at 8pm."
  "C3|create_event+set_reminder|Create a calendar event for team demo next Monday at 10am, then remind me 30 minutes before."
  "C4|navigate+send_message|Navigate to Dubai Mall and share my ETA with Maya."
  "C5|open_app+play_media+set_alarm|Open Spotify and play my workout playlist, then set a 45 minute timer."
  "C6|open_file+take_note|Open my budget spreadsheet and make a note that I need to update May expenses."
  "C7|search+navigate|Search for the nearest pharmacy open now and navigate there."
  "C8|set_reminder+open_app|Remind me tomorrow at 9am to call the dentist, then open the phone app."
)

EDGE_CASES=(
  "E1|ambiguous_time|Schedule coffee with Sara at 6 next Friday."
  "E2|missing_contact|Text my dentist that I am running late."
  "E3|missing_app|Play my focus playlist on Spotify."
  "E4|unsupported|When I receive a WhatsApp from Maya, summarize it."
  "E5|vague|Help me study better every day."
)

echo ""
echo -e "${CYAN}══════════════════════════════════════════${NC}"
echo -e "${CYAN}  IrisApp RETO Test Runner          ${NC}"
echo -e "${CYAN}══════════════════════════════════════════${NC}"
echo ""
echo -e "Results dir: ${YELLOW}$RESULTS_DIR${NC}"
echo ""

# ── Check adb ──
if ! command -v $ADB &>/dev/null; then
  ADB="$HOME/Library/Android/sdk/platform-tools/adb"
fi
if ! command -v $ADB &>/dev/null; then
  echo -e "${RED}ERROR: adb not found at $ADB${NC}"
  exit 1
fi

DEVICE_COUNT=$($ADB devices | grep -v "List" | grep -c "device" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo -e "${RED}ERROR: No device/emulator connected${NC}"
  exit 1
fi
echo -e "Device: ${GREEN}$($ADB devices | grep -m1 device | awk '{print $1}')${NC}"
echo ""

# ── Start logcat capture ──
echo -e "${YELLOW}Starting logcat capture...${NC}"
$ADB logcat -c  # clear buffer
$ADB logcat -v time > "$LOGFILE" 2>&1 &
LOGCAT_PID=$!
trap "kill $LOGCAT_PID 2>/dev/null; echo ''; echo 'Log capture stopped.'" EXIT

echo -e "Logcat PID: $LOGCAT_PID"
echo ""

# ── Run test cases ──
run_test() {
  local num="$1"
  local category="$2"
  local query="$3"

  echo ""
  echo -e "${CYAN}──────────────────────────────────────────${NC}"
  echo -e "${CYAN}  Test #${num} — ${category}${NC}"
  echo -e "${CYAN}──────────────────────────────────────────${NC}"
  echo ""
  echo -e "  ${YELLOW}Query:${NC} $query"
  echo ""
  echo -e "  → Paste this query into the app, tap Generate"
  echo -e "  → Wait for workflow preview to appear"
  echo -e "  → Press ${GREEN}Enter${NC} when done (or 's' to skip)"
  echo ""

  # Copy to clipboard for easy pasting
  if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -n "$query" | pbcopy
    echo -e "  (Query copied to clipboard — Cmd+V in emulator)"
  fi

  read -r input
  if [ "$input" = "s" ]; then
    echo -e "  ${YELLOW}⏭ Skipped${NC}"
    echo "## Test #${num} — ${category}" >> "$SUMMARY"
    echo "" >> "$SUMMARY"
    echo "*SKIPPED by user*" >> "$SUMMARY"
    echo "" >> "$SUMMARY"
    return
  fi

  echo -e "  ${GREEN}✓ Captured${NC}"

  # Add a marker to logcat for later parsing
  $ADB logcat -v time -s "WorkflowGeneration:D" "TEST_MARKER:I" 2>&1 | head -1 >/dev/null || true

  echo "## Test #${num} — ${category}" >> "$SUMMARY"
  echo "" >> "$SUMMARY"
  echo "**Query:** \`$query\`" >> "$SUMMARY"
  echo "" >> "$SUMMARY"
  echo "*Captured — see full log*" >> "$SUMMARY"
  echo "" >> "$SUMMARY"
}

# ── Write summary header ──
cat > "$SUMMARY" << 'HEADER'
# IrisApp RETO Test Results

**Date:** TIMESTAMP_PLACEHOLDER
**Log file:** full_logcat.txt

---

## Core Category Cases

HEADER
sed -i '' "s/TIMESTAMP_PLACEHOLDER/$(date)/" "$SUMMARY"

echo "" >> "$SUMMARY"

for case in "${TEST_CASES[@]}"; do
  IFS='|' read -r num category query <<< "$case"
  run_test "$num" "$category" "$query"
done

echo "" >> "$SUMMARY"
echo "---" >> "$SUMMARY"
echo "" >> "$SUMMARY"
echo "## Chained Workflow Cases" >> "$SUMMARY"
echo "" >> "$SUMMARY"

for case in "${CHAINED_CASES[@]}"; do
  IFS='|' read -r num category query <<< "$case"
  run_test "$num" "$category" "$query"
done

echo "" >> "$SUMMARY"
echo "---" >> "$SUMMARY"
echo "" >> "$SUMMARY"
echo "## Edge Cases" >> "$SUMMARY"
echo "" >> "$SUMMARY"

for case in "${EDGE_CASES[@]}"; do
  IFS='|' read -r num category query <<< "$case"
  run_test "$num" "$category" "$query"
done

# ── Stop logcat ──
kill $LOGCAT_PID 2>/dev/null || true

# ── Parse results ──
echo ""
echo -e "${CYAN}══════════════════════════════════════════${NC}"
echo -e "${CYAN}  Parsing results...                      ${NC}"
echo -e "${CYAN}══════════════════════════════════════════${NC}"
echo ""

python3 "$SCRIPT_DIR/parse_ret_results.py" "$LOGFILE" "$RESULTS_DIR/parsed_report.md" 2>/dev/null || {
  echo -e "${YELLOW}Python parser not available — raw logs saved${NC}"
}

echo ""
echo -e "${GREEN}Done!${NC}"
echo ""
echo -e "Results saved to: ${YELLOW}$RESULTS_DIR${NC}"
echo -e "  Full log:       ${YELLOW}$LOGFILE${NC}"
echo -e "  Summary:        ${YELLOW}$SUMMARY${NC}"
echo ""
echo -e "To inspect a specific case:"
echo -e "  grep -A 50 'Test #N' $LOGFILE"
echo ""
