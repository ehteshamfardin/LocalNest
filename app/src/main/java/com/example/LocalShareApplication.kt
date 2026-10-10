package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.LocalShareRepository
import com.example.server.LocalShareHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalShareApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        LocalShareRepository(
            this,
            database.shareDao(),
            database.sharedItemDao(),
            database.serverLogDao()
        )
    }

    val httpServer by lazy {
        LocalShareHttpServer(this, repository, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            repository.ensureDefaultSampleShares()
        }
    }
}
