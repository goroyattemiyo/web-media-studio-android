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
import com.goroyattemiyo.wms.WmsApplication
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
    private val mediaRepository by lazy { (application as WmsApplication).mediaRepository }
    private val playlistRepository by lazy { (application as WmsApplication).playlistRepository }
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
            ACTION_START -> startManagedJob(
                intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty(),
                intent.getStringExtra(EXTRA_PLAYLIST_ID),
            )
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startManagedJob(sourceUrl: String, playlistId: String?) {
        val source = sourceUrl.trim()
        if (source.isBlank()) {
            stopSelf()
            return
        }
        if (activeJob?.isActive == true) return

        engine.prepareForAcquisition()
        (application as? WmsApplication)?.requestNotificationPermissionForSave()

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

            val registeredResult = result.fold(
                onSuccess = { acquisition ->
                    runCatching {
                        val media = mediaRepository.registerAcquisition(acquisition, source)
                        playlistId?.let { playlistRepository.addMedia(it, media.id) }
                        acquisition
                    }.onFailure {
                        runCatching { mediaRepository.rollbackRegistration(acquisition.file) }
                    }
                },
                onFailure = { Result.failure(it) },
            )

            registeredResult.onSuccess { acquisition ->
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
                val code = engineError?.code ?: if (result.isSuccess) {
                    "LIBRARY_REGISTER_FAILED"
                } else {
                    "ACQUIRE_FAILED"
                }
                val message = engineError?.message ?: if (result.isSuccess) {
                    "保存したメディアをライブラリへ登録できませんでした。"
                } else {
                    "メディアの保存に失敗しました。"
                }
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
            .setContentIntent(openWmsPendingIntent())
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

    private fun terminalNotification(title: String, message: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.wms_emblem)
            .setContentTitle(title)
            .setContentText(message.take(120))
            .setContentIntent(openWmsPendingIntent())
            .setAutoCancel(true)
            .build()

    private fun openWmsPendingIntent(): PendingIntent {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            // MainActivity uses the default launch mode. CLEAR_TOP without SINGLE_TOP
            // recreates it, so a fresh ViewModel reconnects to ManagedAcquisitionBus
            // and the existing URL/state reopens the Import Sheet deterministically.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_START = "com.goroyattemiyo.wms.action.START_ACQUISITION"
        const val ACTION_CANCEL = "com.goroyattemiyo.wms.action.CANCEL_ACQUISITION"
        const val EXTRA_SOURCE_URL = "source_url"
        const val EXTRA_PLAYLIST_ID = "playlist_id"

        private const val CHANNEL_ID = "wms_acquisition"
        private const val NOTIFICATION_ID = 2101
        private const val REQUEST_CANCEL = 2102
        private const val REQUEST_OPEN = 2103
    }
}
