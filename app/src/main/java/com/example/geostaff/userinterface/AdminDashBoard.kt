package com.example.geostaff.userinterface

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

// Data Model (Updated with 'name')
data class EmployeeSummary(
    val uid: String,
    val name: String,
    val email: String,
    val isOnline: Boolean,
    val lat: Double,
    val lng: Double
)

@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()

    // List States
    var employees by remember { mutableStateOf<List<EmployeeSummary>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") } // For Search Bar

    // Dialog States
    var showAddUserDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }     // 1. Added Name State
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    // FETCH LIVE DATA
    LaunchedEffect(Unit) {
        firestore.collection("users")
            .whereEqualTo("role", "employee")
            .addSnapshotListener { value, _ ->
                if (value != null) {
                    employees = value.documents.map { doc ->
                        EmployeeSummary(
                            uid = doc.id,
                            name = doc.getString("name") ?: "No Name", // Fetch Name
                            email = doc.getString("email") ?: "Unknown",
                            isOnline = doc.getBoolean("is_logged_in") ?: false,
                            lat = doc.getDouble("last_lat") ?: 0.0,
                            lng = doc.getDouble("last_lng") ?: 0.0
                        )
                    }
                }
            }
    }

    // FILTER LOGIC (Search)
    val filteredEmployees = if (searchQuery.isEmpty()) {
        employees
    } else {
        employees.filter {
            it.name.lowercase(Locale.getDefault()).contains(searchQuery.lowercase(Locale.getDefault()))
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddUserDialog = true },
                containerColor = Color(0xFF1976D2)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add User", tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // HEADER
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Admin Panel", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2), modifier = Modifier.weight(1f))
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.Close, null, tint = Color.Red)
                }
            }
            Text("Monitor Employee Live Status", color = Color.Gray)

            Spacer(modifier = Modifier.height(16.dp))

            // SEARCH BAR 🔍
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Name") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // LIST
            if (filteredEmployees.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if(searchQuery.isEmpty()) "No Employees Found." else "No result for '$searchQuery'", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(filteredEmployees) { emp ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { onUserClick(emp.uid) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // STATUS DOT
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(if (emp.isOnline) Color.Green else Color.Red, CircleShape)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                // INFO (Name + Email)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(emp.name, fontWeight = FontWeight.Bold, fontSize = 18.sp) // Show Name Big
                                    Text(emp.email, fontSize = 12.sp, color = Color.Gray) // Show Email Small

                                    if (emp.isOnline) {
                                        Text("Lat: ${emp.lat}, Lng: ${emp.lng}", fontSize = 12.sp, color = Color(0xFF1976D2))
                                    } else {
                                        Text("Offline", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.6f))
                                    }
                                }

                                // DELETE BUTTON
                                IconButton(
                                    onClick = {
                                        firestore.collection("users").document(emp.uid).delete()
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove User", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ADD USER DIALOG (Now with Name!) ---
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Register Employee") },
            text = {
                Column {
                    Text("Enter details for the new employee.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Name Input
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Email Input
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Password Input
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Set Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotEmpty() && newEmail.isNotEmpty() && newPassword.isNotEmpty()) {
                            isAdding = true

                            val newUser = hashMapOf(
                                "name" to newName,       // Save Name
                                "email" to newEmail,
                                "password" to newPassword,
                                "role" to "employee",
                                "is_logged_in" to false,
                                "last_lat" to 0.0,
                                "last_lng" to 0.0
                            )

                            firestore.collection("users").add(newUser)
                                .addOnSuccessListener {
                                    isAdding = false
                                    showAddUserDialog = false
                                    newName = ""
                                    newEmail = ""
                                    newPassword = ""
                                }
                        }
                    }
                ) {
                    Text("ADD USER")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}