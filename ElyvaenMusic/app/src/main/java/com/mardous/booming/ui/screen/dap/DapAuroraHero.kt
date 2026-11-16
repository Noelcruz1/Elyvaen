package com.mardous.booming.ui.screen.dap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mardous.booming.ui.component.compose.AnimatedEqBars
import com.mardous.booming.ui.component.compose.decoration.animatedGradient
import com.mardous.booming.ui.theme.AuroraGradient

@Composable
fun DapAuroraHero(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    showEq: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .animatedGradient(AuroraGradient, isPlaying)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CYPFQ DAP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "AURORA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = Color(0xFF5BE8D5)
                )
            }
            if (showEq) {
                AnimatedEqBars(
                    color = Color.White,
                    isPlaying = isPlaying,
                    barCount = 5,
                    barWidth = 3.dp,
                    gap = 3.dp,
                    modifier = Modifier.size(44.dp, 30.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        DapStatusStrip(modifier = Modifier.fillMaxWidth())
    }
}
