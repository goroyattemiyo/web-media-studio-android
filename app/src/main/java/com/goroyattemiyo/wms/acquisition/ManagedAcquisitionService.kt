package com.goroyattemiyo.wms.acquisition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.goroyattemiyo.wms.MainActivity
import com.goroyattemiyo.wms.R
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ManagedAcquisitionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine by lazy { YoutubeDlAcquisitionEngine(applicationContext) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var activeJob: Job? = null
    private var activeSourceUrl: String = ""

    @Volatile
    private var cancelRequested = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelActiveJob()
            ACTION_START -> startManagedJob(intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty())
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startManagedJob(sourceUrl: String) {
        val source = sourceUrl.trim()
        if (source.isBlank()) {
            stopSelf()
            return
        }
        if (activeJob?.isActive == true) return

        activeSourceUrl = source
        cancelRequested = false
        ManagedAcquisitionBus.started(source)

        try {
            startForeground(
                NOTIFICATION_ID,
                runningNotification(0, "取得を開始しています"),
            )
        } catch (error: Throwable) {
            ManagedAcquisitionBus.failed(
                source,
                "FOREGROUND_START_FAILED",
                "バックグラウンド保存の通知を開始できませんでした。",
            )
            activeSourceUrl = ""
            stopSelf()
            return
        }

        activeJob = serviceScope.launch {
            val initState = engine.initialize()
            val result: Result<AcquisitionResult> = if (!initState.ready) {
                Result.failure(
                    AcquisitionEngineException(
                        initState.code,
                        initState.message,
                    ),
                )
            } else {
                val youtubeDl = YoutubeDL.getInstance()
                synchronized(youtubeDl) {
                    // updateYoutubeDL() and local Search use this same monitor.
                    // Engine initialization happens before taking it so the suspend
                    // acquisition can safely run while updater/search are excluded.
                    runBlocking {
                        engine.acquireMp3(source) { progress ->
                            if (!cancelRequested) {
                                ManagedAcquisitionBus.progress(progress.percent, progress.message)
                                notificationManager.notify(
                                    NOTIFICATION_ID,
                                    runningNotification(progress.percent.toInt(), progress.message),
                                )
                            }
                        }
                    }
                }
            }

            if (cancelRequested) return@launch

            result.onSuccess { acquisition ->
                ManagedAcquisitionBus.succeeded(acquisition)
                activeJob = null
                activeSourceUrl = ""
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    terminalNotification(
                        title = "WMS 保存完了",
                        message = acquisition.title,
                    ),
                )
                stopSelf()
            }.onFailure { error ->
                val engineError = error as? AcquisitionEngineException
                val code = engineError?.code ?: "ACQUIRE_FAILED"
                val message = engineError?.message ?: "メディアの保存に失敗しました。"
                ManagedAcquisitionBus.failed(source, code, message)
                activeJob = null
                activeSourceUrl = ""
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.notify(
                    NOTIFICATION_ID,
                    terminalNotification(
                        title = "WMS 保存失敗",
                        message = message,
                    ),
                )
                stopSelf()
            }
        }
    }

    private fun cancelActiveJob() {
        val job = activeJob
        val source = activeSourceUrl
        if (job?.isActive != true) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        cancelRequested = true
        engine.cancel()
        job.cancel()
        activeJob = null
        activeSourceUrl = ""
        if (source.isNotBlank()) {
            ManagedAcquisitionBus.canceled(source)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (activeJob?.isActive == true) {
            engine.cancel()
            activeJob?.cancel()
        }
        activeJob = null
        activeSourceUrl = ""
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "WMS メディア保存",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "WMSのローカル保存処理の進捗を表示します"
            },
        )
    }

    private fun runningNotification(percent: Int, message: String): Notification {
        val cancelIntent = Intent(this, ManagedAcquisitionService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.wms_emblem)
            .setContentTitle("WMS 保存中")
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "キャンセル",
                    cancelPendingIntent,
                ).build(),
            )
            .build()
    }

    private fun terminalNotification(title: String, message: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.wms_emblem)
            .setContentTitle(title)
            .setContentText(message.take(120))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.goroyattemiyo.wms.action.START_ACQUISITION"
        const val ACTION_CANCEL = "com.goroyattemiyo.wms.action.CANCEL_ACQUISITION"
        const val EXTRA_SOURCE_URL = "source_url"

        private const val CHANNEL_ID = "wms_acquisition"
        private const val NOTIFICATION_ID = 2101
        private const val REQUEST_CANCEL = 2102
        private const val REQUEST_OPEN = 2103
    }
}
