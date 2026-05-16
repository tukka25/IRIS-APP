package com.irisapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisapp.domain.model.PlannedWorkflow
import com.irisapp.platform.inference.InferenceState
import com.irisapp.ui.components.BlobPersona
import com.irisapp.ui.components.BlobState
import com.irisapp.ui.components.GlassmorphicCard
import com.irisapp.ui.components.GradientButton
import com.irisapp.ui.components.LivingInputConsole
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.ElectricCyan
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.ui.theme.GreenSuccess
import com.irisapp.ui.theme.LiquidViolet
import com.irisapp.ui.theme.SurfaceDark
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary
import com.irisapp.ui.theme.VioletAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─── Typography helpers ────────────────────────────────────────────────────
// Roboto Black (900) with tight kerning matches the bold, geometric aesthetic
private val HeadlineWeight = FontWeight.Black
private val SubtitleWeight = FontWeight.SemiBold

// ─── Main screen ──────────────────────────────────────────────────────────

@Composable
fun GenerateTabContent(
    viewModel: WorkflowGenerationViewModel,
    state: WorkflowGenerationUiState
) {
    val isGenerating = state.isBusy
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.dp

    // Model manager sheet
    if (state.showModelManager) {
        ModelManagerSheet(
            state = state,
            onDismiss = { viewModel.toggleModelManager() },
            onDownload = { viewModel.downloadModel(it) },
            onSelect = { viewModel.selectModel(it) }
        )
    }

    // Character translates from upper-center (idle) → dead-center (generating)
    val charTranslateY by animateDpAsState(
        targetValue = if (isGenerating) 0.dp else -(screenHeightDp * 0.19f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "char_y"
    )
    val charScale by animateFloatAsState(
        targetValue = if (isGenerating) 1.48f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "char_scale"
    )

    // Resets whenever a new workflow is generated
    var resultDismissed by remember(state.workflowPreview) { mutableStateOf(false) }

    val blobState = when {
        isGenerating                                   -> BlobState.PROCESSING
        state.inferenceState is InferenceState.Loading -> BlobState.LISTENING
        state.workflowPreview != null && !resultDismissed -> BlobState.DONE
        else                                           -> BlobState.IDLE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // ── Layer 0: Pulsing generation aura ──────────────────────────────
        AnimatedVisibility(
            visible = isGenerating,
            enter = fadeIn(tween(700)),
            exit = fadeOut(tween(500))
        ) {
            GenerationAura(modifier = Modifier.fillMaxSize())
        }

        // ── Layer 1: Character + wordmark (always rendered, position animated)
        val charTranslateYPx = with(density) { charTranslateY.toPx() }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationY = charTranslateYPx },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BlobPersona(
                blobState = blobState,
                size = 160.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = charScale
                    scaleY = charScale
                }
            )

            // "IrisApp" wordmark — exits when generating
            AnimatedVisibility(
                visible = !isGenerating,
                enter = fadeIn(tween(400, delayMillis = 120)),
                exit = fadeOut(tween(180))
            ) {
                Text(
                    text = "IRIS",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = HeadlineWeight,
                        letterSpacing = (-2).sp,
                        fontSize = 34.sp,
                        brush = Brush.horizontalGradient(
                            listOf(ElectricCyan, CyanAccent, VioletAccent)
                        )
                    )
                )
            }

            // "Thinking…" caption — enters when generating
            AnimatedVisibility(
                visible = isGenerating,
                enter = fadeIn(tween(400, delayMillis = 300)),
                exit = fadeOut(tween(150))
            ) {
                Text(
                    text = "THINKING",
                    fontSize = 11.sp,
                    fontWeight = SubtitleWeight,
                    letterSpacing = 4.sp,
                    color = ElectricCyan.copy(alpha = 0.75f)
                )
            }
        }

        // ── Layer 2: Orbiting AI icons (generation only) ──────────────────
        AnimatedVisibility(
            visible = isGenerating,
            enter = fadeIn(tween(700, delayMillis = 450)),
            exit = fadeOut(tween(280))
        ) {
            OrbitingAIIcons(modifier = Modifier.fillMaxSize())
        }

        // ── Layer 3: Model status pill (top-right, idle only) ─────────────
        AnimatedVisibility(
            visible = !isGenerating,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut(tween(200)) + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 12.dp)
        ) {
            ModelStatusPill(state = state, viewModel = viewModel)
        }

        // ── Layer 4: Idle bottom content (input + presets + button) ───────
        AnimatedVisibility(
            visible = !isGenerating,
            enter = fadeIn(tween(480)) + slideInVertically { it / 3 },
            exit = fadeOut(tween(250)) + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (state.inferenceState is InferenceState.MissingModel && state.workflowPreview == null) {
                    MissingModelCard(onOpenManager = { viewModel.toggleModelManager() })
                }
                IdleBottomContent(viewModel = viewModel, state = state)
            }
        }

        // ── Layer 5: Generating status panel ──────────────────────────────
        AnimatedVisibility(
            visible = isGenerating,
            enter = fadeIn(tween(500, delayMillis = 600)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GeneratingStatusPanel(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp)
            )
        }

        // ── Layer 6: Workflow result panel (post-generation) ──────────────
        val hasResult = state.workflowPreview != null && !isGenerating && !resultDismissed
        AnimatedVisibility(
            visible = hasResult,
            enter = fadeIn(tween(500)) + slideInVertically { it },
            exit = fadeOut(tween(280)) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (state.workflowPreview != null) {
                WorkflowResultPanel(
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { resultDismissed = true },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // ── Layer 7: Error banner ──────────────────────────────────────────
        if (state.error != null && !isGenerating) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 220.dp)
            ) {
                ErrorBanner(error = state.error!!)
            }
        }

    }
}

// ─── Generation Aura ──────────────────────────────────────────────────────

@Composable
private fun GenerationAura(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aura")

    val pulse by t.animateFloat(
        initialValue = 0.80f, targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulse"
    )
    val alpha by t.animateFloat(
        initialValue = 0.30f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "alpha"
    )
    val hueShift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = LinearEasing), RepeatMode.Restart
        ), label = "hue"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.40f * pulse

        // Outer magenta ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(LiquidViolet, Color(0xFFFF006E), hueShift).copy(alpha = alpha * 0.40f),
                    lerp(Color(0xFFFF006E), ElectricCyan, hueShift).copy(alpha = alpha * 0.20f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r * 2.0f
            ),
            radius = r * 2.0f,
            center = Offset(cx, cy)
        )
        // Inner cyan core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = alpha * 0.28f),
                    LiquidViolet.copy(alpha = alpha * 0.18f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r * 0.95f
            ),
            radius = r * 0.95f,
            center = Offset(cx, cy)
        )
        // Outer ring stroke (subtle iridescent ring)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = alpha * 0.60f),
                    LiquidViolet.copy(alpha = alpha * 0.40f),
                    Color(0xFFFF006E).copy(alpha = alpha * 0.30f),
                    ElectricCyan.copy(alpha = alpha * 0.60f)
                ),
                center = Offset(cx, cy)
            ),
            radius = r * 1.10f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

// ─── Orbiting AI Icons ────────────────────────────────────────────────────

private data class OrbitSpec(
    val icon: ImageVector,
    val speedMs: Int,
    val radiusMult: Float,
    val initialAngle: Float   // radians
)

private val orbitSpecs = listOf(
    OrbitSpec(Icons.Filled.AutoAwesome, 5200,  1.00f, 0.00f),
    OrbitSpec(Icons.Filled.Bolt,        7400,  0.82f, 1.26f),  // 2π/5
    OrbitSpec(Icons.Filled.Code,        6100,  1.12f, 2.51f),  // 2π*2/5
    OrbitSpec(Icons.Filled.Psychology,  8600,  0.88f, 3.77f),  // 2π*3/5
    OrbitSpec(Icons.Filled.Settings,    5800,  0.96f, 5.03f)   // 2π*4/5
)

@Composable
private fun OrbitingAIIcons(modifier: Modifier = Modifier) {
    // Box is centered; each icon uses Modifier.offset from that center
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        orbitSpecs.forEach { spec -> SingleOrbitingIcon(spec = spec) }
    }
}

@Composable
private fun SingleOrbitingIcon(spec: OrbitSpec) {
    val t = rememberInfiniteTransition(label = "orbit_${spec.speedMs}")

    val angle by t.animateFloat(
        initialValue = spec.initialAngle,
        targetValue = spec.initialAngle + (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(spec.speedMs, easing = LinearEasing), RepeatMode.Restart
        ),
        label = "angle_${spec.speedMs}"
    )

    val orbitRx = 130f * spec.radiusMult  // dp — horizontal radius
    val orbitRy = 52f  * spec.radiusMult  // dp — compressed Y → 3D ellipse

    val xDp = (cos(angle.toDouble()) * orbitRx).toFloat().dp
    val yDp = (sin(angle.toDouble()) * orbitRy).toFloat().dp

    // sin > 0 = "front" of ellipse → brighter, larger (depth simulation)
    val depth = ((sin(angle.toDouble()) + 1.0) / 2.0).toFloat()
    val scale = 0.48f + 0.52f * depth
    val alpha = 0.28f + 0.72f * depth
    val tint  = lerp(LiquidViolet, ElectricCyan, depth).copy(alpha = alpha)

    Icon(
        imageVector = spec.icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}

// ─── Model Status Pill ────────────────────────────────────────────────────

@Composable
private fun ModelStatusPill(
    state: WorkflowGenerationUiState,
    viewModel: WorkflowGenerationViewModel
) {
    val isLoading = state.inferenceState is InferenceState.Loading
    val isReady   = state.inferenceState is InferenceState.Ready
    val downloadState = state.inferenceState as? InferenceState.Downloading

    val dotColor = when {
        isReady   -> GreenSuccess
        isLoading -> CyanAccent
        downloadState != null -> VioletAccent
        else      -> TextSecondary.copy(alpha = 0.5f)
    }
    val label = when {
        isReady   -> "Online"
        isLoading -> "Loading"
        downloadState != null -> "Downloading ${"%.2f".format(downloadState.progress * 100f)}%"
        state.inferenceState is InferenceState.MissingModel -> "No Model"
        else      -> "Offline"
    }

    val t = rememberInfiniteTransition(label = "dot")
    val dotAlpha by t.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF080810).copy(alpha = 0.90f))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable { viewModel.toggleModelManager() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (isLoading || downloadState != null) dotAlpha else 1f))
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = SubtitleWeight,
                letterSpacing = 0.3.sp,
                color = TextPrimary
            )
        }
    }
}

// ─── Model Manager Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelManagerSheet(
    state: WorkflowGenerationUiState,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Model Management",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "IrisApp runs completely offline. Download a Gemma 4 model to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            state.availableModels.forEach { model ->
                ModelItemRow(
                    model = model,
                    isBusy = state.isBusy,
                    onDownload = { onDownload(model.id) },
                    onSelect = { onSelect(model.id) },
                    inferenceState = state.inferenceState
                )
            }
        }
    }
}

@Composable
private fun ModelItemRow(
    model: ModelItemUiState,
    isBusy: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    inferenceState: InferenceState
) {
    val downloadState = inferenceState as? InferenceState.Downloading
    val isThisDownloading = downloadState != null && downloadState.modelId == model.fileName
    val isAnyDownloading = downloadState != null
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (model.isActive) CyanAccent else if (isThisDownloading) VioletAccent else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (model.isActive) CyanAccent else if (isThisDownloading) VioletAccent else TextPrimary
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                
                if (isThisDownloading && downloadState != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Downloading: ${"%.2f".format(downloadState.progress * 100f)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = VioletAccent
                    )
                    Text(
                        text = "${formatFileSize(downloadState.downloadedBytes)} / ${formatFileSize(downloadState.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Speed: ${formatSpeed(downloadState.speedBytesPerSecond)} • ETA: ${formatDuration(downloadState.etaSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = model.sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = VioletAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (model.isDownloaded) {
                GradientButton(
                    text = if (model.isActive) "Active" else "Use",
                    enabled = !isBusy && !model.isActive && !isAnyDownloading,
                    onClick = onSelect
                )
            } else {
                OutlinedButton(
                    onClick = onDownload,
                    enabled = !isBusy && !isAnyDownloading,
                    border = BorderStroke(1.dp, if (!isBusy && !isAnyDownloading) CyanAccent.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                ) {
                    Text(if (isThisDownloading) "Wait..." else "Download")
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
    val digitGroups = (Math.log10(bytesPerSecond.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(bytesPerSecond / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "calculating..."
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
private fun MissingModelCard(
    onOpenManager: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        glowColor = VioletAccent
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = VioletAccent,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Intelligence Required",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = "IrisApp needs a Gemma 4 model to understand your requests. Download one to enable AI generation.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            GradientButton(
                text = "Setup Model",
                onClick = onOpenManager,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Idle Bottom Content ──────────────────────────────────────────────────

@Composable
private fun IdleBottomContent(
    viewModel: WorkflowGenerationViewModel,
    state: WorkflowGenerationUiState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Arc-arranged workflow preset chips
        if (state.savedWorkflows.isNotEmpty()) {
            ArcWorkflowChips(
                workflows = state.savedWorkflows,
                onSelect = { wf -> viewModel.loadWorkflowDetail(wf.name) }
            )
        }

        // Living input console (Gemini-style glowing pill)
        LivingInputConsole(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            isGenerating = state.isBusy,
            onSendClick = viewModel::generate,
            sendEnabled = state.canGenerate
        )

        // Generate button
        GradientButton(
            text = "Generate Workflow",
            onClick = viewModel::generate,
            enabled = state.canGenerate
        )
    }
}

// ─── Arc Workflow Chips ───────────────────────────────────────────────────

@Composable
private fun ArcWorkflowChips(
    workflows: List<PlannedWorkflow>,
    onSelect: (PlannedWorkflow) -> Unit
) {
    val count = minOf(workflows.size, 5)
    if (count == 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        workflows.take(count).forEach { wf ->
            ArcChip(
                name = wf.name,
                stepCount = wf.actions.size,
                onClick = { onSelect(wf) }
            )
        }
    }
}

@Composable
private fun ArcChip(
    name: String,
    stepCount: Int,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chip_scale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0D0D1A).copy(alpha = 0.92f),
                        SurfaceDark.copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                0.5.dp,
                Brush.horizontalGradient(
                    listOf(ElectricCyan.copy(0.25f), LiquidViolet.copy(0.15f))
                ),
                RoundedCornerShape(14.dp)
            )
            .clickable {
                pressed = true
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = SubtitleWeight,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            )
            Text(
                text = "$stepCount steps",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = CyanAccent.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            )
        }
    }
}

// ─── Generating Status Panel ──────────────────────────────────────────────

@Composable
private fun GeneratingStatusPanel(
    state: WorkflowGenerationUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shimmer progress bar
        GenerateShimmerBar(modifier = Modifier.fillMaxWidth())

        // Stage label + elapsed time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.stage.ifBlank { "Processing…" },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ElectricCyan.copy(alpha = 0.90f),
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "${state.elapsedSeconds}s",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = LiquidViolet.copy(alpha = 0.80f),
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        // Stage timeline dots (compact)
        if (state.stageTimeline.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.stageTimeline.forEach { stage ->
                    val dotColor = when (stage.status) {
                        StageStatus.Done    -> GreenSuccess
                        StageStatus.Running -> CyanAccent
                        StageStatus.Pending -> TextSecondary.copy(alpha = 0.30f)
                    }
                    val dotSize = when (stage.status) {
                        StageStatus.Running -> 10.dp
                        StageStatus.Done    -> 7.dp
                        else               -> 5.dp
                    }
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerateShimmerBar(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val offset by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = LinearEasing), RepeatMode.Restart
        ), label = "offset"
    )
    Canvas(modifier = modifier.height(3.dp)) {
        drawRoundRect(color = GlassBorder, cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    ElectricCyan.copy(alpha = 0.95f),
                    LiquidViolet.copy(alpha = 0.75f),
                    Color.Transparent
                ),
                start = Offset(size.width * offset, 0f),
                end = Offset(size.width * (offset + 0.45f), 0f)
            ),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

// ─── Workflow Result Panel ─────────────────────────────────────────────────

@Composable
private fun WorkflowResultPanel(
    state: WorkflowGenerationUiState,
    viewModel: WorkflowGenerationViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workflow = state.workflowPreview ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0C18), Color(0xFF080810))
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(ElectricCyan.copy(0.35f), LiquidViolet.copy(0.25f))
                    )
                ),
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 3.dp)
                .clip(CircleShape)
                .background(GlassBorder)
                .align(Alignment.CenterHorizontally)
        )

        // Workflow header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = HeadlineWeight,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                )
                if (workflow.summary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = workflow.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenSuccess.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${workflow.actions.size} actions",
                        fontSize = 11.sp,
                        fontWeight = SubtitleWeight,
                        color = GreenSuccess
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Action / Save / Run buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::saveWorkflow,
                enabled = !state.saved,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                border = BorderStroke(
                    1.dp, CyanAccent.copy(alpha = if (state.saved) 0.25f else 0.65f)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (state.saved) "Saved ✓" else "Save",
                    fontWeight = SubtitleWeight
                )
            }
            Button(
                onClick = viewModel::runWorkflow,
                enabled = state.isValid && !state.isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanAccent,
                    contentColor = Color.Black
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Run Now", fontWeight = HeadlineWeight)
            }
        }

        // Validation errors
        if (state.validationErrors.isNotEmpty()) {
            GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(10.dp),
                    text = state.validationErrors.joinToString("\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        // Execution results (last run)
        if (state.runResults.isNotEmpty()) {
            HorizontalDivider(color = GlassBorder)
            Text(
                "Last run",
                style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
            )
            state.runResults.take(6).forEach { result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (result.success) "✓" else "✗",
                        color = if (result.success) GreenSuccess else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.stepId,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (result.message.isNotBlank()) {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── Error Banner ──────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(error: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        )
    }
}
