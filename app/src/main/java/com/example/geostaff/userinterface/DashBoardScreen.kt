package com.example.geostaff.userinterface

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    // STATES
    var isAttendanceMarked by remember { mutableStateOf(false) } // Default false, but we check DB immediately
    var isLoadingStatus by remember { mutableStateOf(true) }     // Loading indicator for the button

    var statusText by remember { mutableStateOf("Checking Location...") }
    var statusColor by remember { mutableStateOf(Color.Gray) }
    var distanceText by remember { mutableStateOf("Waiting for GPS...") }
    var isInside by remember { mutableStateOf(false) }

    // OFFICE DATA
    var officeLat by remember { mutableStateOf(0.0) }
    var officeLng by remember { mutableStateOf(0.0) }
    var officeRadius by remember { mutableStateOf(100) }

    // 1. CHECK DATABASE: Did I mark attendance today?
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val todayDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            // Query logs for TODAY
            firestore.collection("attendance_logs")
                .whereEqualTo("user_id", userId)
                .whereEqualTo("date_only", todayDate) // We will save this field now
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        isAttendanceMarked = true // Found a log for today! Disable button.
                    }
                    isLoadingStatus = false
                }
                .addOnFailureListener { isLoadingStatus = false }
        }
    }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).update("is_logged_in", true)
        }
    }

    // 2. Fetch Office Settings
    LaunchedEffect(Unit) {
        firestore.collection("offices").document("Head Office").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    officeLat = document.getDouble("lat") ?: 0.0
                    officeLng = document.getDouble("lng") ?: 0.0
                    officeRadius = document.getLong("radius")?.toInt() ?: 100
                }
            }
    }

    // 3. Location Logic
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(officeLat, officeLng) {
        while (true) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            val userId = auth.currentUser?.uid
                            if (userId != null) {
                                firestore.collection("users").document(userId).update(
                                    mapOf("last_lat" to location.latitude, "last_lng" to location.longitude)
                                )
                            }
                            if (officeLat != 0.0) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(location.latitude, location.longitude, officeLat, officeLng, results)
                                val distanceInMeters = results[0]

                                if (distanceInMeters <= officeRadius) {
                                    statusText = "You are in Office"
                                    statusColor = Color(0xFF2E7D32)
                                    isInside = true
                                } else {
                                    statusText = "You are Outside Office"
                                    statusColor = Color(0xFFD32F2F)
                                    isInside = false
                                }
                                distanceText = "${distanceInMeters.toInt()}m from zone (${officeRadius}m allowed)"
                            } else {
                                statusText = "No Office Assigned"
                            }
                        }
                    }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            kotlinx.coroutines.delay(3000)
        }
    }

    // UI LAYOUT
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("DASHBOARD", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(30.dp))

        // STATUS CARD
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(80.dp).background(statusColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocationOn, null, tint = statusColor, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(statusText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = statusColor)
                Text(distanceText, fontSize = 14.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ATTENDANCE BUTTON
        if (isLoadingStatus) {
            CircularProgressIndicator()
        } else {
            Button(
                enabled = isInside && !isAttendanceMarked, // DISABLED if already marked today
                onClick = {
                    val userId = auth.currentUser?.uid
                    if (userId != null && isInside) {
                        isAttendanceMarked = true // Disable immediately

                        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        val todayDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) // Use this for checking later
                        val currentTime = sdf.format(Date())

                        val attendanceData = hashMapOf(
                            "user_id" to userId,
                            "timestamp" to System.currentTimeMillis(),
                            "readable_time" to currentTime,
                            "date_only" to todayDate, // <--- CRITICAL: Save just the date to check against
                            "office_name" to "Head Office",
                            "status" to "Present"
                        )

                        firestore.collection("attendance_logs").add(attendanceData)
                            .addOnSuccessListener {
                                android.widget.Toast.makeText(context, "Attendance Marked!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInside && !isAttendanceMarked) Color(0xFF1976D2) else Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (isAttendanceMarked) "ATTENDANCE ALREADY MARKED" else "MARK ATTENDANCE")
            }
        }
    }
}