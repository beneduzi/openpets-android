package com.openpets.android.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.openpets.android.AppState
import com.openpets.android.HapticsNotifier
import com.openpets.android.companion.CompanionRuntime
import com.openpets.android.companion.CompanionSources
import com.openpets.android.companion.TimerController
import com.openpets.android.companion.ReminderController
import com.openpets.android.companion.FocusController
import com.openpets.android.companion.WaterController
import com.openpets.android.companion.RoutineController
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.PetActionEvent
import com.openpets.android.FeatureHud
import com.openpets.android.R
import com.openpets.android.SoundNotifier
import com.openpets.android.pet.CatalogClient
import com.openpets.android.pet.PetRepository
import com.openpets.android.protocol.IpcServer
import com.openpets.android.protocol.IpcDispatcher
import com.openpets.android.protocol.MediaRequest
import com.openpets.android.protocol.PetCatalogProvider
import com.openpets.android.protocol.PetDescriptor
import com.openpets.android.protocol.PetInstallResult
import com.openpets.android.protocol.PetInstaller
import com.openpets.android.protocol.RemoteServer
import com.openpets.android.protocol.SayRequest
import com.openpets.android.settings.AppSettings

/**
 * Foreground service hosting the OpenPets IPC and remote TCP servers so the
 * companion keeps receiving agent activity while the app is backgrounded.
 */
class OpenPetsService : Service() {

    private lateinit var settings: AppSettings
    private var ipcServer: IpcServer? = null
    private var remoteServer: RemoteServer? = null
    private var wsBridge: WebSocketBridge? = null
    private lateinit var sound: SoundNotifier
    private lateinit var haptics: HapticsNotifier
    private lateinit var notifier: NotificationHelper
    private var timerController: TimerController? = null
    private var reminderController: ReminderController? = null
    private var focusController: FocusController? = null
    private var waterController: WaterController? = null
    private var routineController: RoutineController? = null

    private val eventListener = object : PetBus.Listener {
        override fun onReact(reaction: String, source: String?) {
            if (reaction !in SoundNotifier.NOTIFY_REACTIONS) return
            if (settings.soundEnabled) sound.play()
            if (settings.hapticsEnabled) haptics.vibrate()
            // Companion sources already post dedicated notifications; avoid duplicating them.
            if (settings.notifyEnabled && !AppState.foreground && source !in CompanionSources.ALL) {
                notifier.notifyEvent(source, this@OpenPetsService.getString(R.string.reaction_label, reaction))
            }
        }

        override fun onSay(request: SayRequest) {
            if (settings.soundEnabled) sound.play()
            if (settings.hapticsEnabled) haptics.vibrate()
            // Companion sources already post dedicated notifications; avoid duplicating them.
            if (settings.notifyEnabled && !AppState.foreground && request.source !in CompanionSources.ALL) {
                notifier.notifyEvent(request.source, request.message)
            }
        }

        override fun onDismissBubble(id: String?) {
            // No service-side action needed.
        }

        override fun onHud(hud: FeatureHud) {
            // Feature HUD updates are intentionally silent.
        }

        override fun onAction(event: PetActionEvent) {
            // No service-side action needed.
        }

        override fun onInputText(text: String) { /* No service-side action needed. */ }

        override fun onMedia(request: MediaRequest) {
            if (settings.soundEnabled) sound.play()
            if (settings.hapticsEnabled) haptics.vibrate()
            if (settings.notifyEnabled && !AppState.foreground) {
                notifier.notifyEvent(null, request.message ?: this@OpenPetsService.getString(R.string.media_received))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        CompanionRuntime.init(this)
        sound = SoundNotifier(this)
        sound.warmUp()
        haptics = HapticsNotifier(this)
        notifier = NotificationHelper(this).apply { ensureChannel() }
        createNotificationChannel()
        PetBus.register(eventListener)
        // Reconcile a persisted running timer after reboot or process death: constructing the
        // controller reschedules the exact alarm. The HUD is re-shown when the activity opens.
        timerController = CompanionRuntime.timer.also { it.refresh() }
        reminderController = CompanionRuntime.reminders.also { it.refresh() }
        CompanionRuntime.deadline.refresh()
        focusController = CompanionRuntime.focus.also { it.refresh() }
        waterController = CompanionRuntime.water.also { it.refresh() }
        routineController = CompanionRuntime.routine.also { it.refresh() }
        CompanionRuntime.calendar.refresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                stopServers()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> restartServers()
            else -> startServers()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        PetBus.unregister(eventListener)
        stopServers()
        sound.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServers() {
        stopServers()
        sound.setCustomUri(settings.customSoundUri)
        val repository = PetRepository(this)
        val catalog = object : PetCatalogProvider {
            override val selectedPetId: String get() = settings.selectedPetId
            override fun pets(): List<PetDescriptor> = repository.listPets()
        }
        val installer = object : PetInstaller {
            override fun installFromCatalog(petId: String): PetInstallResult {
                val bytes = CatalogClient().downloadZipBytes(petId)
                val descriptor = repository.importZipBytes(bytes)
                return PetInstallResult(descriptor.id, descriptor.displayName)
            }

            override fun installFromZip(dataBase64: String?, url: String?): PetInstallResult {
                val bytes = when {
                    dataBase64 != null -> Base64.decode(dataBase64, Base64.DEFAULT)
                    url != null -> CatalogClient().downloadBytes(url)
                    else -> throw IllegalArgumentException("no install source")
                }
                val descriptor = repository.importZipBytes(bytes)
                return PetInstallResult(descriptor.id, descriptor.displayName)
            }
        }
        val dispatcher = IpcDispatcher(PetBus.sink, "0.1.0", catalog, installer, settings.requireLease)
        if (settings.ipcEnabled) {
            ipcServer = try {
                IpcServer(
                    sink = PetBus.sink,
                    token = settings.ipcToken,
                    bindAddress = settings.bindAddress,
                    port = settings.ipcPort,
                    petCatalog = catalog,
                    installer = installer,
                    enforceLease = settings.requireLease,
                    dispatcher = dispatcher,
                ).also { it.start() }
            } catch (_: Exception) {
                null
            }
        }
        if (settings.wsEnabled) {
            wsBridge = WebSocketBridge(this, { settings.ipcToken }, dispatcher, "0.1.0").also {
                it.start(settings.wsPort, settings.bindAddress)
            }
        }
        if (settings.remoteEnabled) {
            remoteServer = try {
                RemoteServer(
                    sink = PetBus.sink,
                    token = settings.remoteToken,
                    bindAddress = settings.bindAddress,
                    port = settings.remotePort,
                ).also { it.start() }
            } catch (_: Exception) {
                null
            }
        }
        updateNotification()
    }

    private fun restartServers() {
        startServers()
    }

    private fun stopServers() {
        ipcServer?.stop()
        remoteServer?.stop()
        wsBridge?.stop()
        ipcServer = null
        remoteServer = null
        wsBridge = null
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val ipc = if (ipcServer?.isRunning == true) ipcServer!!.localPort else 0
        val remote = if (remoteServer?.isRunning == true) remoteServer!!.localPort else 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (ipc > 0 || remote > 0) {
                    getString(R.string.notification_text_listening, ipc, remote)
                } else {
                    getString(R.string.notification_text_stopped)
                },
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "openpets"
        const val NOTIFICATION_ID = 1010
        const val ACTION_START = "com.openpets.android.action.START"
        const val ACTION_RELOAD = "com.openpets.android.action.RELOAD"
        const val ACTION_STOP = "com.openpets.android.action.STOP"

        fun start(context: Context) {
            send(context, ACTION_START)
        }

        fun reload(context: Context) {
            send(context, ACTION_RELOAD)
        }

        fun stop(context: Context) {
            send(context, ACTION_STOP)
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, OpenPetsService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
