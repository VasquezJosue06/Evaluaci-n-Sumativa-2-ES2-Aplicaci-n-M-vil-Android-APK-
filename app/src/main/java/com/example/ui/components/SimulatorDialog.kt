package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.AlarmViewModel
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.WarningAmber

@Composable
fun SimulatorDialog(
    viewModel: AlarmViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SafetyOrange.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = SafetyOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simulador de Pruebas",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Inyecte eventos simulados para evaluar la máquina de estados y la respuesta visual/sonora sin requerir hardware físico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botón: Disparar Movimiento PIR
                Button(
                    onClick = { viewModel.simTriggerPir() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_pir_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disparar Movimiento PIR", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón: Abrir/Cerrar Puerta
                Button(
                    onClick = { viewModel.simToggleDoor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_door_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.MeetingRoom, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alternar Puerta (Abrir/Cerrar)", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón: Simular Fallo de Sensor
                OutlinedButton(
                    onClick = { viewModel.simTriggerFault() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_fault_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simular Fallo de Sensor", color = WarningAmber, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón: Simular Pérdida de Conexión
                OutlinedButton(
                    onClick = { viewModel.simDisconnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_disconnect_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PowerOff, contentDescription = null, tint = AlarmRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simular Pérdida de Enlace", color = AlarmRed, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Nota: Todos los eventos originados aquí son rotulados como SIMULADOS en el historial de eventos para auditoría estricta.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
