package com.example.geostaff.userinterface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.geostaff.userinterface.DashboardScreen

@Composable

fun AppNaviGation(){
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login"){

        composable("login"){
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("dashboard"){
                        popUpTo("login") { inclusive = true }
                    }

                }
            )
        }
        composable("dashboard"){
            DashboardScreen()
        }
    }



}
