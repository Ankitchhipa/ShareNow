package org.sharenow.fileshare.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.ui.theme.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import xherit.composeapp.generated.resources.Res
import xherit.composeapp.generated.resources.logo

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500)
    )
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2000)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
        ) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.clip(CircleShape).size(80.dp),
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Share Now",
                style = MaterialTheme.typography.headlineLarge,
                color = TextDark,
                letterSpacing = 8.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            Text(
                text = "SECURE FILE SHARING",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryBlue,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
