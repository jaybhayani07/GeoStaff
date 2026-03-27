package com.example.geostaff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.geostaff.userinterface.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

// Define Screen States (Ensure this matches your logic)
enum class ScreenState {
    LOGIN,
    EMPLOYEE_HOME,
    ADMIN_HOME,
    ADMIN_USER_DETAIL
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. REGISTER LIFECYCLE OBSERVER (Keeps DB clean on swipe-close)
        lifecycle.addObserver(AppLifecycleObserver())

        setContent {
            // STATE VARIABLES
            var currentScreen by remember { mutableStateOf(ScreenState.LOGIN) }
            var selectedAdminUserId by remember { mutableStateOf("") }
            var isCheckingAuth by remember { mutableStateOf(true) } // <--- NEW LOADING STATE


            LaunchedEffect(Unit) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    // GET TOKEN AND SAVE TO DB
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            FirebaseFirestore.getInstance().collection("users")
                                .document(user.uid)
                                .update("fcm_token", token) // Saving token!
                        }
                    }
                }
            }

            // 2. CHECK IF USER IS ALREADY LOGGED IN
            LaunchedEffect(Unit) {
                val user = FirebaseAuth.getInstance().currentUser

                if (user != null) {
                    // User exists! Fetch their Role to know where to send them
                    FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                // RE-LOCK THE SESSION (Since we are back online)
                                FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                    .update("is_logged_in", true)

                                val role = document.getString("role") ?: "employee"
                                if (role == "admin") {
                                    currentScreen = ScreenState.ADMIN_HOME
                                } else {
                                    currentScreen = ScreenState.EMPLOYEE_HOME
                                }
                            }
                            isCheckingAuth = false // Done checking
                        }
                        .addOnFailureListener {
                            // If network fails, go to login just in case
                            currentScreen = ScreenState.LOGIN
                            isCheckingAuth = false
                        }
                } else {
                    // No user found, show Login screen
                    currentScreen = ScreenState.LOGIN
                    isCheckingAuth = false
                }
            }

            // 3. UI LOGIC
            if (isCheckingAuth) {
                // SHOW LOADING SPINNER WHILE CHECKING
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // MAIN NAVIGATION SWITCHER
                when (currentScreen) {
                    ScreenState.LOGIN -> {
                        LoginScreen(onLoginSuccess = { role ->
                            if (role == "admin") {
                                currentScreen = ScreenState.ADMIN_HOME
                            } else {
                                currentScreen = ScreenState.EMPLOYEE_HOME
                            }
                        })
                    }

                    ScreenState.EMPLOYEE_HOME -> {
                        MainScreen(
                            onLogout = {
                                currentScreen = ScreenState.LOGIN
                            }
                        )
                    }

                    ScreenState.ADMIN_HOME -> {
                        AdminDashboardScreen(
                            onLogout = {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                if (uid != null) {
                                    FirebaseFirestore.getInstance().collection("users").document(uid).update("is_logged_in", false)
                                }
                                FirebaseAuth.getInstance().signOut()
                                currentScreen = ScreenState.LOGIN
                            },
                            onUserClick = { userId ->
                                selectedAdminUserId = userId
                                currentScreen = ScreenState.ADMIN_USER_DETAIL
                            }
                        )
                    }

                    ScreenState.ADMIN_USER_DETAIL -> {
                        AdminUserDetail(
                            userId = selectedAdminUserId,
                            onBack = { currentScreen = ScreenState.ADMIN_HOME }
                        )
                    }
                }
            }
        }
    }

    // EXTRA SAFETY: Ensure offline status on destroy
    override fun onDestroy() {
        super.onDestroy()
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Note: This runs when app is killed.
            // The LaunchedEffect above fixes it when app starts again.
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("is_logged_in", false)
        }
    }
}