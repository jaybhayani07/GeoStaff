package com.example.geostaff.userinterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// 1. DATA CLASS (This was likely missing or causing the error)
data class AttendanceLog(
    val date: String,
    val time: String,
    val office: String
)

@Composable
fun HistoryScreen() {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var historyList by remember { mutableStateOf<List<AttendanceLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            // 2. QUERY WITHOUT SORTING (Fixes "Missing Index" error)
            firestore.collection("attendance_logs")
                .whereEqualTo("user_id", userId)
                .addSnapshotListener { value, error ->
                    isLoading = false

                    if (value != null && !value.isEmpty) {
                        val list = value.documents.map { doc ->
                            val fullTime = doc.getString("readable_time") ?: "Unknown"
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val parts = fullTime.split(" ")

                            // Map to a pair (Timestamp, Data) so we can sort
                            Pair(timestamp, AttendanceLog(
                                date = parts.getOrElse(0) { "Unknown" },
                                time = parts.getOrElse(1) { "--:--" },
                                office = doc.getString("office_name") ?: "Head Office"
                            ))
                        }

                        // 3. SORT MANUALLY (Newest First)
                        historyList = list.sortedByDescending { it.first }.map { it.second }

                    } else {
                        historyList = emptyList()
                    }
                }
        } else {
            isLoading = false
        }
    }

    // UI LAYOUT
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "My Attendance",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No attendance history found.", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(historyList) { log ->
                    HistoryItem(log)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(log: AttendanceLog) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 0.dp), // Added vertical spacing
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT SIDE: Date & Office (Takes up available space)
            Column(modifier = Modifier.weight(1f)) {
                // Date with a small Calendar Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Office Name (Indented slightly)
                Text(
                    text = log.office,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 22.dp)
                )
            }

            // RIGHT SIDE: Time Bubble (Fixed size, won't overlap)
            Surface(
                color = Color(0xFFE3F2FD), // Light Blue Background
                shape = RoundedCornerShape(50), // Pill Shape
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = log.time,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2), // Dark Blue Text
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}