package org.sharenow.fileshare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sharenow.fileshare.platform.getAppVersion
import org.sharenow.fileshare.ui.components.GlassCard
import org.sharenow.fileshare.ui.theme.*
import org.sharenow.getPlatform

@Composable
fun ProfileScreen(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = TextDark,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Device Profile Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = PrimaryBlue,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = deviceName,
                style = MaterialTheme.typography.headlineSmall,
                color = TextDark,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Active Device",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryBlue
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Device Info Section
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "DEVICE DETAILS",
                style = MaterialTheme.typography.labelMedium,
                color = TextGray,
                letterSpacing = 2.sp
            )
            
            GlassCard {
                SettingsItem(
                    icon = Icons.Default.PhoneAndroid,
                    label = "Device Name",
                    value = deviceName,
                    iconColor = PrimaryBlue
                )
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = BackgroundLight)
                SettingsItem(
                    icon = Icons.Default.Info,
                    label = "Model",
                    value = org.sharenow.fileshare.platform.currentDeviceName(),
                    iconColor = SecondaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // App Settings Sections
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "APP SETTINGS",
                style = MaterialTheme.typography.labelMedium,
                color = TextGray,
                letterSpacing = 2.sp
            )

            GlassCard {
                SettingsItem(icon = Icons.Default.Folder, label = "Download Path", value = "/Download/Share Now")
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = BackgroundLight)
                SettingsItem(icon = Icons.Default.Shield, label = "Privacy & Security")
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = BackgroundLight)
                SettingsItem(icon = Icons.Default.Help, label = "Help & Support")
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = BackgroundLight)
                SettingsItem(icon = Icons.Default.Info, label = "About Share Now", value = getAppVersion())
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector, 
    label: String, 
    value: String? = null,
    iconColor: Color = PrimaryBlue
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, color = TextDark, fontWeight = FontWeight.Medium)
        }
        if (value != null) {
            Text(text = value, color = TextGray, style = MaterialTheme.typography.bodySmall)
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray.copy(alpha = 0.5f))
        }
    }
}
