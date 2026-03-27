package com.example.geostaff.userinterface

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Data Model
data class TaskItem(
    val id: String,
    val title: String,
    val desc: String,
    val status: String,
    val priority: String = "Normal",
    val proofUri: String = ""
)

@Composable
fun TasksScreen() {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("tasks")
                .whereEqualTo("assigned_to", userId)
                .addSnapshotListener { value, _ ->
                    isLoading = false
                    if (value != null) {
                        val list = value.documents.map { doc ->
                            TaskItem(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                desc = doc.getString("description") ?: "",
                                status = doc.getString("status") ?: "Pending",
                                priority = doc.getString("priority") ?: "Normal",
                                proofUri = doc.getString("proof_uri") ?: ""
                            )
                        }
                        tasks = list.sortedWith(compareBy<TaskItem> {
                            if (it.priority == "High" && it.status != "Completed") 0 else 1
                        }.thenBy {
                            when(it.status) { "Pending" -> 0; "In Progress" -> 1; else -> 2 }
                        })
                    }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp)) {
        Text("My Tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tasks assigned.", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(tasks) { task ->
                    TaskProcessCard(task)
                }
            }
        }
    }
}

@Composable
fun TaskProcessCard(task: TaskItem) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    var showUploadUI by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    var aiScanning by remember { mutableStateOf(false) }
    var aiResultText by remember { mutableStateOf("") }
    var isPhotoRelevant by remember { mutableStateOf(true) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            capturedImageUri = tempCameraUri

            // --- 🤖 IMPROVED AI LOGIC ---
            aiScanning = true
            try {
                val image = InputImage.fromFilePath(context, tempCameraUri!!)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        aiScanning = false
                        // Get anything with > 50% confidence
                        val detectedObjects = labels.filter { it.confidence > 0.5 }.map { it.text }

                        // 1. DEFINE SYNONYMS (The "Thesaurus")
                        val techWords = listOf("computer", "laptop", "screen", "monitor", "keyboard", "electronic", "technology", "display", "machine", "device")
                        val repairWords = listOf("tool", "hardware", "metal", "product", "engineering")

                        // 2. CHECK MATCH
                        val taskTitleLower = task.title.lowercase()
                        var matchFound = false

                        // Does the AI label match the Title directly?
                        if (detectedObjects.any { label -> taskTitleLower.contains(label.lowercase()) }) {
                            matchFound = true
                        }

                        // Does the Title contain "Laptop/PC" and AI see "Tech"?
                        if (!matchFound && (taskTitleLower.contains("laptop") || taskTitleLower.contains("computer") || taskTitleLower.contains("pc"))) {
                            if (detectedObjects.any { label -> techWords.contains(label.lowercase()) }) {
                                matchFound = true
                            }
                        }

                        // Does the Title contain "Repair" and AI see "Tools"?
                        if (!matchFound && (taskTitleLower.contains("repair") || taskTitleLower.contains("fix"))) {
                            if (detectedObjects.any { label -> repairWords.contains(label.lowercase()) }) {
                                matchFound = true
                            }
                        }

                        // 3. RESULT
                        if (matchFound) {
                            isPhotoRelevant = true
                            aiResultText = "✅ AI Verified: Relevant Content Detected"
                        } else {
                            // If detecting "Musical Instrument" but it's clearly a laptop context, we assume the AI is just confused and let it pass partially.
                            // But for now, we show the warning to be safe.
                            isPhotoRelevant = false
                            val displayTags = detectedObjects.take(2).joinToString(", ")
                            aiResultText = "⚠️ AI Warning: Photo looks like $displayTags"
                        }
                    }
                    .addOnFailureListener {
                        aiScanning = false
                        aiResultText = ""
                    }
            } catch (e: Exception) {
                aiScanning = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try { val uri = createImageFile(context); tempCameraUri = uri; cameraLauncher.launch(uri) } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
        } else { Toast.makeText(context, "Camera permission needed.", Toast.LENGTH_LONG).show() }
    }

    val isUrgent = task.priority == "High" && task.status != "Completed"
    val borderColor = if (isUrgent) Color.Red else Color.Transparent
    val cardBg = if (isUrgent) Color(0xFFFFEBEE) else Color.White
    val statusColor = when(task.status) {
        "In Progress" -> Color(0xFF1976D2)
        "Completed" -> Color(0xFF2E7D32)
        else -> Color(0xFFEF6C00)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUrgent) Text("🔥 ", fontSize = 18.sp)
                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isUrgent) Color.Red else Color.Black)
                }
                Surface(color = if(isUrgent) Color.Red else statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(if(isUrgent) "URGENT" else task.status.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(isUrgent) Color.White else statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp).verticalScroll(rememberScrollState())) {
                Text(task.desc, fontSize = 14.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (task.status) {
                "Pending" -> {
                    Button(onClick = { firestore.collection("tasks").document(task.id).update("status", "In Progress") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(modifier = Modifier.width(8.dp)); Text("START TASK")
                    }
                }
                "In Progress" -> {
                    if (!showUploadUI) {
                        Button(onClick = { showUploadUI = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Check, null); Spacer(modifier = Modifier.width(8.dp)); Text("MARK COMPLETED")
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Text("Take Proof Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(onClick = {
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    try { val uri = createImageFile(context); tempCameraUri = uri; cameraLauncher.launch(uri) } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                } else { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
                                Icon(Icons.Default.CameraAlt, null, tint = Color.Gray); Spacer(modifier = Modifier.width(8.dp)); Text(if (capturedImageUri == null) "Open Camera" else "📸 Retake Photo")
                            }

                            if (aiScanning) {
                                Text("AI scanning...", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            } else if (aiResultText.isNotEmpty()) {
                                Text(
                                    text = aiResultText,
                                    fontSize = 12.sp,
                                    color = if (isPhotoRelevant) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                if (capturedImageUri != null) {
                                    firestore.collection("tasks").document(task.id).update(mapOf("status" to "Completed", "proof_uri" to capturedImageUri.toString()))
                                    showUploadUI = false
                                } else { Toast.makeText(context, "Please take a photo first", Toast.LENGTH_SHORT).show() }
                            }, modifier = Modifier.fillMaxWidth(), enabled = capturedImageUri != null, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                                Text("SUBMIT & FINISH")
                            }
                            TextButton(onClick = { showUploadUI = false; capturedImageUri = null; aiResultText = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel", color = Color.Gray, fontSize = 12.sp) }
                        }
                    }
                }
                "Completed" -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (task.proofUri.isNotEmpty()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(task.proofUri), "image/*")
                                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) { Toast.makeText(context, "Cannot open file.", Toast.LENGTH_SHORT).show() }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF2E7D32)); Spacer(modifier = Modifier.width(8.dp)); Text("PROOF", color = Color(0xFF2E7D32))
                        }

                        Button(
                            onClick = { firestore.collection("tasks").document(task.id).delete() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

fun createImageFile(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}