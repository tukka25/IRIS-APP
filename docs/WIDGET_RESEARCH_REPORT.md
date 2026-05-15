# Android Home-Screen Widget: Glance vs RemoteViews
## Technical Research Report for IrisApp

---

## 1. Context from the Project

**Repo**: `/mnt/d/gemma4good_android` (Kotlin, Jetpack Compose, minSdk 26, targetSdk 34)

**Existing data layer**:
- `ExecutionHistoryRepository`: JSON file storage at `filesDir/history/execution_log.json`, append-only, keeps last 100 entries.
- Data models already exist:
  - `ExecutionLogEntry(workflowName, timestampMillis, results: List<ExecutionResult>, allSuccess)`
  - `ExecutionResult(stepId, success, message, timestampMillis)`

**Widget requirement**: Show execution history or live progress for ONE user-selected workflow on the home screen.

---

## 2. The Two Approaches

### 2A. RemoteViews (Legacy / XML Widgets)

The traditional Android widget approach. You write XML layouts, inflate them in an `AppWidgetProvider`, and push updates via `RemoteViews` + `AppWidgetManager`.

```kotlin
// WidgetProvider
class WorkflowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_workflow)
            views.setTextViewText(R.id.tv_status, "Last run: Success")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
```

XML layout (`res/layout/widget_workflow.xml`):
```xml
<LinearLayout>
    <TextView android:id="@+id/tv_workflow_name" />
    <TextView android:id="@+id/tv_status" />
    <ProgressBar android:id="@+id/pb_progress" style="?android:attr/progressBarStyleHorizontal" />
</LinearLayout>
```

**Real-time / live updates**: Requires a `Service` with a foreground notification, or polling via `AlarmManager` + `PendingIntent`, or using `AppWidgetManager.updateAppWidget()` from a coroutine started by `WorkManager`. The widget cannot hold its own background thread.

**Cross-process data bridging**: 
- `SharedPreferences` with `registerOnSharedPreferenceChangeListener` (but the widget process won't receive callbacks — must poll).
- `ContentProvider` wrapping the JSON file.
- File I/O directly from the widget (same app process, no IPC needed for own app's files).

**Manifest entry**:
```xml
<receiver android:name=".widget.WorkflowWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/workflow_widget_info" />
</receiver>
```

**Widget configuration activity** (for user to select which workflow):
```xml
<activity android:name=".widget.WidgetConfigActivity">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

### 2B. Jetpack Glance (Compose-based Widgets)

The modern, Google-recommended approach (2021+). Uses a Kotlin DSL instead of XML. Composables run in the widget's own process with their own lifecycle.

```kotlin
class WorkflowGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Called on a background thread — can do file I/O directly
        val historyRepo = ExecutionHistoryRepository(context)
        val recentRuns = historyRepo.recent(5)

        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Workflow History",
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
                recentRuns.take(3).forEach { entry ->
                    Row(...) {
                        Text(entry.workflowName)
                        Text(if (entry.allSuccess) "OK" else "FAILED")
                    }
                }
            }
        }
    }
}

// GlanceWidgetReceiver (manifest registered like AppWidgetProvider)
class WorkflowGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkflowGlanceWidget()
}
```

**Real-time / live updates**: 
- `GlanceAppWidget.updateAll(context)` can be called from anywhere (Service, WorkManager, `BroadcastReceiver`, or the main app). It enqueues work via `CoroutineContext` internally.
- For live progress during an active run: the `WorkflowRunner` calls `widget.updateAll()` after each step completes. Since the widget's `provideGlance` is a suspend function, it runs on the widget's internal `Dispatchers.Main` under the hood, but the initial load of data must be on `Dispatchers.IO`.
- Periodic refresh via `WorkManager.periodicWorkQuery` or `AlarmManager` — same as RemoteViews.
- Glance supports `GlanceTheme`, `rememberGlanceState()` for local UI state, and `actionStartActivity()` for click handling.

**Cross-process data bridging**:
- Since `ExecutionHistoryRepository` reads a file in `context.filesDir`, it can be accessed directly from the widget's `provideGlance()` suspend function (same process). No ContentProvider or AIDL needed.
- If data were in a different process, you'd use a `ContentProvider`.

**Manifest entry**:
```xml
<receiver android:name=".widget.WorkflowGlanceWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
</receiver>
```

**Widget configuration activity** (same pattern as RemoteViews but uses Glance-specific APIs):
```xml
<activity android:name=".widget.WidgetConfigActivity">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

---

## 3. Detailed Comparison

| Dimension | RemoteViews | Jetpack Glance |
|-----------|-------------|----------------|
| **UI paradigm** | XML layouts + `RemoteViews` API imperative | Kotlin DSL + `@Composable` declarative |
| **Compose integration** | None (pre-Compose) | Native Compose runtime in widget process |
| **Code style** | Imperative, procedural | Declarative, self-contained `provideGlance()` |
| **Click actions** | `PendingIntent` via `RemoteViews.setOnClickPendingIntent()` | `actionStartActivity()`, `actionRunCallback()` — cleaner coroutine-based callbacks |
| **State management** | `SharedPreferences` (must poll) or file I/O | `GlanceStateDefinition` with `PreferencesGlanceStateDefinition`, or custom `GlanceStateDefinition` with file/DB |
| **Background work for data** | Must be done outside widget (Service/WorkManager) | Can be done inside `provideGlance()` suspend function |
| **Min SDK** | API 4 (all Android) | API 26+ (matches project's minSdk 26) |
| **Look and feel** | XML widgets, older styling | Material 3 / Compose theming available |
| **Performance** | Cross-process IPC per update (layout inflation, bitmap render) | Widget process has its own Compose runtime — fewer IPC round-trips |
| **Maturity** | 2008, stable, well-documented | 2021, stable, still evolving |
| **Learning curve** | Steeper for Compose developers | Familiar for Compose developers |
| **List/collections** | `RemoteViews.setRemoteAdapter()` + `ListView` (complex, deprecated in API 31) | `LazyColumn` via `androidx.glance.appwidget.lazy` (Compose API) |
| **AppWidgetProvider** | Subclass `AppWidgetProvider` | Subclass `GlanceAppWidgetReceiver` + `GlanceAppWidget` |
| **Updater** | `AppWidgetManager.updateAppWidget()` | `GlanceAppWidget.updateAll()` or `glanceId.let { widget.update(it) }` |

---

## 4. Real-Time Update Mechanisms

### For live progress (workflow running NOW)

The widget needs to show step-by-step progress as the workflow executes. There are two scenarios:

**Scenario A: Workflow runs in the main app process**

The `WorkflowRunner` already runs in the app's process. After each step completes, it can call:

```kotlin
// In WorkflowRunner, after each step:
val widget = WorkflowGlanceWidget()
widget.updateAll(context) // Static call, enqueues recomposition
```

Since `provideGlance()` is a suspend function, the widget reads the latest state from `ExecutionHistoryRepository` on each update. The widget is always showing the current file state — no need for a separate "in-progress" flag if the file reflects the running state.

However, `updateAll()` is a static convenience method that updates **all** instances of the widget. For a targeted update of a specific `GlanceId`:

```kotlin
// Better: keep a reference to the active GlanceId
suspend fun updateWidget(context: Context, glanceId: GlanceId) {
    WorkflowGlanceWidget().update(context, glanceId)
}
```

**Scenario B: Workflow runs in a foreground Service or WorkManager (different process)**

If the runner moves to a Service (e.g., for background NFC-triggered runs), the same `updateAll()` call still works from that Service's process — it's just a static call that talks to the widget host. The data is read from the shared JSON file.

**Limitation**: There is NO mechanism for truly push-based real-time updates in either approach. Both RemoteViews and Glance rely on:
1. Widget being added to the home screen (triggers initial `onUpdate`)
2. `AppWidgetManager.updateAppWidget()` / `GlanceAppWidget.updateAll()` being called explicitly by the app
3. Android's own background widget refresh (inexact, battery-dependent, ~30-60 min intervals when idle)

**For sub-minute live updates**, you need:
- A foreground `Service` that keeps running during the workflow
- After each step: call `updateAll()` 
- This works because the foreground service process stays alive

**For minute-level updates** (history display, not live progress):
- `WorkManager.periodicWorkRequest` with a 15-minute minimum
- `AlarmManager` with `setExactAndAllowWhileIdle()` for more precise timing

### Updating from the main app during workflow execution

```kotlin
// In WorkflowRunner.kt — after each step result is recorded:
scope.launch {
    delay(500) // Debounce rapid updates
    withContext(Dispatchers.Main) {
        try {
            WorkflowGlanceWidget().updateAll(appContext)
        } catch (e: Exception) {
            // Widget may not be on any home screen — safe to ignore
        }
    }
}
```

---

## 5. Cross-Process Data Bridging

For IrisApp, the data bridge is simple because:
- Widget and main app are the **same app package** (`com.irisapp`)
- `ExecutionHistoryRepository` reads a JSON file in `context.filesDir`
- The widget's `Context` and the app's `Context` resolve to the **same filesDir**

### Therefore, no special IPC is needed for this project.

The widget's `provideGlance()` can directly instantiate `ExecutionHistoryRepository(context)` and read the same file:

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repo = ExecutionHistoryRepository(context)
    val selectedWorkflow = getSelectedWorkflowId(context) // from prefs
    val entries = repo.forWorkflow(selectedWorkflow).takeLast(5)
    // render entries...
}
```

### SharedPreferences for widget configuration (which workflow is selected)

```kotlin
// Save selected workflow in app
val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
prefs.edit().putString("selected_workflow_$widgetId", workflowName).apply()

// Read in widget
val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
val selectedWorkflow = prefs.getString("selected_workflow_$appWidgetId", null)
```

Glance provides `PreferencesGlanceStateDefinition` for this:

```kotlin
override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

// In provideGlance:
val workflowName = glanceState[Preferences.Key("selected_workflow")] ?: "No workflow"
```

---

## 6. Complexity and Effort Estimates

### RemoteViews approach

| Component | Effort | Complexity |
|-----------|--------|------------|
| XML layout for widget | 1-2 hrs | Low |
| `AppWidgetProvider` subclass | 2-3 hrs | Medium (lifecycle callbacks, PendingIntent wiring) |
| `WidgetConfigActivity` for workflow selection | 3-4 hrs | Medium-High (prefs wiring, return result) |
| Real-time update calls from WorkflowRunner | 1-2 hrs | Low-Medium |
| Progress bar animation during live run | 2-3 hrs | Medium (RemoteViews animations are limited) |
| Manifest + `appwidget-provider` XML | 1 hr | Low |
| **Total** | **~10-15 hrs** | |

**Risks with RemoteViews**:
- Progress bar (`ProgressBar`) in `RemoteViews` doesn't animate smoothly — you can only change the `max`/`progress` values.
- No first-class coroutine support — data loading must be done in the `AppWidgetProvider` thread or via a separate executor.
- XML layout inflation on each update is slow and memory-intensive.
- If you want a list of recent runs, `RemoteViews.setRemoteAdapter()` with `ListView` is complex and officially deprecated in Android 12+ (API 31).

### Jetpack Glance approach

| Component | Effort | Complexity |
|-----------|--------|------------|
| `GlanceAppWidget` subclass with `provideGlance()` | 2-3 hrs | Low-Medium (familiar Compose patterns) |
| `GlanceAppWidgetReceiver` in manifest | 30 min | Low |
| Compose UI for widget (text, icons, progress) | 2-3 hrs | Low (same Compose APIs) |
| `WidgetConfigActivity` (Compose-based or XML) | 3-4 hrs | Medium |
| `PreferencesGlanceStateDefinition` for config | 1-2 hrs | Low-Medium |
| Real-time `updateAll()` calls from WorkflowRunner | 1 hr | Low |
| Progress state during live run | 1-2 hrs | Low-Medium (use `LazyColumn` with sticky header or `LinearProgressIndicator`) |
| **Total** | **~11-17 hrs** | |

**Risks with Glance**:
- Glance is in beta (1.0.0-beta01 as of the latest published version). API surface is stable but still evolving.
- The `GlanceModifier` system is not identical to Compose `Modifier` — some gaps exist (e.g., no `clickable` with custom ripple at the widget level in older versions, though `actionStartActivity` handles this).
- The widget's `provideGlance()` runs in the widget process's main thread. Heavy data loading still needs to go to `Dispatchers.IO` within the suspend function.
- Debugging widget composables is harder than regular Compose debugging.

---

## 7. Recommendation

**Use Jetpack Glance.** Here is why for IrisApp specifically:

1. **Same language**: The team is already using Jetpack Compose throughout the app. Glance uses the same `@Composable` mental model and many of the same APIs (`Column`, `Row`, `Text`, `LazyColumn`). RemoteViews requires learning an entirely different imperative API.

2. **Existing data access**: `ExecutionHistoryRepository` already reads a JSON file. Glance's `provideGlance()` is a suspend function — it can call `repo.recent()` directly on `Dispatchers.IO` without any structural changes.

3. **Progress display**: Showing a step-by-step progress bar during a live run is straightforward with Compose `LinearProgressIndicator` in Glance. With RemoteViews, animating a `ProgressBar` in a widget is painful (you can only set integer values, no smooth animation from a widget process).

4. **No IPC complexity**: Both widget and app are the same package. Direct file access from `provideGlance()` is sufficient. No ContentProvider needed.

5. **Modern tooling**: Glance has `GlanceAppWidgetManager` for listing widgets, `GlanceStateDefinition` for preferences, and `actionRunCallback()` for background click handling — all coroutine-friendly.

6. **Matches minSdk**: Glance requires minSdk 26, which exactly matches IrisApp's `minSdk = 26`. No compatibility concerns.

---

## 8. Implementation Steps

### Step 1: Add Glance dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // For Material 3 styling in widget:
    implementation("androidx.glance:glance-material3:1.1.1")
}
```

**Note**: Check for the latest stable version on Google Maven (1.1.1 was recent; verify with `./gradlew dependencies`). Glance is at version 1.1.x as of late 2024/early 2025. The earlier 1.0.x versions had more limitations.

### Step 2: Create the GlanceWidget class

```
app/src/main/java/com/iris/widget/
+-- WorkflowStatusGlanceWidget.kt    # Main widget
+-- WorkflowWidgetReceiver.kt        # BroadcastReceiver entry point
+-- WidgetConfigActivity.kt          # Workflow selection UI
```

```kotlin
// WorkflowStatusGlanceWidget.kt
class WorkflowStatusGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val selectedWorkflow = prefs.getString("selected_workflow_${id.hashCode()}", null)

        val repo = ExecutionHistoryRepository(context)
        val entries = if (selectedWorkflow != null) {
            repo.forWorkflow(selectedWorkflow).takeLast(5).reversed()
        } else {
            repo.recent(5).reversed()
        }

        // Check if a workflow is currently running (look for a lock file or in-progress marker)
        val isRunning = isWorkflowRunning(context)
        val runningEntry = if (isRunning) repo.recent(1).lastOrNull() else null

        provideContent {
            WorkflowWidgetContent(
                entries = entries,
                isRunning = isRunning,
                runningEntry = runningEntry
            )
        }
    }

    @Composable
    private fun WorkflowWidgetContent(
        entries: List<ExecutionLogEntry>,
        isRunning: Boolean,
        runningEntry: ExecutionLogEntry?
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedWorkflow ?: "Recent Runs",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Live progress if running
            if (isRunning && runningEntry != null) {
                val completedSteps = runningEntry.results.count { it.success }
                val totalSteps = runningEntry.results.size
                LinearProgressIndicator(
                    progress = completedSteps.toFloat() / totalSteps.toFloat().coerceAtLeast(1f),
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp)
                )
                Text(
                    text = "Step $completedSteps/$totalSteps",
                    style = TextStyle(fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
            }

            // Recent runs list
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries) { entry ->
                    RunEntryRow(entry)
                }
            }
        }
    }

    @Composable
    private fun RunEntryRow(entry: ExecutionLogEntry) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (entry.allSuccess) "check_circle" else "error"
            val color = if (entry.allSuccess) ColorProvider(Color.Green) else ColorProvider(Color.Red)
            // Note: Icon and Color require Material 3 Glance
            Text(
                text = entry.workflowName,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontSize = 12.sp)
            )
            Text(
                text = formatTime(entry.timestampMillis),
                style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray))
            )
        }
    }
}
```

### Step 3: Create the Receiver

```kotlin
// WorkflowWidgetReceiver.kt
class WorkflowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkflowStatusGlanceWidget()
}
```

### Step 4: Register in AndroidManifest.xml

```xml
<!-- Widget receiver -->
<receiver
    android:name=".widget.WorkflowWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
</receiver>

<!-- Widget configuration activity -->
<activity
    android:name=".widget.WidgetConfigActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

### Step 5: Add widget info XML

```
res/xml/
+-- workflow_widget_info.xml
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:configure="com.irisapp.widget.WidgetConfigActivity"
    android:initialLayout="@layout/widget_initial"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
```

**Note**: `android:initialLayout` still requires an XML layout for the brief moment before Glance takes over. Create a minimal `res/layout/widget_initial.xml`.

### Step 6: WidgetConfigActivity

```kotlin
// WidgetConfigActivity.kt — user picks which workflow to monitor
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
            ?: return finish()

        // Load saved workflows
        val workflows = loadWorkflows() // from your existing WorkflowRepository

        setContent {
            MaterialTheme {
                var selected by remember { mutableStateOf<String?>(null) }
                Column {
                    Text("Select a workflow")
                    workflows.forEach { workflow ->
                        Row(
                            modifier = GlanceModifier.clickable {
                                // Save selection
                                getSharedPreferences("widget_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("selected_workflow_$appWidgetId", workflow.name)
                                    .apply()
                                // Tell widget to update
                                val manager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                                val views = RemoteViews(packageName, R.layout.widget_initial)
                                manager.updateAppWidget(appWidgetId, views)
                                // Return result
                                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        ) {
                            Text(workflow.name)
                        }
                    }
                }
            }
        }
    }
}
```

### Step 7: Trigger widget updates from WorkflowRunner

```kotlin
// In WorkflowRunner.kt, after each step:
private val widget = WorkflowStatusGlanceWidget()

fun onStepComplete(result: ExecutionResult) {
    // ... existing result recording logic ...
    // Trigger widget update
    try {
        val appContext = applicationContext
        // Update the specific widget instance if known, otherwise all instances
        widget.updateAll(appContext)
    } catch (e: Exception) {
        // Widget may not be placed on any home screen
    }
}

fun onWorkflowComplete(results: List<ExecutionResult>) {
    // ... existing history logging logic ...
    try {
        widget.updateAll(applicationContext)
    } catch (e: Exception) { /* ignore */ }
}
```

### Step 8: In-progress detection

Since `ExecutionHistoryRepository` appends only after a full workflow completes, you need a separate mechanism to detect a running workflow:

```kotlin
// Use a marker file
private val RUNNING_FILE = "history/in_progress.json"

fun markRunning(workflowName: String, totalSteps: Int) {
    File(context.filesDir, RUNNING_FILE).writeText(
        Json.encodeToString(InProgressMarker(workflowName, totalSteps, 0))
    )
}

fun updateProgress(completedSteps: Int) {
    val marker = Json.decodeFromString<InProgressMarker>(
        File(context.filesDir, RUNNING_FILE).readText()
    )
    File(context.filesDir, RUNNING_FILE).writeText(
        Json.encodeToString(marker.copy(completedSteps = completedSteps))
    )
}

fun clearRunning() {
    File(context.filesDir, RUNNING_FILE).delete()
}

data class InProgressMarker(
    val workflowName: String,
    val totalSteps: Int,
    val completedSteps: Int
)
```

Then in `provideGlance()`:

```kotlin
val inProgress = File(context.filesDir, "history/in_progress.json").takeIf { it.exists() }
val runningEntry = inProgress?.let {
    runCatching { Json.decodeFromString<InProgressMarker>(it.readText()) }.getOrNull()
}
```

---

## 9. Trade-off Summary

| Aspect | Glance | RemoteViews |
|--------|--------|-------------|
| Dev speed (for Compose devs) | Faster | Slower |
| UI expressiveness | High | Low (limited widgets) |
| Live progress quality | Good (Compose animations) | Poor (setProgress only) |
| List support | `LazyColumn` (excellent) | `RemoteViews.setRemoteAdapter()` (complex, deprecated) |
| Data access simplicity | Can read files directly in suspend | Must use background thread externally |
| Stability | Beta (1.1.x) | Stable (ancient) |
| APK size impact | Slightly larger (Compose runtime) | Smaller |
| Battery impact | Similar | Similar |
| Config activity complexity | Same | Same |

---

## 10. Key Risks and Mitigations

1. **Glance beta risk**: Glance is at 1.1.x (not 1.0 stable). Mitigation: Pin to a specific version in Gradle. The API is stable enough for production use.

2. **Widget not on any home screen**: `updateAll()` will be a no-op if no widget is placed. Always wrap in try/catch.

3. **Battery from frequent updates**: Calling `updateAll()` after every step (~1-5 seconds) is acceptable since it just schedules a Compose recomposition in the widget process. Don't use a foreground Service just for widget updates.

4. **Large execution history**: `repo.recent(5)` is O(1) since it keeps last 100 entries in memory and takes the last 5. `repo.forWorkflow(name).takeLast(5)` is O(n) where n = 100. This is fine.

5. **Widget resize**: Handle `targetCellWidth` and `targetCellHeight` in `appwidget-provider`. Glance `fillMaxSize()` handles resizing automatically.

---

## 11. Estimated Total Effort

- Glance widget with config activity and live progress: **~13-18 hours**
- RemoteViews equivalent: **~10-15 hours** (but significantly worse live-progress UX)

The Glance approach is recommended despite the similar hours because:
- The widget's UI will be significantly more capable (progress bars, lists, Compose theming)
- The codebase stays in one paradigm (Compose everywhere)
- The team will spend less time on XML + RemoteViews quirks and more time on workflow runner logic

---

## Files Referenced from the Project

- `app/src/main/java/com/iris/data/repository/ExecutionHistoryRepository.kt`
- `app/src/main/java/com/iris/domain/model/WorkflowModels.kt`
- `app/build.gradle.kts` (minSdk=26, targetSdk=34, Compose BOM 2024.12.01)
- `ARCHITECTURE.md`
- `docs/MILESTONE_6_STATUS.md`
