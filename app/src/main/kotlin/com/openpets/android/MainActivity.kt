package com.openpets.android

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openpets.android.net.OpenPetsService
import com.openpets.android.companion.TimerController
import com.openpets.android.companion.TimerEngine
import com.openpets.android.companion.TimerRunState
import com.openpets.android.companion.TimerSnapshot
import com.openpets.android.companion.Reminder
import com.openpets.android.companion.ReminderController
import com.openpets.android.companion.FocusController
import com.openpets.android.companion.FocusSnapshot
import com.openpets.android.companion.FocusState
import com.openpets.android.companion.PetMood
import com.openpets.android.companion.PetStats
import com.openpets.android.companion.VirtualPetController
import com.openpets.android.companion.WaterController
import com.openpets.android.companion.WaterEngine
import com.openpets.android.companion.WaterSettings
import com.openpets.android.FeatureHud
import com.openpets.android.pet.CatalogClient
import com.openpets.android.pet.CatalogPet
import com.openpets.android.pet.PetPackageLoader
import com.openpets.android.pet.PetRepository
import com.openpets.android.protocol.PetDescriptor
import com.openpets.android.protocol.DEFAULT_WAITING_ANIMATION_MS
import com.openpets.android.protocol.MediaRequest
import com.openpets.android.protocol.REACTIONS
import com.openpets.android.protocol.SELECTABLE_ANIMATIONS
import com.openpets.android.protocol.SayRequest
import com.openpets.android.protocol.resolveReactionSpriteState
import com.openpets.android.settings.AppSettings
import com.openpets.android.view.PetStageView
import java.io.File
import java.util.Locale

/**
 * Always-on companion surface. Shows the pet full-screen, keeps the screen on,
 * and drives it from the IPC/remote servers via [PetBus].
 *
 * Tap the pet to wave; long-press to open settings (ports, tokens, pet picker).
 */
class MainActivity : AppCompatActivity(), PetBus.Listener {

    private lateinit var stage: PetStageView
    private lateinit var settings: AppSettings
    private lateinit var repository: PetRepository
    private val catalogClient = CatalogClient()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var timerController: TimerController? = null
    private var focusController: FocusController? = null
    private var focusDialog: AlertDialog? = null
    private var focusRemainingView: TextView? = null
    private var focusDialogListener: ((FocusSnapshot) -> Unit)? = null
    private var focusDialogRender: ((FocusSnapshot) -> Unit)? = null
    private var renderedFocusKey: String? = null
    private var reminderController: ReminderController? = null
    private var reminderDialogListener: ((List<Reminder>) -> Unit)? = null
    private var reminderDialog: AlertDialog? = null
    private var reminderDialogRender: ((List<Reminder>) -> Unit)? = null
    private var virtualPetController: VirtualPetController? = null
    private var virtualPetDialog: AlertDialog? = null
    private var virtualPetDialogListener: ((PetStats) -> Unit)? = null
    private var virtualPetDialogRender: ((PetStats) -> Unit)? = null
    private var waterController: WaterController? = null
    private var waterDialog: AlertDialog? = null
    private var waterDialogListener: ((WaterSettings) -> Unit)? = null
    private var waterDialogRender: ((WaterSettings) -> Unit)? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerDialog: AlertDialog? = null
    private var timerRemainingView: TextView? = null
    private var timerLabelView: TextView? = null
    private var timerDialogListener: ((TimerSnapshot) -> Unit)? = null
    private var timerDialogRender: ((TimerSnapshot) -> Unit)? = null
    private var timerControls: LinearLayout? = null
    private var renderedTimerState: TimerRunState? = null
    private var activityStarted = false
    private val timerTick = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || !activityStarted) return
            val controller = timerController ?: return
            controller.refresh()
            val snapshot = controller.snapshot()
            if (snapshot.state == TimerRunState.RUNNING) {
                timerRemainingView?.text = TimerEngine.formatRemaining(snapshot.remainingMs)
                timerHandler.postDelayed(this, 1_000L)
            }
        }
    }
    private val focusTick = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || !activityStarted) return
            val controller = focusController ?: return
            controller.refresh()
            val snapshot = controller.snapshot()
            if (snapshot.state == FocusState.FOCUS || snapshot.state == FocusState.BREAK) {
                focusRemainingView?.text = TimerEngine.formatRemaining(snapshot.phaseRemainingMs)
                timerHandler.postDelayed(this, 1_000L)
            }
        }
    }

    private val importZipLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importPet { repository.importZip(it) } }
    }
    private val importFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { importPet { repository.importFolder(it) } }
    }
    private val customSoundLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            settings.customSoundUri = uri.toString()
            OpenPetsService.reload(this)
            Toast.makeText(this, "Som personalizado definido", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings = AppSettings(this)
        repository = PetRepository(this)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                try {
                    tts?.language = Locale.getDefault()
                } catch (_: Exception) {
                }
            }
        }

        stage = PetStageView(this).apply {
            setBackgroundColor(Color.BLACK)
            onTap = { react("waving") }
            onLongPress = { showSettings() }
        }
        setContentView(stage)
        timerController = TimerController(this)
        focusController = FocusController(this)
        reminderController = ReminderController(this)
        virtualPetController = VirtualPetController(this)
        waterController = WaterController(this)
        reloadPet()
        applyAnimationConfig()

        OpenPetsService.start(this)
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        AppState.foreground = true
        PetBus.register(this)
        timerController?.refresh()
        startTimerTickIfRunning()
        timerDialogListener?.let { timerController?.addListener(it) }
        reminderDialogListener?.let { reminderController?.addListener(it) }
        reminderController?.refresh()
        focusDialogListener?.let { focusController?.addListener(it) }
        focusController?.refresh()
        virtualPetDialogListener?.let { virtualPetController?.addListener(it) }
        virtualPetController?.refresh()
        waterDialogListener?.let { waterController?.addListener(it) }
        waterController?.refresh()
        startFocusTickIfRunning()
        if (timerDialog?.isShowing == true) timerController?.snapshot()?.let { timerDialogRender?.invoke(it) }
        if (focusDialog?.isShowing == true) focusController?.snapshot()?.let { focusDialogRender?.invoke(it) }
        if (reminderDialog?.isShowing == true) reminderDialogRender?.invoke(reminderController?.snapshot().orEmpty())
        if (virtualPetDialog?.isShowing == true) virtualPetController?.snapshot()?.let { virtualPetDialogRender?.invoke(it) }
        if (waterDialog?.isShowing == true) waterController?.settings()?.let { waterDialogRender?.invoke(it) }
        if (::stage.isInitialized) applyAnimationConfig()
    }

    override fun onStop() {
        activityStarted = false
        timerHandler.removeCallbacks(timerTick)
        timerHandler.removeCallbacks(focusTick)
        timerDialogListener?.let { timerController?.removeListener(it) }
        reminderDialogListener?.let { reminderController?.removeListener(it) }
        focusDialogListener?.let { focusController?.removeListener(it) }
        virtualPetDialogListener?.let { virtualPetController?.removeListener(it) }
        waterDialogListener?.let { waterController?.removeListener(it) }
        PetBus.unregister(this)
        AppState.foreground = false
        super.onStop()
    }

    override fun onReact(reaction: String, source: String?) {
        stage.react(reaction, source)
    }

    override fun onSay(request: SayRequest) {
        stage.say(request)
        if (settings.ttsEnabled) speak(request.message)
    }

    override fun onMedia(request: MediaRequest) {
        stage.showMedia(request)
    }

    override fun onDismissBubble(id: String?) {
        stage.dismissBubble(id)
    }

    override fun onHud(hud: FeatureHud) {
        if (::stage.isInitialized) stage.onHud(hud)
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openpets")
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    // ---- pet loading / selection ----

    private fun applyAnimationConfig() {
        stage.setAnimationConfig(settings.reactionOverrides, settings.waitingDurationMs)
    }

    private fun showAnimationSettings() {
        val overrides = settings.reactionOverrides.toMutableMap()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        REACTIONS.forEach { reaction ->
            val row = actionButton("") { }
            fun refresh() {
                val current = overrides[reaction] ?: resolveReactionSpriteState(reaction)
                row.text = "$reaction  →  $current"
            }
            refresh()
            row.setOnClickListener {
                val options = listOf("Padrão") + SELECTABLE_ANIMATIONS
                AlertDialog.Builder(this)
                    .setTitle("Animação para $reaction")
                    .setItems(options.toTypedArray()) { _, which ->
                        if (which == 0) overrides.remove(reaction) else overrides[reaction] = options[which]
                        refresh()
                    }
                    .show()
            }
            content.addView(row)
        }
        val waiting = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(settings.waitingDurationMs.toString())
        }
        content.addView(labelWithField("Duração 'waiting' (ms)", waiting))
        AlertDialog.Builder(this)
            .setTitle("Animações")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Salvar") { _, _ ->
                settings.reactionOverrides = overrides
                waiting.text.toString().toIntOrNull()?.let { settings.waitingDurationMs = it.coerceIn(200, 10_000) }
                applyAnimationConfig()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun reloadPet() {
        try {
            stage.bind(repository.loadPet(settings.selectedPetId))
        } catch (e: Exception) {
            try {
                stage.bind(PetPackageLoader.loadFromAssets(this))
            } catch (_: Exception) {
                // nothing else to do
            }
            Toast.makeText(this, "Falha ao carregar pet: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun currentPetLabel(): String =
        repository.listPets().firstOrNull { it.id == settings.selectedPetId }?.displayName ?: settings.selectedPetId

    private fun selectPet(pet: PetDescriptor) {
        settings.selectedPetId = pet.id
        reloadPet()
        OpenPetsService.reload(this)
        Toast.makeText(this, "Pet: ${pet.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun importPet(block: () -> PetDescriptor) {
        Toast.makeText(this, "Importando pet…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val descriptor = block()
                runOnUiThread {
                    settings.selectedPetId = descriptor.id
                    reloadPet()
                    OpenPetsService.reload(this)
                    Toast.makeText(this, "Pet importado: ${descriptor.displayName}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Falha ao importar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showPetPicker() {
        val pets = repository.listPets()
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        pets.forEach { pet ->
            val label = buildString {
                append(pet.displayName)
                when {
                    pet.builtIn -> append("  (embutido)")
                    repository.isImported(pet.id) -> append("  (importado)")
                }
                if (pet.id == settings.selectedPetId) append("  ✓")
            }
            content.addView(actionButton(label) {
                selectPet(pet)
                dialogRef[0]?.dismiss()
            })
        }
        content.addView(actionButton("Catálogo online…") {
            dialogRef[0]?.dismiss()
            showCatalogBrowser()
        })
        content.addView(actionButton("Importar .zip…") {
            dialogRef[0]?.dismiss()
            importZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        })
        content.addView(actionButton("Importar pasta…") {
            dialogRef[0]?.dismiss()
            importFolderLauncher.launch(null)
        })
        if (repository.isImported(settings.selectedPetId)) {
            content.addView(actionButton("Excluir pet importado") {
                val id = settings.selectedPetId
                if (repository.deleteImported(id)) {
                    settings.selectedPetId = "tux"
                    reloadPet()
                    OpenPetsService.reload(this)
                    Toast.makeText(this, "Pet importado removido", Toast.LENGTH_SHORT).show()
                }
                dialogRef[0]?.dismiss()
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pets")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Fechar", null)
            .create()
        dialogRef[0] = dialog
        dialog.show()
    }

    private fun showCatalogBrowser() {
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val search = EditText(this).apply { hint = "Buscar pet (nome/tag)" }
        val searchButton = actionButton("Buscar") { }
        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(6))
        }
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val prevButton = actionButton("◀ Anterior") { }
        val nextButton = actionButton("Próxima ▶") { }
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(prevButton)
            addView(nextButton)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(searchButton)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(searchRow)
            addView(status)
            addView(listContainer)
            addView(navRow)
        }

        var page = 1
        var totalPages = 1
        var query = ""

        fun load(target: Int) {
            status.text = "Carregando…"
            listContainer.removeAllViews()
            Thread {
                try {
                    val result = catalogClient.listPets(query, target)
                    runOnUiThread {
                        page = result.page
                        totalPages = result.totalPages
                        status.text = "Página $page/$totalPages · ${result.total} pets"
                        listContainer.removeAllViews()
                        if (result.pets.isEmpty()) {
                            listContainer.addView(sectionLabel("Nenhum pet encontrado."))
                        }
                        result.pets.forEach { pet ->
                            listContainer.addView(actionButton("${pet.displayName}  ·  ${pet.author}") {
                                downloadAndImport(pet) { dialogRef[0]?.dismiss() }
                            })
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Falha ao carregar: ${e.message}" }
                }
            }.start()
        }

        prevButton.setOnClickListener { if (page > 1) load(page - 1) }
        nextButton.setOnClickListener { if (page < totalPages) load(page + 1) }
        searchButton.setOnClickListener {
            query = search.text.toString().trim()
            load(1)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Catálogo · codex-pets.net")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Fechar", null)
            .create()
        dialogRef[0] = dialog
        dialog.show()
        load(1)
    }

    private fun downloadAndImport(pet: CatalogPet, onDone: () -> Unit) {
        Toast.makeText(this, "Baixando ${pet.displayName}…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val file = File(cacheDir, "catalog-${pet.id}.zip")
                catalogClient.downloadPet(pet, file)
                val descriptor = repository.importZip(Uri.fromFile(file))
                file.delete()
                runOnUiThread {
                    settings.selectedPetId = descriptor.id
                    reloadPet()
                    OpenPetsService.reload(this)
                    Toast.makeText(this, "Pet: ${descriptor.displayName}", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Falha ao baixar: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ---- settings ----

    private fun showSettings() {
        val ipcEnabled = CheckBox(this).apply { text = getString(R.string.settings_ipc); isChecked = settings.ipcEnabled }
        val remoteEnabled = CheckBox(this).apply { text = getString(R.string.settings_remote); isChecked = settings.remoteEnabled }
        val autoStart = CheckBox(this).apply { text = getString(R.string.settings_autostart); isChecked = settings.autoStartOnBoot }
        val soundToggle = CheckBox(this).apply { text = "Som de notificação"; isChecked = settings.soundEnabled }
        val hapticsToggle = CheckBox(this).apply { text = "Vibrar junto"; isChecked = settings.hapticsEnabled }
        val ttsToggle = CheckBox(this).apply { text = "Falar o balão (voz)"; isChecked = settings.ttsEnabled }
        val notifyToggle = CheckBox(this).apply { text = "Notificação do sistema"; isChecked = settings.notifyEnabled }
        val requireLeaseToggle = CheckBox(this).apply { text = "Exigir lease (leaseId obrigatório)"; isChecked = settings.requireLease }
        val bind = EditText(this).apply { setText(settings.bindAddress) }
        val ipcPort = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(settings.ipcPort.toString())
        }
        val remotePort = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(settings.remotePort.toString())
        }
        val ipcToken = EditText(this).apply { setText(settings.ipcToken) }
        val remoteToken = EditText(this).apply { setText(settings.remoteToken) }

        val deviceIp = AppSettings.findLanIpv4() ?: "unavailable"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(sectionLabel("Pet atual: ${currentPetLabel()}"))
            addView(actionButton("Alterar pet…") { showPetPicker() })
            addView(actionButton("Animações…") { showAnimationSettings() })
            addView(actionButton(getString(R.string.timer_open)) { showTimerDialog() })
            addView(actionButton(getString(R.string.reminders_open)) { showRemindersDialog() })
            addView(actionButton(getString(R.string.focus_open)) { showFocusDialog() })
            addView(actionButton(getString(R.string.virtualpet_open)) { showVirtualPetDialog() })
            addView(actionButton(getString(R.string.water_open)) { showWaterDialog() })
            addView(sectionLabel(getString(R.string.settings_device_ip) + ": $deviceIp"))
            addView(ipcEnabled)
            addView(remoteEnabled)
            addView(autoStart)
            addView(soundToggle)
            addView(actionButton("Testar som") { SoundNotifier(this@MainActivity).playTest() })
            addView(hapticsToggle)
            addView(ttsToggle)
            addView(notifyToggle)
            addView(requireLeaseToggle)
            addView(actionButton("Som personalizado…") { customSoundLauncher.launch(arrayOf("audio/*")) })
            addView(actionButton("Som padrão") {
                settings.customSoundUri = null
                OpenPetsService.reload(this@MainActivity)
            })
            addView(labelWithField(getString(R.string.settings_bind), bind))
            addView(labelWithField(getString(R.string.settings_ipc_port), ipcPort))
            addView(labelWithField(getString(R.string.settings_remote_port), remotePort))
            addView(labelWithField(getString(R.string.settings_ipc_token), ipcToken))
            addView(copyRow(ipcToken))
            addView(labelWithField(getString(R.string.settings_remote_token), remoteToken))
            addView(copyRow(remoteToken))
        }

        val scroll = ScrollView(this).apply { addView(content) }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(scroll)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                settings.ipcEnabled = ipcEnabled.isChecked
                settings.remoteEnabled = remoteEnabled.isChecked
                settings.autoStartOnBoot = autoStart.isChecked
                settings.soundEnabled = soundToggle.isChecked
                settings.hapticsEnabled = hapticsToggle.isChecked
                settings.ttsEnabled = ttsToggle.isChecked
                settings.notifyEnabled = notifyToggle.isChecked
                settings.requireLease = requireLeaseToggle.isChecked
                settings.bindAddress = bind.text.toString().trim().ifEmpty { "0.0.0.0" }
                ipcPort.text.toString().toIntOrNull()?.let { settings.ipcPort = it }
                remotePort.text.toString().toIntOrNull()?.let { settings.remotePort = it }
                settings.ipcToken = ipcToken.text.toString().trim()
                settings.remoteToken = remoteToken.text.toString().trim()
                OpenPetsService.reload(this)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun startTimerTickIfRunning() {
        timerHandler.removeCallbacks(timerTick)
        if (timerController?.snapshot()?.state == TimerRunState.RUNNING && activityStarted && !isFinishing) {
            timerHandler.postDelayed(timerTick, 1_000L)
        }
    }

    private fun showTimerDialog() {
        val controller = timerController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.timer_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        timerDialog = dialog
        timerControls = content

        fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) }
        fun fullButton(label: String, action: () -> Unit) = actionButton(label, action).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun styleField(field: EditText) {
            field.setTextColor(Color.WHITE)
            field.setHintTextColor(0xFF9E9E9E.toInt())
        }
        fun render(snapshot: TimerSnapshot) {
            renderedTimerState = snapshot.state
            content.removeAllViews()
            timerRemainingView = null
            timerLabelView = null
            when (snapshot.state) {
                TimerRunState.IDLE -> {
                    val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    listOf(5, 10, 25, 60).forEach { minutes ->
                        val button = actionButton("$minutes min") { controller.start(minutes * 60_000L) }
                        presets.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    val custom = EditText(this).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER
                        hint = getString(R.string.timer_custom_minutes)
                        styleField(this)
                    }
                    val label = EditText(this).apply {
                        hint = getString(R.string.timer_label)
                        styleField(this)
                    }
                    content.addView(sectionLabel(getString(R.string.timer_title)))
                    content.addView(presets)
                    content.addView(spacer())
                    content.addView(custom)
                    content.addView(spacer())
                    content.addView(label)
                    content.addView(spacer())
                    content.addView(fullButton(getString(R.string.timer_start)) {
                        val minutes = custom.text.toString().toIntOrNull()
                        if (minutes == null || minutes <= 0) {
                            Toast.makeText(this, getString(R.string.timer_custom_minutes), Toast.LENGTH_SHORT).show()
                        } else {
                            controller.start(minutes * 60_000L, label.text.toString().trim().ifEmpty { null })
                        }
                    })
                }
                TimerRunState.RUNNING, TimerRunState.PAUSED -> {
                    val remaining = TextView(this).apply {
                        text = TimerEngine.formatRemaining(snapshot.remainingMs)
                        textSize = 34f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(if (snapshot.state == TimerRunState.RUNNING) Color.WHITE else 0xFF9E9E9E.toInt())
                        setPadding(0, dp(12), 0, dp(8))
                    }
                    timerRemainingView = remaining
                    content.addView(remaining)
                    snapshot.label?.takeIf { it.isNotBlank() }?.let { text ->
                        val label = TextView(this).apply {
                            this.text = text
                            gravity = android.view.Gravity.CENTER
                            setTextColor(0xFF9E9E9E.toInt())
                        }
                        timerLabelView = label
                        content.addView(label)
                    }
                    content.addView(spacer())
                    content.addView(fullButton(getString(if (snapshot.state == TimerRunState.RUNNING) R.string.timer_pause else R.string.timer_resume)) {
                        if (snapshot.state == TimerRunState.RUNNING) controller.pause() else controller.resume()
                    })
                    content.addView(fullButton(getString(R.string.timer_add_five)) { controller.addMinutes(5) })
                    content.addView(fullButton(getString(R.string.timer_cancel)) { controller.cancel() })
                }
                TimerRunState.FINISHED -> {
                    content.addView(TextView(this).apply {
                        text = getString(R.string.timer_finished)
                        textSize = 22f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setPadding(0, dp(20), 0, dp(12))
                    })
                    snapshot.label?.takeIf { it.isNotBlank() }?.let { text ->
                        content.addView(TextView(this).apply {
                            this.text = text
                            gravity = android.view.Gravity.CENTER
                            setTextColor(0xFF9E9E9E.toInt())
                        })
                    }
                    content.addView(spacer())
                    content.addView(fullButton(getString(R.string.timer_dismiss)) { controller.dismissFinished() })
                }
            }
        }

        val listener: (TimerSnapshot) -> Unit = { snapshot ->
            if (snapshot.state != renderedTimerState) render(snapshot)
            startTimerTickIfRunning()
        }
        timerDialogListener?.let { controller.removeListener(it) }
        timerDialogListener = listener
        timerDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            timerDialogListener?.let { controller.removeListener(it) }
            timerDialogListener = null
            timerDialogRender = null
            timerDialog = null
            timerRemainingView = null
            timerLabelView = null
            timerControls = null
        }
        render(controller.snapshot())
        dialog.show()
        startTimerTickIfRunning()
    }

    private fun startFocusTickIfRunning() {
        timerHandler.removeCallbacks(focusTick)
        val state = focusController?.snapshot()?.state
        if ((state == FocusState.FOCUS || state == FocusState.BREAK) && activityStarted && !isFinishing) {
            timerHandler.postDelayed(focusTick, 1_000L)
        }
    }

    private fun showFocusDialog() {
        val controller = focusController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.focus_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        focusDialog = dialog

        fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) }
        fun fullButton(label: String, onClick: () -> Unit) = actionButton(label, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun render(snapshot: FocusSnapshot) {
            val key = "${snapshot.state}:${snapshot.pausedPhase}"
            renderedFocusKey = key
            content.removeAllViews()
            focusRemainingView = null
            when (snapshot.state) {
                FocusState.IDLE -> {
                    content.addView(sectionLabel(getString(R.string.focus_title)))
                    val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    listOf(25 to 5, 50 to 10).forEach { (focus, breakMinutes) ->
                        presets.addView(
                            actionButton("$focus/$breakMinutes") { controller.start(focus, breakMinutes) },
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                        )
                    }
                    val focusInput = EditText(this).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER
                        setText("25")
                        hint = getString(R.string.focus_focus_minutes)
                        setTextColor(Color.WHITE)
                        setHintTextColor(0xFF9E9E9E.toInt())
                    }
                    val breakInput = EditText(this).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER
                        setText("5")
                        hint = getString(R.string.focus_break_minutes)
                        setTextColor(Color.WHITE)
                        setHintTextColor(0xFF9E9E9E.toInt())
                    }
                    content.addView(presets)
                    content.addView(spacer())
                    content.addView(labelWithField(getString(R.string.focus_focus_minutes), focusInput))
                    content.addView(labelWithField(getString(R.string.focus_break_minutes), breakInput))
                    content.addView(spacer())
                    content.addView(fullButton(getString(R.string.focus_start)) {
                        val focus = focusInput.text.toString().toIntOrNull()
                        val breakValue = breakInput.text.toString().toIntOrNull()
                        if (focus == null || focus <= 0 || breakValue == null || breakValue <= 0) {
                            Toast.makeText(this, getString(R.string.focus_invalid), Toast.LENGTH_SHORT).show()
                        } else controller.start(focus, breakValue)
                    })
                }
                FocusState.FOCUS, FocusState.BREAK, FocusState.PAUSED -> {
                    val remaining = TextView(this).apply {
                        text = TimerEngine.formatRemaining(snapshot.phaseRemainingMs)
                        textSize = 34f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(when {
                            snapshot.state == FocusState.FOCUS -> Color.WHITE
                            snapshot.state == FocusState.BREAK -> 0xFF81C784.toInt()
                            else -> 0xFF9E9E9E.toInt()
                        })
                        setPadding(0, dp(12), 0, dp(8))
                    }
                    focusRemainingView = remaining
                    content.addView(remaining)
                    val statusText = when (snapshot.state) {
                        FocusState.FOCUS -> getString(R.string.focus_cycles, snapshot.cyclesCompleted)
                        FocusState.BREAK -> getString(R.string.focus_break_label)
                        FocusState.PAUSED -> getString(
                            if (snapshot.pausedPhase == com.openpets.android.companion.FocusPhase.BREAK) R.string.focus_paused_break else R.string.focus_paused_focus,
                        )
                        FocusState.IDLE -> ""
                    }
                    content.addView(TextView(this).apply {
                        text = statusText
                        gravity = android.view.Gravity.CENTER
                        setTextColor(0xFF9E9E9E.toInt())
                    })
                    content.addView(spacer())
                    if (snapshot.state == FocusState.PAUSED) {
                        content.addView(fullButton(getString(R.string.focus_resume)) { controller.resume() })
                    } else {
                        content.addView(fullButton(getString(R.string.focus_pause)) { controller.pause() })
                        content.addView(fullButton(getString(if (snapshot.state == FocusState.FOCUS) R.string.focus_skip_break else R.string.focus_skip_focus)) {
                            controller.skip()
                        })
                    }
                    content.addView(fullButton(getString(R.string.focus_end)) { controller.end() })
                }
            }
        }

        val listener: (FocusSnapshot) -> Unit = { snapshot ->
            val key = "${snapshot.state}:${snapshot.pausedPhase}"
            if (key != renderedFocusKey) render(snapshot)
            startFocusTickIfRunning()
        }
        focusDialogListener?.let { controller.removeListener(it) }
        focusDialogListener = listener
        focusDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            focusDialogListener?.let { controller.removeListener(it) }
            focusDialogListener = null
            focusDialogRender = null
            focusDialog = null
            focusRemainingView = null
            renderedFocusKey = null
        }
        render(controller.snapshot())
        dialog.show()
        startFocusTickIfRunning()
        controller.refresh()
    }

    private fun showVirtualPetDialog() {
        val controller = virtualPetController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.virtualpet_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        virtualPetDialog = dialog

        fun fullButton(text: String, action: () -> Unit) = actionButton(text, action).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun moodLabel(mood: PetMood): Int = when (mood) {
            PetMood.HAPPY -> R.string.virtualpet_mood_happy
            PetMood.CONTENT -> R.string.virtualpet_mood_content
            PetMood.TIRED -> R.string.virtualpet_mood_tired
            PetMood.HUNGRY -> R.string.virtualpet_mood_hungry
            PetMood.EXCITED -> R.string.virtualpet_mood_excited
        }
        fun statRow(label: Int, value: Int, color: Int) {
            content.addView(TextView(this).apply {
                text = "${getString(label)} · $value%"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, dp(6), 0, dp(2))
            })
            content.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = value.coerceIn(0, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(color)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
            })
        }
        fun render(stats: PetStats) {
            content.removeAllViews()
            content.addView(sectionLabel(getString(R.string.virtualpet_mood, getString(moodLabel(controller.mood())))))
            statRow(R.string.virtualpet_satiety, stats.satiety, 0xFF16A34A.toInt())
            statRow(R.string.virtualpet_happiness, stats.happiness, 0xFFDB2777.toInt())
            statRow(R.string.virtualpet_energy, stats.energy, 0xFF2563EB.toInt())
            val spacing = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) }
            content.addView(spacing)
            fun action(label: Int, perform: () -> Unit) {
                content.addView(fullButton(getString(label)) {
                    perform()
                    controller.showOnPet()
                })
            }
            action(R.string.virtualpet_feed) { controller.feed() }
            action(R.string.virtualpet_play) { controller.play() }
            action(R.string.virtualpet_pet) { controller.cuddle() }
            action(R.string.virtualpet_nap) { controller.rest() }
        }

        val listener: (PetStats) -> Unit = { render(it) }
        virtualPetDialogListener?.let { controller.removeListener(it) }
        virtualPetDialogListener = listener
        virtualPetDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            virtualPetDialogListener?.let { controller.removeListener(it) }
            virtualPetDialogListener = null
            virtualPetDialogRender = null
            virtualPetDialog = null
        }
        render(controller.snapshot())
        dialog.show()
        controller.refresh()
        controller.showOnPet()
    }

    private fun showWaterDialog() {
        val controller = waterController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.water_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        waterDialog = dialog

        fun fullButton(text: String, onClick: () -> Unit) = actionButton(text, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun render(settings: WaterSettings) {
            content.removeAllViews()
            val enabled = CheckBox(this).apply {
                text = getString(R.string.water_enabled)
                isChecked = settings.enabled
            }
            val interval = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.intervalMinutes.toString())
                setTextColor(Color.WHITE)
            }
            val from = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.fromHour.toString())
                setTextColor(Color.WHITE)
            }
            val to = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.toHour.toString())
                setTextColor(Color.WHITE)
            }
            content.addView(enabled)
            content.addView(labelWithField(getString(R.string.water_interval), interval))
            content.addView(labelWithField(getString(R.string.water_from), from))
            content.addView(labelWithField(getString(R.string.water_to), to))
            val next = WaterEngine().nextFire(System.currentTimeMillis(), settings)
            content.addView(sectionLabel(
                getString(
                    R.string.water_next,
                    next?.let { android.text.format.DateFormat.format("MMM d, HH:mm", it).toString() } ?: "—",
                ),
            ))
            val gap = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) }
            content.addView(gap)
            content.addView(fullButton(getString(R.string.water_save)) {
                val intervalValue = interval.text.toString().toIntOrNull()
                val fromValue = from.text.toString().toIntOrNull()
                val toValue = to.text.toString().toIntOrNull()
                if (intervalValue == null || intervalValue !in WaterEngine.MIN_INTERVAL_MINUTES..WaterEngine.MAX_INTERVAL_MINUTES ||
                    fromValue == null || fromValue !in 0..23 || toValue == null || toValue !in 0..23 || fromValue == toValue
                ) {
                    Toast.makeText(this, getString(R.string.water_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    controller.update(enabled.isChecked, intervalValue, fromValue, toValue)
                    Toast.makeText(this, getString(R.string.water_saved), Toast.LENGTH_SHORT).show()
                }
            })
        }

        val listener: (WaterSettings) -> Unit = { render(it) }
        waterDialogListener?.let { controller.removeListener(it) }
        waterDialogListener = listener
        waterDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            waterDialogListener?.let { controller.removeListener(it) }
            waterDialogListener = null
            waterDialogRender = null
            waterDialog = null
        }
        render(controller.settings())
        dialog.show()
        controller.refresh()
    }

    private fun showRemindersDialog() {
        val controller = reminderController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.reminders_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        fun spacer(height: Int = 8) = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }
        fun fullButton(text: String, onClick: () -> Unit) = actionButton(text, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun dueText(dueAtMs: Long, now: Long): String {
            val delta = dueAtMs - now
            return when {
                delta <= 0L -> "overdue"
                delta < 60 * 60_000L -> "in ${((delta + 59_999L) / 60_000L)} min"
                delta < 24 * 60 * 60_000L -> "in ${((delta + 3_599_999L) / 3_600_000L)} h"
                else -> DateFormat.format("MMM d, HH:mm", dueAtMs).toString()
            }
        }
        fun render(reminders: List<Reminder>) {
            content.removeAllViews()
            val title = EditText(this).apply {
                hint = getString(R.string.reminders_add_title)
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9E9E9E.toInt())
            }
            val minutes = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("10")
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9E9E9E.toInt())
                hint = getString(R.string.reminders_in_minutes)
            }
            content.addView(labelWithField(getString(R.string.reminders_add_title), title))
            content.addView(labelWithField(getString(R.string.reminders_in_minutes), minutes))
            content.addView(spacer())
            content.addView(fullButton(getString(R.string.reminders_add)) {
                val cleanTitle = title.text.toString().trim()
                val delay = minutes.text.toString().toIntOrNull()
                if (cleanTitle.isEmpty() || delay == null || delay <= 0) {
                    Toast.makeText(this, getString(R.string.reminders_invalid), Toast.LENGTH_SHORT).show()
                } else if (cleanTitle.length > 120) {
                    Toast.makeText(this, getString(R.string.reminders_title_too_long), Toast.LENGTH_SHORT).show()
                } else {
                    controller.addInMinutes(cleanTitle, delay)
                    title.text.clear()
                }
            })
            content.addView(spacer(12))

            if (reminders.isEmpty()) {
                content.addView(sectionLabel(getString(R.string.reminders_empty)))
            } else {
                val now = System.currentTimeMillis()
                reminders.forEachIndexed { index, reminder ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, dp(6), 0, dp(6))
                    }
                    val titleLine = TextView(this).apply {
                        text = reminder.title
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    val dueLine = TextView(this).apply {
                        text = dueText(reminder.dueAtMs, now)
                        textSize = 12f
                        setTextColor(0xFF9E9E9E.toInt())
                    }
                    row.addView(titleLine)
                    row.addView(dueLine)
                    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    listOf(
                        getString(R.string.reminders_done) to { controller.complete(reminder.id) },
                        getString(R.string.reminders_snooze) to { controller.snooze(reminder.id, 10) },
                        getString(R.string.reminders_delete) to { controller.remove(reminder.id) },
                    ).forEach { (label, action) ->
                        actions.addView(actionButton(label, action), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    row.addView(actions)
                    content.addView(row)
                    if (index < reminders.lastIndex) {
                        content.addView(View(this).apply {
                            setBackgroundColor(0x339E9E9E)
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                        })
                    }
                }
            }
        }

        val listener: (List<Reminder>) -> Unit = { render(it) }
        reminderDialogListener?.let { controller.removeListener(it) }
        reminderDialogListener = listener
        reminderDialogRender = { render(it) }
        reminderDialog = dialog
        controller.addListener(listener)
        dialog.setOnDismissListener {
            reminderDialogListener?.let { controller.removeListener(it) }
            reminderDialogListener = null
            reminderDialogRender = null
            reminderDialog = null
        }
        render(controller.snapshot())
        dialog.show()
        controller.refresh()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    // ---- view helpers ----

    private fun actionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun labelWithField(label: String, field: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, 0)
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 12f
            setTextColor(Color.DKGRAY)
        })
        addView(field)
    }

    private fun copyRow(field: EditText): View = Button(this).apply {
        text = getString(R.string.settings_copy)
        isAllCaps = false
        setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("openpets", field.text.toString()))
            Toast.makeText(this@MainActivity, getString(R.string.settings_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(8), 0, dp(2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
