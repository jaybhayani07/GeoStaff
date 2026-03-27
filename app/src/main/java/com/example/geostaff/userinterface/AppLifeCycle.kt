package com.example.geostaff.userinterface

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppLifecycleObserver : DefaultLifecycleObserver {

    // This runs when the App goes to Background or is Closed
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        setUserOffline()
    }

    // This runs when the App is fully Destroyed (Swiped away)
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        setUserOffline()
    }

    private fun setUserOffline() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // SILENTLY UPDATE DATABASE TO OFFLINE
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("is_logged_in", false)
        }
    }
}