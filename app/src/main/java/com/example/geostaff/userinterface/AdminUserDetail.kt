package com.example.geostaff.userinterface

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

// Data Model
data class AdminTaskItem(
    val id: String,
    val title: String,
    val desc: String,
    val status: String
)

@Composable
fun AdminUserDetail(userId: String, onBack: () -> Unit) {
    val firestore = FirebaseFirestore.getInstance()

    // 1. GRAB CONTEXT HERE (Safe to use in buttons later)
    val context = LocalContext.current

    var attendanceLogs by remember { mutableStateOf<List<AttendanceLog>>(emptyList()) }
    var taskList by remember { mutableStateOf<List<AdminTaskItem>>(emptyList()) }
    var email by remember { mutableStateOf("Loading...") }
    var selectedTab by remember { mutableStateOf(0) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    var taskDesc by remember { mutableStateOf("") }
    var isSavingTask by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        firestore.collection("users").document(userId).get().addOnSuccessListener { email = it.getString("email") ?: "Unknown User" }

        firestore.collection("attendance_logs").whereEqualTo("user_id", userId).addSnapshotListener { value, _ ->
            if (value != null) {
                val list = value.documents.map { doc ->
                    val fullTime = doc.getString("readable_time") ?: "Unknown"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val parts = fullTime.split(" ")
                    Pair(timestamp, AttendanceLog(parts.getOrElse(0) { "Unknown" }, parts.getOrElse(1) { "--:--" }, doc.getString("office_name") ?: "Unknown"))
                }
                attendanceLogs = list.sortedByDescending { it.first }.map { it.second }
            }
        }

        firestore.collection("tasks").whereEqualTo("assigned_to", userId).addSnapshotListener { value, _ ->
            if (value != null) {
                val list = value.documents.map { doc ->
                    AdminTaskItem(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        desc = doc.getString("description") ?: "",
                        status = doc.getString("status") ?: "Pending"
                    )
                }
                val statusOrder = mapOf("Pending" to 0, "In Progress" to 1, "Completed" to 2)
                taskList = list.sortedBy { statusOrder[it.status] ?: 3 }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 1) {
                ExtendedFloatingActionButton(onClick = { showTaskDialog = true }, containerColor = Color(0xFF1976D2), contentColor = Color.White) {
                    Icon(Icons.Default.Add, null); Spacer(modifier = Modifier.width(8.dp)); Text("ASSIGN TASK")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                Column { Text("Employee Details", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(email, fontSize = 14.sp, color = Color.Gray) }
            }
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("ATTENDANCE") }, icon = { Icon(Icons.Default.DateRange, null) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("TASKS") }, icon = { Icon(Icons.Default.List, null) })
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (selectedTab == 0) {
                // FIXED: Better Layout for Attendance Logs (Prevent text overlapping)
                if (attendanceLogs.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No attendance records.", color = Color.Gray) }
                else LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(attendanceLogs) { log ->
                        // Use a custom row layout here if needed, or your existing HistoryItem
                        HistoryItem(log)
                    }
                }
            } else {
                if (taskList.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No tasks assigned yet.", color = Color.Gray) }
                else LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) { items(taskList) { task -> AdminTaskCard(task) } }
            }
        }
    }

    if (showTaskDialog) {
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text("Assign New Task") },
            text = {
                Column {
                    OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it }, label = { Text("Task Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = taskDesc, onValueChange = { taskDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = {
                Button(enabled = !isSavingTask, onClick = {
                    if (taskTitle.isNotEmpty()) {
                        isSavingTask = true

                        // AI Logic
                        val lowerDesc = taskDesc.lowercase()
                        val isUrgent = listOf("urgent", "asap", "emergency", "fire").any { lowerDesc.contains(it) }
                        val priority = if (isUrgent) "High" else "Normal"

                        val newTask = hashMapOf(
                            "title" to taskTitle,
                            "description" to taskDesc,
                            "assigned_to" to userId,
                            "status" to "Pending",
                            "priority" to priority,
                            "proof_uri" to "",
                            "timestamp" to System.currentTimeMillis()
                        )

                        firestore.collection("tasks").add(newTask).addOnSuccessListener {
                            isSavingTask = false
                            showTaskDialog = false
                            taskTitle = ""
                            taskDesc = ""

                            // 🔔 SEND NOTIFICATION (V1 API)
                            firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
                                val userToken = document.getString("fcm_token")
                                if (!userToken.isNullOrEmpty()) {
                                    // Calls the new logic below
                                    sendNotificationV1(
                                        context = context,
                                        token = userToken,
                                        title = "New Task Assigned",
                                        message = "Task: $taskTitle ($priority)"
                                    )
                                }
                            }
                        }
                    }
                }) { Text("ASSIGN") }
            },
            dismissButton = { TextButton(onClick = { showTaskDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminTaskCard(task: AdminTaskItem) {
    val firestore = FirebaseFirestore.getInstance()
    val statusColor = when(task.status) {
        "In Progress" -> Color(0xFF1976D2)
        "Completed" -> Color(0xFF2E7D32)
        else -> Color(0xFFEF6C00)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(text = task.status.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { firestore.collection("tasks").document(task.id).delete() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp).verticalScroll(rememberScrollState())) {
                Text(task.desc, fontSize = 14.sp, color = Color.Gray)
            }
            if (task.status == "Completed") {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Proof stored on user device.", fontSize = 12.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
    }
}

// ----------------------------------------------------------------
// 🚀 NEW V1 NOTIFICATION LOGIC (Uses FcmTokenGenerator)
// ----------------------------------------------------------------
fun sendNotificationV1(context: Context, token: String, title: String, message: String) {

    // 1. Get the Signed JWT from the helper class we made
    val jwt = FcmTokenGenerator.getAccessToken()

    if (jwt == null) {
        Toast.makeText(context, "Error: Could not generate Key", Toast.LENGTH_SHORT).show()
        return
    }

    // 2. Exchange JWT for Google Access Token
    val authUrl = "https://oauth2.googleapis.com/token"
    val authParams = JSONObject().apply {
        put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        put("assertion", jwt)
    }

    val authRequest = object : JsonObjectRequest(Method.POST, authUrl, authParams,
        { response ->
            // Success! We got the Access Token
            val accessToken = response.getString("access_token")
            // Now send the actual message
            sendActualV1Message(context, token, title, message, accessToken)
        },
        { error -> error.printStackTrace() }
    ) {}
    Volley.newRequestQueue(context).add(authRequest)
}

fun sendActualV1Message(context: Context, token: String, title: String, message: String, accessToken: String) {
    // ✅ YOUR PROJECT ID from your JSON file
    val projectId = "geostaff-b0d66"
    val fcmUrl = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"

    val jsonBody = JSONObject()
    val messageObj = JSONObject()
    val notification = JSONObject()

    try {
        notification.put("title", title)
        notification.put("body", message)

        messageObj.put("token", token)
        messageObj.put("notification", notification)

        jsonBody.put("message", messageObj)

        val request = object : JsonObjectRequest(Method.POST, fcmUrl, jsonBody,
            { response -> Toast.makeText(context, "Notification Sent!", Toast.LENGTH_SHORT).show() },
            { error ->
                error.printStackTrace()
                Toast.makeText(context, "Failed to send notification", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer $accessToken" // <--- The New V1 Way
                headers["Content-Type"] = "application/json"
                return headers
            }
        }
        Volley.newRequestQueue(context).add(request)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}