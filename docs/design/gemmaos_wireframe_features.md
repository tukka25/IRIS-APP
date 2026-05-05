# GemmaOS Wireframe Feature Brief

## Purpose

GemmaOS is a premium dark-mode iPhone app for non-technical users who want their phone to help them automatically. The app uses local on-device AI to turn plain-language requests into reusable phone helpers called **Routines**.

The core product flow is:

```text
Ask in plain language -> AI interprets request -> Routine preview -> user approves -> Routine is saved -> Routine can run manually or through Shortcuts/automation setup
```

The experience should feel simple, trustworthy, and Apple-like. Users should always understand:

- What the Routine does.
- When it runs.
- Whether it is active or off.
- Whether it can run automatically.
- Whether it needs setup before it can work.
- Whether it needs user confirmation before taking action.

## Product Principles

- **Plain language first:** avoid technical words like JSON, model, parser, intents, or automation graph.
- **User control:** no Routine should feel hidden. Every active Routine must be visible and easy to turn off.
- **Clear automation state:** active, off, manual-only, and setup-needed states must be visually obvious.
- **Local AI trust:** show that AI runs on-device and private without making the UI feel technical.
- **Preview before action:** users should see what will happen before a Routine is created or enabled.
- **Calm confidence:** the app should feel premium and quiet, not playful or overloaded.

## App Structure

Bottom tab navigation with 4 tabs:

1. **Home**
2. **Ask**
3. **Routines**
4. **Activity**

Secondary screens can open from these tabs:

- Routine Preview
- Routine Detail
- Example Routine Gallery / Marketplace
- Success Sheet
- Setup Sheet

## Core Objects

### Routine

A Routine is a reusable helper created from a user request.

Required fields shown in UI:

- Routine name
- Short description
- Status
- Trigger type
- Last run
- Shortcut connection status
- Setup requirement
- Confirmation requirement

Example:

```text
Expense Capture
Saves receipts and logs spending from photos.
Status: Active
Trigger: Share Sheet
Last run: Today, 2:18 PM
Shortcut: Connected
Confirmation: Required before saving
```

### Routine Statuses

Use clear labels and consistent badges:

| Status | Meaning | Suggested Badge |
|--------|---------|-----------------|
| Active | Routine can run automatically or from its trigger | Green dot, `Active` |
| Off | Routine is saved but disabled | Gray dot, `Off` |
| Manual Only | Routine can run only when the user taps Run Now | Blue dot, `Manual` |
| Needs Setup | Routine exists but cannot run until user finishes setup | Amber dot, `Needs Setup` |
| Failed Setup | Routine tried to connect but something failed | Red dot, `Fix Setup` |

### Trigger Types

Trigger language should stay user-friendly:

| Trigger | User-Facing Copy |
|---------|------------------|
| Manual | Runs when you tap Run Now |
| Time | Runs at a scheduled time |
| Location | Runs when you arrive or leave |
| Share Sheet | Runs when you share something to GemmaOS |
| Shortcut | Runs from an Apple Shortcut |
| NFC | Runs when you tap an NFC tag |
| App Event | Runs from a connected app or action |

## Visual Style

### Mood

- Premium dark mode.
- Black and deep graphite backgrounds.
- White text with soft gray secondary text.
- Compact rounded cards.
- Calm, approachable, and trustworthy.
- Apple-like clarity with simple hierarchy.

### Suggested Palette

```text
Background: #050505
Surface: #111113
Elevated Surface: #1A1A1D
Border: #2B2B30
Primary Text: #FFFFFF
Secondary Text: #A9A9B2
Muted Text: #70707A
Active: #42D77D
Needs Setup: #F5B849
Manual: #6EA8FF
Error: #FF5A66
```

### Component Style

- Use compact cards with 8-12px radius.
- Avoid large decorative hero cards.
- Use simple badges for states.
- Use icons only where they make the status easier to scan.
- Keep primary actions visually strong but not loud.
- Use bottom sheets for confirmations and setup steps.

## Global Components

### Local AI Ready Badge

Purpose: reassure users that the app is private and ready.

Content:

```text
Local AI Ready
Runs on this iPhone
```

States:

- Ready
- Loading local AI
- Local AI unavailable
- Offline and ready

### Routine Card

Used on Home, Routines, and gallery preview surfaces.

Fields:

- Routine name
- One-line explanation
- Status badge
- Trigger type
- Last run
- Shortcut connected badge
- On/off toggle when appropriate

Actions:

- Tap card: open Routine Detail
- Toggle: turn Routine on/off
- Long press or overflow: Edit, Duplicate, Delete

### Status Badge

Badges should be short:

- `Active`
- `Off`
- `Manual`
- `Needs Setup`
- `Connected`
- `Shortcut Ready`
- `Confirm First`

### Routine Preview Card

Used after AI interprets a request.

Fields:

- Suggested name
- What it will do
- When it will run
- Apps or system features involved
- Confirmation requirement
- Setup requirement

Actions:

- Preview
- Create Routine
- Save for Later

## Screen 1: Home

### Goal

Home is the friendly command center. It should help users ask for help, understand that local AI is ready, and see what Routines are currently active.

### Wireframe Sections

1. **Welcome Header**
   - Greeting: `Good morning, Alex`
   - Subtext: `Your phone helpers are ready.`
   - Local AI Ready badge.

2. **Main Ask Input**
   - Prompt: `What would you like your phone to help with?`
   - Large rounded input area.
   - Send/arrow button.
   - Microcopy: `Describe a task in your own words.`

3. **Suggested Ideas**
   - Horizontal chips or compact cards.
   - Examples:
     - `Track my spending from receipts`
     - `Summarize my day every evening`
     - `Save quick notes from anywhere`
     - `Remind me to study when I get home`

4. **Running Now**
   - Compact metrics row:
     - `3 Active`
     - `1 Needs Setup`
     - `5 Recently Used`
   - Each count should be tappable and deep-link to filtered Routines or Activity.

5. **Active Routine Preview**
   - 2-3 Routine cards.
   - Show toggle, status badge, trigger, and last run.
   - Link: `View all Routines`

6. **Quick Links**
   - `Ask`
   - `Routines`
   - `Activity`
   - `Explore Examples`

### Empty State

If the user has no Routines:

```text
No Routines yet
Ask GemmaOS to create your first phone helper.
```

Primary action: `Create a Routine`

Secondary action: `Browse Examples`

## Screen 2: Ask

### Goal

Ask is where the user describes what they want. It should show the AI interpretation in plain English and let the user create a Routine only after reviewing it.

### Wireframe Sections

1. **Natural-Language Input**
   - Title: `Ask GemmaOS`
   - Input placeholder: `Tell your phone what you want help with...`
   - Large multiline area.
   - Primary button: `Understand Request`

2. **Example Requests**
   - Compact examples beneath the input.
   - Examples:
     - `When I take a receipt photo, save it and log the amount.`
     - `Every night, summarize my calendar and reminders for tomorrow.`
     - `When I share text, save it as a quick note.`
     - `At 8 PM, ask me what I studied today.`

3. **AI Interpretation**
   - Plain English summary.
   - Example:

```text
GemmaOS thinks you want a Routine that saves receipt photos, extracts the total, and adds the expense to a spending log.
```

4. **Routine Preview**
   - Suggested Routine name.
   - Trigger suggestion.
   - Apps/features involved.
   - Expected result.

5. **Trigger Suggestion**
   - Example:

```text
Suggested trigger: Share Sheet
Run this when you share a receipt image to GemmaOS.
```

6. **Actions**
   - Primary: `Create Routine`
   - Secondary: `Preview`
   - Tertiary: `Save for Later`

### Loading State

While local AI is interpreting:

```text
Understanding your request...
Running locally on this iPhone
```

### Error State

If AI cannot create a useful Routine:

```text
GemmaOS needs a little more detail.
Try mentioning when this should run and what should happen.
```

## Screen 3: Routine Preview

### Goal

Routine Preview is the approval screen. The user must understand what will happen before turning anything on.

### Wireframe Sections

1. **Routine Header**
   - Routine name.
   - Short description.
   - Status badge: `Preview`

2. **When It Runs**
   - Trigger type.
   - Schedule/location/share/shortcut detail.
   - If setup is needed, show `Needs Setup` clearly.

3. **What It Does**
   - Step-by-step plain-language list.
   - Example:
     - `Reads the receipt image you share.`
     - `Finds the store, date, and total.`
     - `Adds the expense to your Money log.`
     - `Saves the receipt image for later.`

4. **Confirmation**
   - State:
     - `Runs after you confirm`
     - `Can run automatically after setup`
     - `Manual only`

5. **Setup Requirements**
   - Explain what is required:
     - `Connect to Shortcuts`
     - `Allow Photos access`
     - `Choose a storage location`

6. **Actions**
   - Primary: `Turn On`
   - Secondary: `Save`
   - Tertiary: `Edit`

### Safety Copy

Use small text near the action area:

```text
You can turn this off anytime from Routines.
```

## Screen 4: Routines

### Goal

Routines is the library/hub where users manage everything they have created or saved.

### Wireframe Sections

1. **Header**
   - Title: `Routines`
   - Search icon or search field.
   - Filter button.

2. **Status Filters**
   - Segmented control or chips:
     - `All`
     - `Active`
     - `Off`
     - `Needs Setup`
     - `Manual`

3. **Active Section**
   - Routine cards with toggles.
   - Show trigger, last run, and Shortcut badge.

4. **Needs Setup Section**
   - Cards should have stronger warning affordance.
   - Primary inline action: `Finish Setup`

5. **Off Section**
   - Saved but inactive Routines.
   - Toggle available.

6. **Suggested Section**
   - Small marketplace preview.
   - Link: `Explore Routine Gallery`

### Routine Card Requirements

Every card should show:

- Routine name
- One-line explanation
- Status badge
- Trigger type
- Last run
- On/off toggle
- Shortcut connected badge when applicable

Example card:

```text
Receipt Saver
Saves receipt photos and logs the total.
Active | Share Sheet | Last run Today
Shortcut Connected
[Toggle On]
```

## Screen 5: Routine Detail

### Goal

Routine Detail gives the user full control over one Routine.

### Wireframe Sections

1. **Header**
   - Routine name.
   - Status badge.
   - On/off toggle.

2. **Summary**
   - Short description.
   - Last run.
   - Created date.

3. **What It Does**
   - Step-by-step plain-language explanation.
   - Avoid technical automation details by default.

4. **Trigger Details**
   - Trigger type and configuration.
   - Example:

```text
Runs from Share Sheet when you send a photo to GemmaOS.
```

5. **Connection Status**
   - Shortcuts connected state.
   - Required permissions.
   - Setup status.

6. **Activity Preview**
   - Last 3 runs.
   - Success/failure state.
   - Link: `View full activity`

7. **Actions**
   - Primary: `Run Now`
   - Secondary: `Edit`
   - Secondary destructive: `Delete`
   - Toggle/action: `Turn Off` or `Turn On`

### Delete Confirmation

Use a bottom sheet:

```text
Delete this Routine?
This removes the Routine from GemmaOS. Connected Shortcuts may also need to be removed.
```

Actions:

- `Delete Routine`
- `Cancel`

## Screen 6: Activity

### Goal

Activity shows what GemmaOS has done recently, what ran automatically, what ran manually, and what needs attention.

### Wireframe Sections

1. **Header**
   - Title: `Activity`
   - Filter icon.

2. **Activity Filters**
   - `All`
   - `Automatic`
   - `Manual`
   - `Needs Attention`

3. **Timeline**
   - Group by day:
     - `Today`
     - `Yesterday`
     - `This Week`

4. **Activity Items**
   - Routine name.
   - Run type: automatic/manual.
   - Result: success/failure/setup needed.
   - Timestamp.
   - Short plain-English result.

Example:

```text
Receipt Saver
Success | Manual | 2:18 PM
Saved a receipt from Starbucks and logged $8.40.
```

5. **Setup Reminders**
   - Inline cards for blocked Routines.
   - Example:

```text
Daily Summary needs setup
Connect a Shortcut before it can run every evening.
```

Action: `Finish Setup`

### Failure State

Example:

```text
Quick Notes could not run
GemmaOS needs permission to save notes.
```

Action: `Fix Permission`

## Screen 7: Example Routine Gallery / Marketplace

### Goal

The gallery is a user-friendly marketplace of Routine ideas. Users can browse examples, preview what they do, and create personalized versions.

This should not feel like a developer marketplace. It should feel like a calm catalog of useful phone helpers.

### Entry Points

- Home quick link: `Explore Examples`
- Routines suggested section
- Empty state button: `Browse Examples`
- Ask screen example prompt cards

### Wireframe Sections

1. **Header**
   - Title: `Routine Gallery`
   - Subtitle: `Start from a helpful example. GemmaOS will personalize it.`

2. **Search**
   - Placeholder: `Search routines`

3. **Category Tabs**
   - `Money`
   - `Daily Life`
   - `Study`
   - `Productivity`

4. **Featured Routine**
   - Larger compact card.
   - Example:

```text
Expense Capture
Track spending from receipts in seconds.
Money | Share Sheet | Needs confirmation
```

5. **Routine Example Grid/List**
   - Compact cards.
   - Each card shows:
     - Name
     - Category
     - One-line value
     - Trigger suggestion
     - Setup level: `Easy`, `Needs Shortcut`, or `Manual`

6. **Preview Action**
   - Tap example to open template detail.
   - Actions:
     - `Use This`
     - `Preview`
     - `Save Idea`

### Categories And Example Routines

#### Money

- **Expense Capture**
  - Saves receipt photos, extracts totals, and logs spending.
  - Trigger: Share Sheet.
  - Setup: Needs storage or spreadsheet destination.

- **Receipt Saver**
  - Files receipt images into a monthly folder.
  - Trigger: Share Sheet.
  - Setup: Needs Photos or Files permission.

- **Subscription Reminder**
  - Reminds the user before recurring bills.
  - Trigger: Time.
  - Setup: Manual entry or calendar connection.

#### Daily Life

- **Daily Summary**
  - Summarizes calendar, reminders, and important notes.
  - Trigger: Time.
  - Setup: Needs Shortcuts connection.

- **Leaving Home Checklist**
  - Reminds the user about wallet, keys, charger, and errands.
  - Trigger: Location.
  - Setup: Needs location permission.

- **Grocery Helper**
  - Adds shared grocery text to a shopping list.
  - Trigger: Share Sheet.
  - Setup: Needs list destination.

#### Study

- **Study Recap**
  - Asks what the user studied and saves a short summary.
  - Trigger: Time.
  - Setup: Manual or Shortcut.

- **Lecture Notes Cleaner**
  - Turns rough notes into clean bullet points.
  - Trigger: Share Sheet.
  - Setup: Manual only by default.

- **Exam Countdown**
  - Sends study reminders before an exam date.
  - Trigger: Time.
  - Setup: Needs exam date.

#### Productivity

- **Quick Notes**
  - Saves shared text as a clean note.
  - Trigger: Share Sheet.
  - Setup: Needs notes destination.

- **Meeting Prep**
  - Creates a short prep note before calendar events.
  - Trigger: Time/calendar.
  - Setup: Needs calendar access.

- **Focus Launcher**
  - Starts a focus mode, opens study tools, and logs start time.
  - Trigger: Manual or Shortcut.
  - Setup: Needs Shortcut connection.

### Template Detail

When a gallery item is opened, show:

- Routine name.
- What it helps with.
- How it works.
- Suggested trigger.
- What setup it needs.
- What the user can customize.

Actions:

- `Use This`
- `Customize`
- `Save Idea`

## Screen 8: Success Sheet

### Goal

The Success Sheet confirms that the user completed an important action and tells them what happens next.

### Variants

#### Routine Created

```text
Routine created
Receipt Saver is saved in your Routines.
```

Actions:

- `Turn On`
- `View Routine`
- `Done`

#### Routine Is Now Active

```text
Routine is now active
Receipt Saver will run when you share a receipt to GemmaOS.
```

Actions:

- `View Routine`
- `Done`

#### Setup Needed

```text
Setup needed before this can run automatically
Daily Summary is saved, but it needs a Shortcut connection first.
```

Actions:

- `Finish Setup`
- `Keep Manual Only`
- `Done`

#### Saved For Later

```text
Saved for later
You can finish this Routine anytime from Routines.
```

Actions:

- `View Saved Routine`
- `Done`

## Setup Flow

### Goal

Setup should explain what is needed without exposing technical details.

### Possible Setup Steps

- Connect Shortcut.
- Allow Photos access.
- Allow Location access.
- Choose save destination.
- Confirm trigger.
- Test Routine.

### Setup Screen / Sheet Structure

1. **What needs setup**
   - Plain-English explanation.

2. **Why it is needed**
   - Example:

```text
GemmaOS needs a Shortcut so this can run automatically every evening.
```

3. **Steps**
   - Step list with completion states.

4. **Actions**
   - `Continue Setup`
   - `Run Manually Instead`
   - `Cancel`

## Recommended First Wireframe Flow

Use this path for the first clickable prototype:

1. Home with empty state and example ideas.
2. User taps an idea or types a request.
3. Ask screen shows AI interpretation.
4. User taps `Preview`.
5. Routine Preview explains the Routine.
6. User taps `Create Routine`.
7. Success Sheet appears.
8. User opens Routines tab.
9. New Routine appears under `Needs Setup` or `Manual Only`.
10. User opens Routine Detail.
11. User taps `Run Now`.
12. Activity shows the successful manual run.

## Sample User Prompts

Use these prompts in the wireframes:

- `Help me save receipts and track what I spend.`
- `Every evening, summarize tomorrow from my calendar and reminders.`
- `When I share text, save it as a quick note.`
- `Remind me to study when I get home.`
- `Create a focus routine for deep work.`

## Sample Routine Copy

### Expense Capture

```text
Saves receipts and logs spending from photos.
Runs when you share a receipt to GemmaOS.
Needs confirmation before saving.
```

### Daily Summary

```text
Prepares a short summary of tomorrow every evening.
Runs at 8:00 PM.
Needs Shortcut setup before automatic runs.
```

### Quick Notes

```text
Turns shared text into a clean note.
Runs from the Share Sheet.
Manual only until you choose a notes destination.
```

## Wireframe Checklist

The wireframes should include:

- 4 bottom tabs.
- Home dashboard.
- Ask flow.
- Routine Preview.
- Routine library with sections.
- Routine Detail.
- Activity timeline.
- Example Routine Gallery / Marketplace.
- Success Sheet variants.
- Setup-needed state.
- Active/off/manual/needs-setup badges.
- On/off Routine toggle.
- Shortcut connected badge.
- Empty states.
- Error/failure states.
