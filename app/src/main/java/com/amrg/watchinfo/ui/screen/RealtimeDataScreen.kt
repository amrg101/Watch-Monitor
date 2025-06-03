package com.amrg.watchinfo.ui.screen

// Import Wear Compose Material components
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VideoStable
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.amrg.watchinfo.R
import com.amrg.watchinfo.model.GpsPoint
import com.amrg.watchinfo.model.IncidentData
import com.amrg.watchinfo.ui.theme.AppDarkColors
import com.amrg.watchinfo.ui.theme.WatchInfoTheme

// Define some hardcoded dark theme friendly colors


@Composable
fun RealtimeDataScreen(
    isContinuousServiceActive: Boolean,
    heartRate: Int?,
    steps: Int?,
    battery: Int?,
    onToggleContinuousMonitoring: () -> Unit,
    isIncidentRecordingActive: Boolean,
    lastIncidentData: IncidentData?,
    onToggleIncidentRecording: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 4.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.Top),
        contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
    ) {
        // --- Continuous Monitoring Section ---
        item {
            ListHeader(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    "Continuous Monitoring",
                    style = MaterialTheme.typography.caption1.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppDarkColors.textSecondary
                    )
                )
            }
        }
        item {
            StatusChip(
                label = "Service", value = if (isContinuousServiceActive) "Active" else "Inactive",
                icon = if (isContinuousServiceActive) Icons.Filled.NotificationsActive else Icons.Filled.RadioButtonUnchecked,
                valueColor = if (isContinuousServiceActive) AppDarkColors.statusGreen else AppDarkColors.statusOrange
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactDataChip(
                    label = "HR",
                    value = heartRate?.toString() ?: "--",
                    unit = "bpm",
                    icon = Icons.Filled.Favorite,
                    iconTint = Color(0xFFE91E63)
                )
                CompactDataChip(
                    label = "Steps",
                    value = steps?.toString() ?: "--",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    iconTint = Color(0xFF2196F3)
                )
            }
        }
        item {
            StatusChip(
                label = "Battery", value = battery?.toString()?.plus("%") ?: "--",
                icon = Icons.Filled.BatteryStd,
                valueColor = if ((battery
                        ?: 100) < 20
                ) AppDarkColors.statusRed else AppDarkColors.textPrimary
            )
        }
        item {
            Button(
                onClick = onToggleContinuousMonitoring,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .padding(vertical = 3.dp)
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isContinuousServiceActive) AppDarkColors.surfaceSlightlyLighter else AppDarkColors.primaryAction,
                    contentColor = if (isContinuousServiceActive) AppDarkColors.primaryAction else AppDarkColors.primaryActionContent
                )
            ) {
                Text(
                    if (isContinuousServiceActive) stringResource(R.string.wear_stop_monitoring) else stringResource(
                        R.string.wear_start_monitoring
                    ),
                    style = MaterialTheme.typography.button
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 5.dp, horizontal = 20.dp),
                thickness = 0.5.dp,
                color = AppDarkColors.textTertiary.copy(alpha = 0.5f)
            )
        }

        // --- Incident Recording Section ---
        item {
            ListHeader {
                Text(
                    "Incident Event",
                    style = MaterialTheme.typography.caption1.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppDarkColors.textSecondary
                    )
                )
            }
        }
        item {
            StatusChip(
                label = "Recording",
                value = if (isIncidentRecordingActive) "In Progress" else "Idle",
                icon = if (isIncidentRecordingActive) Icons.Filled.VideoStable else Icons.Filled.RadioButtonUnchecked,
                valueColor = if (isIncidentRecordingActive) AppDarkColors.statusGreen else AppDarkColors.textSecondary
            )
        }
        item {
            Button(
                onClick = onToggleIncidentRecording,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .padding(vertical = 3.dp)
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isIncidentRecordingActive) AppDarkColors.statusRed else AppDarkColors.secondaryAction,
                    contentColor = if (isIncidentRecordingActive) Color.Black else AppDarkColors.secondaryActionContent
                )
            ) {
                Text(
                    if (isIncidentRecordingActive) stringResource(R.string.wear_stop_incident) else stringResource(
                        R.string.wear_start_incident
                    ),
                    style = MaterialTheme.typography.button
                )
            }
        }

        // --- Last Incident Details Section ---
        if (lastIncidentData != null) {
            item { Spacer(modifier = Modifier.height(5.dp)) }
            item {
                ListHeader(modifier = Modifier.padding(top = 0.dp)) {
                    Text(
                        "Last Incident Data",
                        style = MaterialTheme.typography.caption2.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppDarkColors.textSecondary
                        )
                    )
                }
            }
            item { DetailRow(label = "Time:", value = lastIncidentData.timestamp) }
            item {
                DetailRow(
                    label = "Start:",
                    value = formatLocation(lastIncidentData.startPoint)
                )
            }
            item { DetailRow(label = "End:", value = formatLocation(lastIncidentData.endPoint)) }
            item {
                DetailRow(
                    label = "Accel Samples:",
                    value = lastIncidentData.accelerometerData.size.toString()
                )
            }
            if (lastIncidentData.accelerometerData.isNotEmpty()) {
                val firstAccel = lastIncidentData.accelerometerData.first()
                item {
                    DetailRow(
                        label = "Accel #1:",
                        value = "X:%.1f Y:%.1f Z:%.1f".format(
                            firstAccel.x,
                            firstAccel.y,
                            firstAccel.z
                        )
                    )
                }
            }
            if (lastIncidentData.source == "wear_os_error") {
                item {
                    DetailRow(
                        label = "Status:",
                        value = "Error during recording",
                        valueColor = AppDarkColors.statusRed
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    label: String, value: String, icon: ImageVector? = null,
    valueColor: Color = AppDarkColors.textPrimary
) {
    Chip(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp),
        onClick = { /* No action */ },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.caption2,
                maxLines = 1,
                color = AppDarkColors.textSecondary
            )
        },
        secondaryLabel = {
            Text(
                value,
                style = MaterialTheme.typography.body2.copy(
                    color = valueColor,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        },
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = label,
                    modifier = Modifier.size(ChipDefaults.SmallIconSize),
                    tint = AppDarkColors.iconTint
                )
            }
        },
        colors = ChipDefaults.secondaryChipColors(backgroundColor = AppDarkColors.surfaceAlpha)
    )
}

@Composable
fun CompactDataChip(
    label: String, value: String, unit: String? = null, icon: ImageVector? = null,
    iconTint: Color = AppDarkColors.iconTint
) {
    CompactChip(
        onClick = { /* No action */ },
        modifier = Modifier.padding(horizontal = 1.dp),
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = label,
                    modifier = Modifier.size(ChipDefaults.SmallIconSize),
                    tint = iconTint
                )
            }
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = AppDarkColors.textPrimary
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(1.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.caption2,
                        color = AppDarkColors.textSecondary
                    )
                }
            }
        },
        colors = ChipDefaults.chipColors(backgroundColor = AppDarkColors.surfaceAlpha)
    )
}

@Composable
fun DetailRow(
    label: String, value: String,
    valueColor: Color = AppDarkColors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.40f),
            color = AppDarkColors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.caption1.copy(
                color = valueColor,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.60f)
        )
    }
}

@SuppressLint("DefaultLocale")
fun formatLocation(point: GpsPoint?): String {
    return if (point != null) "Lat: ${
        String.format(
            "%.2f",
            point.latitude
        )
    }, Lon: ${String.format("%.2f", point.longitude)}" else "--"
}

// --- Preview ---
@Preview(
    device = WearDevices.LARGE_ROUND,
    showSystemUi = true,
    backgroundColor = 0xFF121212,
    showBackground = true
)
@Composable
fun RealtimeDataScreenPreview_Idle_DarkHardcoded() {
    WatchInfoTheme(darkTheme = true) { // Your M2 Wear Theme
        RealtimeDataScreen(
            isContinuousServiceActive = false,
            heartRate = null,
            steps = null,
            battery = 75,
            onToggleContinuousMonitoring = {},
            isIncidentRecordingActive = false,
            lastIncidentData = null,
            onToggleIncidentRecording = {}
        )
    }
}

@Preview(
    device = WearDevices.LARGE_ROUND,
    showSystemUi = true,
    backgroundColor = 0xFF121212,
    showBackground = true
)
@Composable
fun RealtimeDataScreenPreview_Active_DarkHardcoded() {
    WatchInfoTheme(darkTheme = true) {
        val sampleIncident = IncidentData(
            "d",
            "w",
            GpsPoint(1.0, 1.0),
            GpsPoint(1.1, 1.1),
            List(5) { com.amrg.watchinfo.model.AccelerometerSample(0f, 0f, 0f, 0L) },
            "2023-01-01T12:00:00.000Z",
            "wear_os"
        )
        RealtimeDataScreen(
            isContinuousServiceActive = true,
            heartRate = 72,
            steps = 34,
            battery = 15, // Low battery test
            onToggleContinuousMonitoring = {},
            isIncidentRecordingActive = true,
            lastIncidentData = sampleIncident,
            onToggleIncidentRecording = {}
        )
    }
}