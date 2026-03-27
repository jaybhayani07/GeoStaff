package com.example.geostaff.userinterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    // VARIABLES
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // --- HELPER 1: HANDLE EXISTING USER LOGIN ---
    fun checkLockAndEnter(userId: String) {
        val userRef = firestore.collection("users").document(userId)
        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val isLoggedIn = document.getBoolean("is_logged_in") ?: false
                if (isLoggedIn) {
                    // BLOCKED: User is already online
                    isLoading = false
                    auth.signOut()
                    errorMessage = "Login Blocked: Active on another device."
                } else {
                    // ALLOWED: Lock the door and enter
                    userRef.update("is_logged_in", true).addOnSuccessListener {
                        isLoading = false
                        val role = document.getString("role") ?: "employee"
                        onLoginSuccess(role)
                    }
                }
            } else {
                isLoading = false
                errorMessage = "User record missing."
                auth.signOut()
            }
        }.addOnFailureListener {
            isLoading = false
            errorMessage = "Network Error."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("GeoStaff", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    isLoading = true
                    errorMessage = null

                    // 1. ATTEMPT NORMAL LOGIN
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            // Success! Check for locks.
                            checkLockAndEnter(result.user!!.uid)
                        }
                        .addOnFailureListener {
                            // 2. LOGIN FAILED? CHECK IF ADMIN ADDED THEM (Auto-Registration)
                            firestore.collection("users")
                                .whereEqualTo("email", email)
                                .whereEqualTo("password", password) // Check the saved password
                                .get()
                                .addOnSuccessListener { documents ->
                                    if (!documents.isEmpty) {
                                        // FOUND! Register them now.
                                        auth.createUserWithEmailAndPassword(email, password)
                                            .addOnSuccessListener { result ->
                                                val oldDoc = documents.documents[0]
                                                val newUid = result.user!!.uid

                                                // Copy Admin data to new Real Account
                                                val data = oldDoc.data ?: hashMapOf()
                                                data["is_logged_in"] = true // Set to Online immediately

                                                firestore.collection("users").document(newUid).set(data)
                                                    .addOnSuccessListener {
                                                        // Delete the temporary Admin entry
                                                        firestore.collection("users").document(oldDoc.id).delete()
                                                        isLoading = false
                                                        onLoginSuccess("employee")
                                                    }
                                            }
                                            .addOnFailureListener {
                                                isLoading = false
                                                errorMessage = "Registration Failed: ${it.message}"
                                            }
                                    } else {
                                        isLoading = false
                                        errorMessage = "Invalid Credentials."
                                    }
                                }
                        }
                }
            }
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("LOGIN", fontSize = 18.sp)
        }
    }
}