package org.sharenow.fileshare.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sharenow.fileshare.ui.theme.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.model.PermissionRequestPurpose
import androidx.compose.foundation.shape.CircleShape

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
) {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            PrimaryBlue.copy(alpha = 0.05f),
            PrimaryBlue.copy(alpha = 0.15f),
            PrimaryBlue.copy(alpha = 0.05f),
        ),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(x = translateAnim, y = translateAnim)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun FuturisticBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundLight, Color(0xFF020617))))
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = SurfaceWhite.copy(alpha = 0.7f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            content()
        }
    }
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue,
    enabled: Boolean = true
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(60.dp)
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = color.copy(alpha = 0.5f),
                spotColor = color.copy(alpha = 0.5f)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun NeonOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PrimaryBlue
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        border = BorderStroke(2.dp, color),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PermissionBottomSheet(
    purpose: PermissionRequestPurpose?,
    onGrant: () -> Unit,
    onExit: () -> Unit
) {
    val title = when (purpose) {
        PermissionRequestPurpose.Send -> "Allow Send Permissions"
        PermissionRequestPurpose.Receive -> "Allow Receive Permissions"
        else -> "Permissions Required"
    }
    val description = when (purpose) {
        PermissionRequestPurpose.Send -> "To send files, Share Now needs media or file access for the picker and nearby network access to create the connection."
        PermissionRequestPurpose.Receive -> "To receive files, Share Now needs camera access to scan the QR code and nearby network access to connect quickly."
        else -> "Share Now only asks for the permissions needed for the action you choose."
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                NeonButton(
                    text = "GRANT PERMISSIONS",
                    onClick = onGrant
                )
                TextButton(onClick = onExit) {
                    Text("Exit App", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun FuturisticBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = SurfaceWhite.copy(alpha = 0.8f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shadowElevation = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.Home, "Home", selectedTab == 0) { onTabSelected(0) }
            BottomNavItem(Icons.Default.History, "History", selectedTab == 1) { onTabSelected(1) }
            BottomNavItem(Icons.Default.Person, "Profile", selectedTab == 2) { onTabSelected(2) }
        }
    }
}

@Composable
fun BottomNavItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) PrimaryBlue else TextGray,
            modifier = Modifier.size(24.dp)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .background(PrimaryBlue, CircleShape)
            )
        }
    }
}
