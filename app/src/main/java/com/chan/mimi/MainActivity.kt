package com.chan.mimi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.chan.mimi.data.repository.SavedThreadsPollingScheduler
import com.chan.mimi.data.repository.WatchedThreadsPollingScheduler
import com.chan.mimi.navigation.ChanNavGraph
import com.chan.mimi.ui.theme.ChanTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        SavedThreadsPollingScheduler.updatePersistentNotification(this)
        WatchedThreadsPollingScheduler.updatePersistentNotification(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            ChanTheme {
                ChanNavGraph()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
