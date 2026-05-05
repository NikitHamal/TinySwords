package com.tinyswords.app.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

private const val TUTORIAL_PREF_KEY = "tinyswords_tutorial_completed"

data class TutorialStep(
    val icon: String,
    val title: String,
    val body: String
)

val TUTORIAL_STEPS = listOf(
    TutorialStep("⚔️", "Welcome, Commander!", "Tiny Swords is a real-time strategy game. Build a base, train an army, gather resources, and conquer rival realms."),
    TutorialStep("🗺️", "Look Around", "Drag with one finger to pan the camera. Pinch to zoom in and out. Explore your starting area."),
    TutorialStep("👆", "Select Units", "Tap a unit to select it. Drag a box to select multiple units. Your workers gather resources and construct buildings."),
    TutorialStep("🪵", "Gather Resources", "Select workers, then tap trees for wood, gold veins for gold, or animals for food. Resources fuel everything."),
    TutorialStep("🏗️", "Build Structures", "Tap the build button (🏗️) to open the build menu. Place houses for population, barracks for warriors."),
    TutorialStep("🗡️", "Train & Fight", "Select Castle or Barracks, then use action buttons to train units. Tap enemies to attack."),
    TutorialStep("💾", "Save Your Realm", "Your world auto-saves. Use the pause button to save manually or exit. Long-press for right-click actions."),
    TutorialStep("🏰", "You're Ready!", "Scout, expand, and crush your rivals. Good luck, Commander!")
)

fun isTutorialCompleted(context: Context): Boolean {
    val prefs = context.getSharedPreferences("tinyswords", Context.MODE_PRIVATE)
    return prefs.getBoolean(TUTORIAL_PREF_KEY, false)
}

fun markTutorialCompleted(context: Context) {
    context.getSharedPreferences("tinyswords", Context.MODE_PRIVATE)
        .edit().putBoolean(TUTORIAL_PREF_KEY, true).apply()
}

@Composable
fun TutorialOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) }
    val step = TUTORIAL_STEPS[currentStep]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInVertically { it / 4 } + fadeIn() togetherWith
                slideOutVertically { -it / 4 } + fadeOut()
            },
            label = "tutorial_step"
        ) { stepIndex ->
            val s = TUTORIAL_STEPS[stepIndex]
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
                    .padding(bottom = 24.dp)
                    .background(GameColors.Panel.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                    .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(14.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Step dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    for (i in TUTORIAL_STEPS.indices) {
                        val color = when {
                            i < stepIndex -> GameColors.Green
                            i == stepIndex -> GameColors.TextGold
                            else -> Color.White.copy(alpha = 0.12f)
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Text(s.icon, fontSize = 28.sp, textAlign = TextAlign.Center)
                Text(s.title, style = GameTypography.SectionTitle, textAlign = TextAlign.Center)
                Text(
                    s.body,
                    style = GameTypography.Body.copy(color = GameColors.TextSecondary),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stepIndex > 0) {
                        TutorialButton(
                            text = "Back",
                            primary = false,
                            onClick = { currentStep-- },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        TutorialButton(
                            text = "Skip",
                            primary = false,
                            onClick = {
                                markTutorialCompleted(context)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TutorialButton(
                        text = if (stepIndex == TUTORIAL_STEPS.lastIndex) "Start Playing" else "Next",
                        primary = true,
                        onClick = {
                            if (stepIndex == TUTORIAL_STEPS.lastIndex) {
                                markTutorialCompleted(context)
                                onDismiss()
                            } else {
                                currentStep++
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (primary) GameColors.ButtonPressed else GameColors.ButtonNormal.copy(alpha = 0.3f)
    val border = if (primary) GameColors.TextGold.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
    Box(
        modifier = modifier
            .height(42.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.5.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = GameTypography.Button.copy(
                fontSize = 12.sp,
                color = if (primary) GameColors.TextGold else GameColors.TextPrimary
            )
        )
    }
}
