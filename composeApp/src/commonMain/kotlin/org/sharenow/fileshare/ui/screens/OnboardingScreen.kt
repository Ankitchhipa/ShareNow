package org.sharenow.fileshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.ui.components.NeonButton
import org.sharenow.fileshare.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: String,
    val color: Color
)

val pages = listOf(
    OnboardingPage(
        "Fast Sharing",
        "Experience lightning fast file transfers with our optimized engine.",
        "⚡",
        PrimaryBlue
    ),
    OnboardingPage(
        "No Internet Needed",
        "Share files anywhere, anytime without using any mobile data.",
        "🌐",
        SecondaryBlue
    ),
    OnboardingPage(
        "Secure Transfer",
        "Your privacy is our priority. Encrypted and safe transfers always.",
        "🛡️",
        AccentGreen
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinished) {
                Text("Skip", color = TextGray)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { index ->
            OnboardingContent(pages[index])
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Pager Indicator
        Row(
            modifier = Modifier.height(48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pages.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) pages[iteration].color else TextGray.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        NeonButton(
            text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Next",
            onClick = {
                if (pagerState.currentPage < pages.size - 1) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onFinished()
                }
            },
            color = pages[pagerState.currentPage].color
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun OnboardingContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = CircleShape,
            color = page.color.copy(alpha = 0.1f),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = page.icon,
                    fontSize = 80.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextDark,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
