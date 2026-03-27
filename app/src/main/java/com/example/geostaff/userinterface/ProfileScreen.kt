package com.example.geostaff.userinterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var name by remember { mutableStateOf("Loading...") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Employee") }

    // Fetch User Details
    LaunchedEffect(Unit) {
        val user = auth.currentUser
        if (user != null) {
            email = user.email ?: ""
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener {
                    name = it.getString("name") ?: "User"
                    role = it.getString("role")?.replaceFirstChar { char -> char.uppercase() } ?: "Employee"
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // --- TOP HEADER (Blue Curve) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    color = Color(0xFF1976D2),
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar Placeholder
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(role, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- INFO CARDS ---
        Column(modifier = Modifier.padding(16.dp)) {
            ProfileItem(title = "Email Address", value = email, icon = Icons.Default.Person)

            Spacer(modifier = Modifier.height(16.dp))

            // You can add more rows here later (e.g., Phone, Department)
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- LOGOUT BUTTON ---
        Button(
            onClick = {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    // Mark Offline
                    firestore.collection("users").document(userId).update("is_logged_in", false)
                        .addOnSuccessListener {
                            auth.signOut()
                            onLogout() // TRIGGER NAVIGATION SWITCH
                        }
                } else {
                    auth.signOut()
                    onLogout()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Red
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(54.dp)
        ) {
            Icon(Icons.Default.ExitToApp, null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("LOG OUT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileItem(title: String, value: String, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFF1976D2))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 12.sp, color = Color.Gray)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}