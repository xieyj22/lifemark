package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.DocumentRepository

class MarkdownApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { DocumentRepository(database.documentDao()) }
}
