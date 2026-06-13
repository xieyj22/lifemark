package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.DocumentListScreen
import com.example.ui.DocumentViewModel
import com.example.ui.DocumentViewModelFactory
import com.example.ui.EditorScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable capturing full-height of WebView for PNG exporting
        try {
            android.webkit.WebView.enableSlowWholeDocumentDraw()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Get our database repository singleton
        val app = application as MarkdownApplication
        val repository = app.repository

        setContent {
            MyApplicationTheme {
                // Initialize modern View State management with Factory Injection
                val mainViewModel: DocumentViewModel = viewModel(
                    factory = DocumentViewModelFactory(repository)
                )

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        // Document Manager Dashboard
                        composable("dashboard") {
                            DocumentListScreen(
                                viewModel = mainViewModel,
                                onNavigateToEditor = { docId ->
                                    navController.navigate("editor/$docId")
                                }
                            )
                        }

                        // Advanced Markdown and LaTeX Editor View
                        composable(
                            route = "editor/{docId}",
                            arguments = listOf(
                                navArgument("docId") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            val docId = backStackEntry.arguments?.getInt("docId") ?: -1
                            EditorScreen(
                                documentId = docId,
                                viewModel = mainViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
