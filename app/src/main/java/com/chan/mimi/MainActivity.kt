package com.chan.mimi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.chan.mimi.data.repository.SavedThreadsPollingWorker
import com.chan.mimi.data.repository.SavedThreadsPollingScheduler
import com.chan.mimi.data.repository.WatchedThreadsPollingScheduler
import com.chan.mimi.navigation.ChanNavGraph
import com.chan.mimi.ui.theme.ChanTheme

data class SavedNotificationTarget(
    val boardTag: String,
    val threadNo: Long,
    val highlightPostId: Long?,
    val addedPostIds: List<Long>,
    val deletedPostIds: List<Long>,
    val requestId: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    private var savedNotificationTarget by mutableStateOf<SavedNotificationTarget?>(null)

    private val requestNotificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        SavedThreadsPollingScheduler.updatePersistentNotification(this)
        WatchedThreadsPollingScheduler.updatePersistentNotification(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedNotificationTarget = intent.toSavedNotificationTarget()
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            ChanTheme {
                ChanNavGraph(
                    savedNotificationTarget = savedNotificationTarget,
                    onSavedNotificationHandled = { savedNotificationTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        savedNotificationTarget = intent.toSavedNotificationTarget()
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

private fun Intent?.toSavedNotificationTarget(): SavedNotificationTarget? {
    val extras = this?.extras ?: return null
    if (!extras.getBoolean(SavedThreadsPollingWorker.EXTRA_IS_SAVED, false)) return null

    val boardTag = extras.getString(SavedThreadsPollingWorker.KEY_BOARD_TAG).orEmpty()
    val threadNo = extras.getLong(SavedThreadsPollingWorker.KEY_THREAD_NO, 0L)
    if (boardTag.isBlank() || threadNo <= 0L) return null

    val highlightPostId = extras.getLong(SavedThreadsPollingWorker.EXTRA_HIGHLIGHT_POST_ID, -1L)
        .takeIf { it > 0L }

    return SavedNotificationTarget(
        boardTag = boardTag,
        threadNo = threadNo,
        highlightPostId = highlightPostId,
        addedPostIds = extras.parseIdList(SavedThreadsPollingWorker.EXTRA_ADDED_POST_IDS),
        deletedPostIds = extras.parseIdList(SavedThreadsPollingWorker.EXTRA_DELETED_POST_IDS)
    )
}

private fun Bundle.parseIdList(key: String): List<Long> =
    getString(key)
        .orEmpty()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
