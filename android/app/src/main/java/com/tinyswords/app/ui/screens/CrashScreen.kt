package com.tinyswords.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.MainActivity
import com.tinyswords.app.data.CrashHandler
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

@Composable
fun CrashScreen(crashTrace: String) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GameColors.Background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "CRASH REPORT",
                style = GameTypography.Title.copy(fontSize = 22.sp),
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Text(
                text = "The app encountered an unexpected error. Copy the log below and share it with the developer.",
                style = GameTypography.Small.copy(fontSize = 10.sp, color = GameColors.TextSecondary),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(GameColors.Panel.copy(alpha = 0.85f))
                    .border(1.dp, GameColors.PanelBorder)
                    .padding(8.dp)
            ) {
                Text(
                    text = crashTrace,
                    style = GameTypography.Body.copy(
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GameColors.TextPrimary
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CrashButton("COPY LOG", modifier = Modifier.weight(1f)) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", crashTrace))
                }
                CrashButton("RESTART APP", modifier = Modifier.weight(1f)) {
                    CrashHandler.clearCrash(context)
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    context.startActivity(intent)
                    Process.killProcess(Process.myPid())
                }
            }
        }
    }
}

@Composable
private fun CrashButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(GameColors.ButtonNormal)
            .border(1.dp, GameColors.ButtonBorder)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = GameTypography.Button)
    }
}