package com.openpets.android

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import android.widget.LinearLayout.LayoutParams
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
import com.openpets.android.companion.DeadlineController
import com.openpets.android.companion.Deadline
import com.openpets.android.companion.FocusController
import com.openpets.android.companion.SttController
import com.openpets.android.companion.SttState
import com.openpets.android.companion.FocusSnapshot
import com.openpets.android.companion.FocusState
import com.openpets.android.companion.PetMood
import com.openpets.android.companion.PetStats
import com.openpets.android.companion.VirtualPetController
import com.openpets.android.companion.WaterController
import com.openpets.android.companion.WaterEngine
import com.openpets.android.companion.WaterSettings
import com.openpets.android.companion.MoodController
import com.openpets.android.companion.MoodEntry
import com.openpets.android.companion.MoodLevel
import com.openpets.android.companion.level
import com.openpets.android.companion.RoutineController
import com.openpets.android.companion.RoutineSettings
import com.openpets.android.companion.RoutineEngine
import com.openpets.android.companion.BreathingController
import com.openpets.android.companion.BreathSnapshot
import com.openpets.android.companion.BreathPhase
import com.openpets.android.companion.BreathingPattern
import com.openpets.android.companion.SystemResourcesController
import com.openpets.android.companion.DeviceResources
import com.openpets.android.companion.SystemResourcesFormatter
import com.openpets.android.companion.CalendarController
import com.openpets.android.companion.DeviceEvent
import com.openpets.android.companion.CalendarFormat
import com.openpets.android.companion.ChecklistController
import com.openpets.android.companion.ChecklistItem
import com.openpets.android.net.CommandRegistry
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
import com.openpets.android.protocol.MoveRequest
import com.openpets.android.protocol.resolveReactionSpriteState
import com.openpets.android.settings.AppSettings
import com.openpets.android.vocab.VocabularyClient
import com.openpets.android.vocab.VocabDefinition
import com.openpets.android.view.PetStageView
import java.io.File
import java.util.Locale
import java.util.Calendar

/**
 * Always-on companion surface. Shows the pet full-screen, keeps the screen on,
 * and drives it from the IPC/remote servers via [PetBus].
 *
 * Tap the pet to wave; long-press to open settings (ports, tokens, pet picker).
 */
class MainActivity : AppCompatActivity(), PetBus.Listener {
    private var sttController: SttController? = null

    private lateinit var stage: PetStageView
    private lateinit var settings: AppSettings
    private lateinit var repository: PetRepository
    private val catalogClient = CatalogClient()
    private val vocabularyClient = VocabularyClient()
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
    private var moodController: MoodController? = null
    private var moodDialog: AlertDialog? = null
    private var moodDialogListener: ((List<MoodEntry>) -> Unit)? = null
    private var moodDialogRender: ((List<MoodEntry>) -> Unit)? = null
    private var routineController: RoutineController? = null
    private var routineDialog: AlertDialog? = null
    private var routineDialogListener: ((RoutineSettings) -> Unit)? = null
    private var routineDialogRender: ((RoutineSettings) -> Unit)? = null
    private var breathingController: BreathingController? = null
    private var breathingDialog: AlertDialog? = null
    private var breathingPhaseView: TextView? = null
    private var breathingDialogListener: ((BreathSnapshot) -> Unit)? = null
    private var breathingDialogRender: ((BreathSnapshot) -> Unit)? = null
    private var renderedBreathingKey: String? = null
    private var resourcesController: SystemResourcesController? = null
    private var resourcesDialog: AlertDialog? = null
    private var resourcesDialogListener: ((DeviceResources) -> Unit)? = null
    private var resourcesDialogRender: ((DeviceResources) -> Unit)? = null
    private var calendarController: CalendarController? = null
    private var calendarDialog: AlertDialog? = null
    private var calendarDialogListener: ((List<DeviceEvent>) -> Unit)? = null
    private var calendarDialogRender: ((List<DeviceEvent>) -> Unit)? = null
    private var checklistController: ChecklistController? = null
    private var checklistDialog: AlertDialog? = null
    private var checklistDialogListener: ((List<ChecklistItem>) -> Unit)? = null
    private var checklistDialogRender: ((List<ChecklistItem>) -> Unit)? = null
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
    private val breathingTick = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || !activityStarted) return
            val controller = breathingController ?: return
            controller.refresh()
            val snapshot = controller.snapshot()
            if (snapshot.active) {
                breathingPhaseView?.text = breathingPhaseText(snapshot)
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
        moodController = MoodController(this)
        routineController = RoutineController(this)
        breathingController = BreathingController(this)
        resourcesController = SystemResourcesController(this)
        calendarController = CalendarController(this)
        checklistController = ChecklistController(this)
        reloadPet()
        applyAnimationConfig()
        sharedText()?.let { stage.post { showVocabDialog(it) } }

        OpenPetsService.start(this)
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.content.Intent.ACTION_SEND) {
            intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::showVocabDialog)
        }
    }

    private fun sharedText(): String? = if (intent?.action == android.content.Intent.ACTION_SEND) {
        intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    } else null

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
        moodDialogListener?.let { moodController?.addListener(it) }
        moodController?.refresh()
        routineDialogListener?.let { routineController?.addListener(it) }
        routineController?.refresh()
        breathingDialogListener?.let { breathingController?.addListener(it) }
        breathingController?.refresh()
        resourcesDialogListener?.let { resourcesController?.addListener(it) }
        resourcesController?.refresh()
        calendarDialogListener?.let { calendarController?.addListener(it) }
        checklistDialogListener?.let { checklistController?.addListener(it) }
        checklistController?.refresh()
        calendarController?.refresh()
        startFocusTickIfRunning()
        startBreathingTickIfActive()
        if (timerDialog?.isShowing == true) timerController?.snapshot()?.let { timerDialogRender?.invoke(it) }
        if (focusDialog?.isShowing == true) focusController?.snapshot()?.let { focusDialogRender?.invoke(it) }
        if (reminderDialog?.isShowing == true) reminderDialogRender?.invoke(reminderController?.snapshot().orEmpty())
        if (virtualPetDialog?.isShowing == true) virtualPetController?.snapshot()?.let { virtualPetDialogRender?.invoke(it) }
        if (waterDialog?.isShowing == true) waterController?.settings()?.let { waterDialogRender?.invoke(it) }
        if (moodDialog?.isShowing == true) moodController?.recent()?.let { moodDialogRender?.invoke(it) }
        if (routineDialog?.isShowing == true) routineController?.settings()?.let { routineDialogRender?.invoke(it) }
        if (breathingDialog?.isShowing == true) breathingController?.snapshot()?.let { breathingDialogRender?.invoke(it) }
        if (resourcesDialog?.isShowing == true) resourcesController?.snapshot()?.let { resourcesDialogRender?.invoke(it) }
        if (calendarDialog?.isShowing == true) calendarDialogRender?.invoke(calendarController?.upcoming().orEmpty())
        if (checklistDialog?.isShowing == true) checklistDialogRender?.invoke(checklistController?.items().orEmpty())
        if (::stage.isInitialized) applyAnimationConfig()
    }

    override fun onStop() {
        activityStarted = false
        timerHandler.removeCallbacks(timerTick)
        timerHandler.removeCallbacks(focusTick)
        timerHandler.removeCallbacks(breathingTick)
        timerDialogListener?.let { timerController?.removeListener(it) }
        reminderDialogListener?.let { reminderController?.removeListener(it) }
        focusDialogListener?.let { focusController?.removeListener(it) }
        virtualPetDialogListener?.let { virtualPetController?.removeListener(it) }
        waterDialogListener?.let { waterController?.removeListener(it) }
        moodDialogListener?.let { moodController?.removeListener(it) }
        routineDialogListener?.let { routineController?.removeListener(it) }
        breathingDialogListener?.let { breathingController?.removeListener(it) }
        resourcesDialogListener?.let { resourcesController?.removeListener(it) }
        calendarDialogListener?.let { calendarController?.removeListener(it) }
        checklistDialogListener?.let { checklistController?.removeListener(it) }
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

    override fun onAction(event: PetActionEvent) {}
    override fun onInputText(text: String) {}
    override fun onMove(request: MoveRequest) { if (::stage.isInitialized) stage.movePet(request) }
    override fun onScale(scale: Float) { if (::stage.isInitialized) stage.applyPetScale(scale) }
    override fun onVisibility(visible: Boolean) { if (::stage.isInitialized) stage.setPetVisible(visible) }
    override fun onPointer(kind: String, x: Float, y: Float) {}
    override fun onNotify(request: com.openpets.android.protocol.NotificationRequest) {}
    override fun onCommandInvoked(event: CommandInvoked) {}
    override fun onCommandsChanged() {}

    private fun speak(text: String) {
        if (!ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openpets")
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        sttController?.destroy()
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
        stage.applyWalkabout(settings.walkaboutMode, settings.walkaboutSpeed)
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

    private fun showDeadlineDialog() {
        val controller = com.openpets.android.companion.CompanionRuntime.deadline
        val title = EditText(this).apply { hint = "Title" }
        val due = java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR, 1) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(sectionLabel("Add deadline")); root.addView(title)
        root.addView(actionButton(getString(R.string.deadline_date)) { DatePickerDialog(this, { _, y, m, d -> due.set(y,m,d) }, due.get(java.util.Calendar.YEAR), due.get(java.util.Calendar.MONTH), due.get(java.util.Calendar.DAY_OF_MONTH)).show() })
        root.addView(actionButton(getString(R.string.deadline_time)) { TimePickerDialog(this, { _, h, m -> due.set(java.util.Calendar.HOUR_OF_DAY,h); due.set(java.util.Calendar.MINUTE,m) }, due.get(java.util.Calendar.HOUR_OF_DAY), due.get(java.util.Calendar.MINUTE), DateFormat.is24HourFormat(this)).show() })
        root.addView(actionButton(getString(R.string.deadline_add)) { runCatching { controller.add(title.text.toString(), due.timeInMillis); title.text.clear() }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() } })
        root.addView(sectionLabel(getString(R.string.deadline_title))); root.addView(list)
        var dialog: AlertDialog? = null
        val render: (List<Deadline>) -> Unit = { entries -> runOnUiThread { list.removeAllViews(); val nearest=entries.firstOrNull(); list.addView(sectionLabel(nearest?.let { getString(R.string.deadline_nearest, it.title) } ?: getString(R.string.deadline_empty))); entries.forEach { item -> val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; row.addView(sectionLabel("${item.title} — ${if(item.dueAtMs < System.currentTimeMillis()) "overdue" else "${(item.dueAtMs-System.currentTimeMillis())/60000} min"}")); row.addView(actionButton(getString(R.string.deadline_complete)) { controller.complete(item.id) }); row.addView(actionButton(getString(R.string.deadline_delete)) { controller.remove(item.id) }); list.addView(row) } } }
        controller.addListener(render)
        dialog = AlertDialog.Builder(this).setTitle(R.string.deadline_title).setView(root).setNegativeButton(R.string.dialog_close, null).create()
        dialog.setOnDismissListener { controller.removeListener(render) }; dialog.show(); controller.refresh(); render(controller.upcoming())
    }

    private fun showChecklistDialog() {
        val controller = checklistController ?: return
        val itemField = EditText(this).apply { hint = getString(R.string.checklist_item) }
        val greetingField = EditText(this).apply { hint = "Leave blank for the time-of-day greeting"; setText(settings.launchGreeting) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        root.addView(sectionLabel(getString(R.string.checklist_item))); root.addView(itemField)
        root.addView(actionButton(getString(R.string.checklist_add)) { runCatching { controller.add(itemField.text.toString()); itemField.text.clear() }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() } })
        root.addView(sectionLabel(getString(R.string.checklist_title))); root.addView(list)
        root.addView(actionButton(getString(R.string.checklist_clear)) { controller.clearCompleted() })
        root.addView(actionButton(getString(R.string.checklist_launch)) { settings.launchGreeting = greetingField.text.toString(); controller.launch() })
        root.addView(labelWithField(getString(R.string.checklist_greeting), greetingField))
        val render: (List<ChecklistItem>) -> Unit = { entries -> runOnUiThread {
            list.removeAllViews()
            if (entries.isEmpty()) list.addView(sectionLabel(getString(R.string.checklist_empty)))
            entries.forEach { item ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                row.addView(sectionLabel(item.label))
                row.addView(actionButton(getString(if (item.done) R.string.checklist_undo else R.string.checklist_done)) { controller.toggle(item.id) })
                row.addView(actionButton(getString(R.string.checklist_remove)) { controller.remove(item.id) })
                list.addView(row)
            }
        } }
        checklistDialogRender = render
        checklistDialogListener = render
        controller.addListener(render)
        checklistDialog = AlertDialog.Builder(this).setTitle(R.string.checklist_title).setView(root).setNegativeButton(R.string.dialog_close, null).create()
        checklistDialog?.setOnDismissListener { controller.removeListener(render); settings.launchGreeting = greetingField.text.toString(); checklistDialog = null; checklistDialogListener = null; checklistDialogRender = null }
        checklistDialog?.show()
        controller.refresh()
        render(controller.items())
    }

    private fun showWalkaboutDialog() {
        val speed = EditText(this).apply {
            hint = "0.5–3.0"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settings.walkaboutSpeed.toString())
        }
        var selectedMode = settings.walkaboutMode
        val modes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        root.addView(sectionLabel(getString(R.string.walkabout_title)))
        fun renderModes() {
            modes.removeAllViews()
            listOf("off" to R.string.walkabout_off, "roam" to R.string.walkabout_roam, "patrol" to R.string.walkabout_patrol).forEach { (mode, label) ->
                val text = getString(label) + if (selectedMode == mode) " ✓" else ""
                modes.addView(actionButton(text) { selectedMode = mode; renderModes() })
            }
        }
        renderModes()
        root.addView(modes)
        root.addView(labelWithField(getString(R.string.walkabout_speed), speed))
        val dialog = AlertDialog.Builder(this).setTitle(R.string.walkabout_title).setView(root)
            .setPositiveButton(R.string.settings_save, null)
            .setNegativeButton(R.string.dialog_close, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = speed.text.toString().toFloatOrNull()
                if (parsed == null || parsed !in 0.5f..3.0f) {
                    Toast.makeText(this, "Enter a speed from 0.5 to 3.0", Toast.LENGTH_SHORT).show()
                } else {
                    settings.walkaboutMode = selectedMode
                    settings.walkaboutSpeed = parsed
                    stage.applyWalkabout(settings.walkaboutMode, settings.walkaboutSpeed)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showVocabSettingsDialog() {
        val language = EditText(this).apply { setText(settings.vocabTargetLang) }
        val host = EditText(this).apply { hint = "host or host:port"; setText(settings.ankiHost) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            addView(labelWithField(getString(R.string.vocab_target_lang), language))
            addView(labelWithField(getString(R.string.vocab_anki_host), host))
        }
        AlertDialog.Builder(this).setTitle(R.string.vocab_title).setView(content)
            .setPositiveButton(R.string.settings_save) { _, _ -> settings.vocabTargetLang = language.text.toString(); settings.ankiHost = host.text.toString() }
            .setNegativeButton(R.string.dialog_close, null).show()
    }

    private fun showVocabDialog(text: String) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val word = text.trim()
        val definitions = TextView(this)
        val translation = TextView(this)
        val defineButton = actionButton(getString(R.string.vocab_define)) {
            definitions.text = "…"
            vocabularyClient.define(word, { values ->
                definitions.text = values.take(4).joinToString("\n") { "${it.partOfSpeech?.let { pos -> "$pos — " } ?: ""}${it.definition}" }.ifEmpty { getString(R.string.vocab_error) }
            }, { definitions.text = "${getString(R.string.vocab_error)}: $it" })
        }
        val translateButton = actionButton(getString(R.string.vocab_translate)) {
            translation.text = "…"
            vocabularyClient.translate(word, "auto", settings.vocabTargetLang, { translation.text = it }, { translation.text = "${getString(R.string.vocab_error)}: $it" })
        }
        val ankiButton = actionButton(getString(R.string.vocab_anki)) {
            val back = definitions.text.toString().ifBlank { translation.text.toString() }
            vocabularyClient.addToAnki(settings.ankiHost, word, back) { ok -> Toast.makeText(this, if (ok) getString(R.string.vocab_sent) else getString(R.string.vocab_error), Toast.LENGTH_SHORT).show() }
        }.apply { isEnabled = settings.ankiHost.isNotBlank() }
        content.addView(sectionLabel(word))
        content.addView(defineButton); content.addView(definitions)
        content.addView(translateButton); content.addView(translation)
        content.addView(actionButton(getString(R.string.vocab_show)) {
            val message = definitions.text.toString().takeIf { it.isNotBlank() && it != "…" } ?: translation.text.toString().takeIf { it.isNotBlank() && it != "…" } ?: word
            PetBus.sink.onSay(SayRequest(message = message, reaction = null, source = "vocab", bubbleId = "vocab", pinned = false))
            Toast.makeText(this, getString(R.string.vocab_sent), Toast.LENGTH_SHORT).show()
        })
        content.addView(ankiButton)
        AlertDialog.Builder(this).setTitle(R.string.vocab_title).setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null).show()
    }

    private fun showCommandsDialog() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val commands = CommandRegistry.list()
        if (commands.isEmpty()) content.addView(sectionLabel(getString(R.string.commands_empty)))
        commands.forEach { command ->
            val button = actionButton("${command.title}\n${command.source}") { PetBus.invokeCommand(command.source, command.id) }
            content.addView(button)
            command.description?.takeIf { it.isNotBlank() }?.let { content.addView(sectionLabel(it)) }
        }
        AlertDialog.Builder(this).setTitle(R.string.commands_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null).show()
    }

    private fun showSettings() {
        val ipcEnabled = CheckBox(this).apply { text = getString(R.string.settings_ipc); isChecked = settings.ipcEnabled }
        val remoteEnabled = CheckBox(this).apply { text = getString(R.string.settings_remote); isChecked = settings.remoteEnabled }
        val wsEnabled = CheckBox(this).apply { text = getString(R.string.settings_ws); isChecked = settings.wsEnabled }
        val hostStatus = CheckBox(this).apply { text = getString(R.string.settings_host_status); isChecked = settings.hostStatusEnabled }
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
        val wsPort = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(settings.wsPort.toString()) }
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
            addView(actionButton(getString(R.string.deadline_open)) { showDeadlineDialog() })
            addView(actionButton(getString(R.string.focus_open)) { showFocusDialog() })
            addView(actionButton(getString(R.string.virtualpet_open)) { showVirtualPetDialog() })
            addView(actionButton(getString(R.string.water_open)) { showWaterDialog() })
            addView(actionButton(getString(R.string.fun_open)) { showFunDialog() })
            addView(actionButton(getString(R.string.mood_open)) { showMoodDialog() })
            addView(actionButton(getString(R.string.routine_open)) { showRoutineDialog() })
            addView(actionButton(getString(R.string.breathe_open)) { showBreathingDialog() })
            addView(actionButton(getString(R.string.resources_open)) { showResourcesDialog() })
            addView(actionButton(getString(R.string.calendar_open)) { showCalendarDialog() })
            addView(actionButton(getString(R.string.checklist_open)) { showChecklistDialog() })
            addView(actionButton(getString(R.string.walkabout_open)) { showWalkaboutDialog() })
            addView(actionButton(getString(R.string.vocab_open)) { showVocabSettingsDialog() })
            addView(actionButton(getString(R.string.commands_open)) { showCommandsDialog() })
            addView(actionButton(getString(R.string.settings_voice)) { showVoiceDialog() })
            addView(sectionLabel(getString(R.string.settings_device_ip) + ": $deviceIp"))
            addView(ipcEnabled)
            addView(remoteEnabled)
            addView(wsEnabled)
            addView(hostStatus)
            addView(labelWithField(getString(R.string.settings_ws_port), wsPort))
            addView(sectionLabel(getString(R.string.settings_ws_hint)))
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
                settings.wsEnabled = wsEnabled.isChecked
                settings.hostStatusEnabled = hostStatus.isChecked
                settings.autoStartOnBoot = autoStart.isChecked
                settings.soundEnabled = soundToggle.isChecked
                settings.hapticsEnabled = hapticsToggle.isChecked
                settings.ttsEnabled = ttsToggle.isChecked
                settings.notifyEnabled = notifyToggle.isChecked
                settings.requireLease = requireLeaseToggle.isChecked
                settings.bindAddress = bind.text.toString().trim().ifEmpty { "0.0.0.0" }
                ipcPort.text.toString().toIntOrNull()?.let { settings.ipcPort = it }
                remotePort.text.toString().toIntOrNull()?.let { settings.remotePort = it }
                wsPort.text.toString().toIntOrNull()?.let { settings.wsPort = it }
                settings.ipcToken = ipcToken.text.toString().trim()
                settings.remoteToken = remoteToken.text.toString().trim()
                OpenPetsService.reload(this)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showCalendarDialog() {
        val controller = calendarController ?: return
        if (!controller.hasPermission()) {
            AlertDialog.Builder(this).setTitle(R.string.calendar_title).setMessage(R.string.calendar_permission_rationale)
                .setPositiveButton(R.string.calendar_grant) { _, _ -> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 1002) }
                .setNegativeButton(R.string.dialog_close, null).show()
            return
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.calendar_title).setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null).create()
        calendarDialog = dialog
        fun render(events: List<DeviceEvent>) {
            content.removeAllViews()
            if (events.isEmpty()) content.addView(sectionLabel(getString(R.string.calendar_empty)))
            events.forEach { content.addView(sectionLabel("${it.title} · ${CalendarFormat.relativeTime(it.beginMs, System.currentTimeMillis())}")) }
            content.addView(actionButton(getString(R.string.calendar_refresh)) { controller.refresh() })
            content.addView(actionButton(getString(R.string.calendar_show)) { controller.showOnPet() })
            content.addView(CheckBox(this).apply { text = getString(R.string.calendar_remind); isChecked = controller.enabled(); setOnCheckedChangeListener { _, checked -> controller.setEnabled(checked) } })
        }
        val listener: (List<DeviceEvent>) -> Unit = { render(it) }
        calendarDialogListener?.let { controller.removeListener(it) }
        calendarDialogListener = listener; calendarDialogRender = { render(it) }; controller.addListener(listener)
        dialog.setOnDismissListener { calendarDialogListener?.let { controller.removeListener(it) }; calendarDialogListener = null; calendarDialogRender = null; calendarDialog = null }
        render(controller.upcoming()); dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showCalendarDialog()
        if (requestCode == 1003) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showVoiceDialog()
            else Toast.makeText(this, R.string.voice_permission, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVoiceDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1003)
            return
        }
        val status = TextView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)); addView(status) }
        val button = Button(this).apply { text = getString(R.string.voice_speak) }
        content.addView(button)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.voice_title).setView(content).setNegativeButton(R.string.dialog_close, null).create()
        val controller = sttController ?: SttController(this, { text ->
            PetBus.inputText(text)
            runOnUiThread { Toast.makeText(this, R.string.voice_sent, Toast.LENGTH_SHORT).show() }
        }, { state -> runOnUiThread { status.text = when (state) {
            SttState.IDLE -> ""
            SttState.LISTENING -> getString(R.string.voice_listening)
            SttState.ERROR -> getString(R.string.voice_error)
            SttState.UNAVAILABLE -> getString(R.string.voice_unavailable)
        } } }).also { sttController = it }
        var listening = false
        button.setOnClickListener {
            if (listening) { listening = false; controller.stop() }
            else { listening = true; controller.start() }
        }
        dialog.setOnDismissListener { controller.cancel() }
        dialog.show()
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

    private fun startBreathingTickIfActive() {
        timerHandler.removeCallbacks(breathingTick)
        if (breathingController?.snapshot()?.active == true && activityStarted && !isFinishing) {
            timerHandler.postDelayed(breathingTick, 1_000L)
        }
    }

    private fun breathingPhaseText(snapshot: BreathSnapshot): String {
        val label = when (snapshot.phase) {
            BreathPhase.INHALE -> getString(R.string.breathe_in)
            BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> getString(R.string.breathe_hold)
            BreathPhase.EXHALE -> getString(R.string.breathe_out)
            BreathPhase.DONE -> getString(R.string.breathe_done)
        }
        return "$label · ${snapshot.phaseRemainingSec}s"
    }

    private fun showResourcesDialog() {
        val controller = resourcesController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.resources_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        resourcesDialog = dialog

        fun render(resources: DeviceResources) {
            content.removeAllViews()
            val unavailable = getString(R.string.resources_unavailable)
            val battery = resources.batteryPercent?.let { "$it%" } ?: unavailable
            val charging = resources.charging?.let { if (it) getString(R.string.resources_charging) else "Not charging" } ?: unavailable
            content.addView(sectionLabel("${getString(R.string.resources_battery)}: $battery"))
            content.addView(sectionLabel("${getString(R.string.resources_charging)}: $charging"))
            content.addView(sectionLabel(
                "${getString(R.string.resources_memory)}: ${SystemResourcesFormatter.megabytes(resources.memoryUsedMb)} / ${SystemResourcesFormatter.megabytes(resources.memoryTotalMb)}",
            ))
            content.addView(sectionLabel(
                "${getString(R.string.resources_storage)}: ${SystemResourcesFormatter.megabytes(resources.storageUsedMb)} / ${SystemResourcesFormatter.megabytes(resources.storageTotalMb)}",
            ))
            content.addView(sectionLabel("${getString(R.string.resources_network)}: ${resources.networkType ?: unavailable}"))
            resources.batteryTempC?.let { content.addView(sectionLabel("Battery temperature: %.1f °C".format(it))) }
            content.addView(actionButton(getString(R.string.resources_refresh)) { controller.refresh() })
            content.addView(actionButton(getString(R.string.resources_show)) { controller.showOnPet() })
        }

        val listener: (DeviceResources) -> Unit = { render(it) }
        resourcesDialogListener?.let { controller.removeListener(it) }
        resourcesDialogListener = listener
        resourcesDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            resourcesDialogListener?.let { controller.removeListener(it) }
            resourcesDialogListener = null
            resourcesDialogRender = null
            resourcesDialog = null
        }
        render(controller.snapshot())
        dialog.show()
        controller.refresh()
    }

    private fun showBreathingDialog() {
        val controller = breathingController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.breathe_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        breathingDialog = dialog
        fun fullButton(label: String, onClick: () -> Unit) = actionButton(label, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun render(snapshot: BreathSnapshot) {
            val key = "${snapshot.active}:${snapshot.patternId}:${snapshot.phase}"
            renderedBreathingKey = key
            content.removeAllViews()
            breathingPhaseView = null
            if (snapshot.active) {
                val phase = TextView(this).apply {
                    text = breathingPhaseText(snapshot)
                    textSize = 30f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(20), 0, dp(20))
                }
                breathingPhaseView = phase
                content.addView(phase)
                content.addView(fullButton(getString(R.string.breathe_stop)) { controller.stop() })
            } else {
                content.addView(sectionLabel(getString(R.string.breathe_title)))
                val minutes = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText("3")
                    hint = getString(R.string.breathe_minutes)
                    setTextColor(Color.WHITE)
                    setHintTextColor(0xFF9E9E9E.toInt())
                }
                content.addView(labelWithField(getString(R.string.breathe_minutes), minutes))
                content.addView(fullButton(getString(R.string.breathe_box)) {
                    val duration = minutes.text.toString().toIntOrNull()
                    if (duration == null || duration <= 0) Toast.makeText(this, getString(R.string.breathe_invalid), Toast.LENGTH_SHORT).show()
                    else {
                        controller.start(BreathingPattern.BOX.id, duration)
                        startBreathingTickIfActive()
                    }
                })
                content.addView(fullButton(getString(R.string.breathe_478)) {
                    val duration = minutes.text.toString().toIntOrNull()
                    if (duration == null || duration <= 0) Toast.makeText(this, getString(R.string.breathe_invalid), Toast.LENGTH_SHORT).show()
                    else {
                        controller.start(BreathingPattern.RELAX_478.id, duration)
                        startBreathingTickIfActive()
                    }
                })
            }
        }
        val listener: (BreathSnapshot) -> Unit = { snapshot ->
            val key = "${snapshot.active}:${snapshot.patternId}:${snapshot.phase}"
            if (key != renderedBreathingKey) render(snapshot)
            startBreathingTickIfActive()
        }
        breathingDialogListener?.let { controller.removeListener(it) }
        breathingDialogListener = listener
        breathingDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            breathingDialogListener?.let { controller.removeListener(it) }
            breathingDialogListener = null
            breathingDialogRender = null
            breathingDialog = null
            breathingPhaseView = null
            renderedBreathingKey = null
        }
        render(controller.snapshot())
        dialog.show()
        controller.refresh()
        startBreathingTickIfActive()
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

    private fun showRoutineDialog() {
        val controller = routineController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.routine_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        routineDialog = dialog

        fun fullButton(label: String, action: () -> Unit) = actionButton(label, action).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        fun render(settings: RoutineSettings) {
            content.removeAllViews()
            val morningEnabled = CheckBox(this).apply {
                text = getString(R.string.routine_enabled)
                isChecked = settings.morningEnabled
            }
            val morningHour = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.morningHour.toString())
                setTextColor(Color.WHITE)
            }
            val morningMinute = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.morningMinute.toString())
                setTextColor(Color.WHITE)
            }
            content.addView(sectionLabel(getString(R.string.routine_morning)))
            content.addView(morningEnabled)
            content.addView(labelWithField(getString(R.string.routine_hour), morningHour))
            content.addView(labelWithField(getString(R.string.routine_minute), morningMinute))

            val eveningEnabled = CheckBox(this).apply {
                text = getString(R.string.routine_enabled)
                isChecked = settings.eveningEnabled
            }
            val eveningHour = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.eveningHour.toString())
                setTextColor(Color.WHITE)
            }
            val eveningMinute = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(settings.eveningMinute.toString())
                setTextColor(Color.WHITE)
            }
            content.addView(sectionLabel(getString(R.string.routine_evening)))
            content.addView(eveningEnabled)
            content.addView(labelWithField(getString(R.string.routine_hour), eveningHour))
            content.addView(labelWithField(getString(R.string.routine_minute), eveningMinute))

            val next = RoutineEngine().nextFire(System.currentTimeMillis(), settings)
            content.addView(sectionLabel(
                getString(
                    R.string.routine_next,
                    next?.let { android.text.format.DateFormat.format("MMM d, HH:mm", it).toString() } ?: "—",
                ),
            ))
            content.addView(View(this).apply { layoutParams = LayoutParams(1, dp(8)) })
            content.addView(fullButton(getString(R.string.routine_save)) {
                val mh = morningHour.text.toString().toIntOrNull()
                val mm = morningMinute.text.toString().toIntOrNull()
                val eh = eveningHour.text.toString().toIntOrNull()
                val em = eveningMinute.text.toString().toIntOrNull()
                if (mh == null || mh !in 0..23 || mm == null || mm !in 0..59 ||
                    eh == null || eh !in 0..23 || em == null || em !in 0..59
                ) {
                    Toast.makeText(this, getString(R.string.routine_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    controller.update(morningEnabled.isChecked, mh, mm, eveningEnabled.isChecked, eh, em)
                    Toast.makeText(this, getString(R.string.routine_saved), Toast.LENGTH_SHORT).show()
                }
            })
        }

        val listener: (RoutineSettings) -> Unit = { render(it) }
        routineDialogListener?.let { controller.removeListener(it) }
        routineDialogListener = listener
        routineDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            routineDialogListener?.let { controller.removeListener(it) }
            routineDialogListener = null
            routineDialogRender = null
            routineDialog = null
        }
        render(controller.settings())
        dialog.show()
        controller.refresh()
    }

    private fun showMoodDialog() {
        val controller = moodController ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mood_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        moodDialog = dialog
        var noteText = ""

        fun valueLabel(value: Int): Int = when (value) {
            1 -> R.string.mood_level_1
            2 -> R.string.mood_level_2
            3 -> R.string.mood_level_3
            4 -> R.string.mood_level_4
            else -> R.string.mood_level_5
        }
        fun relativeTime(atMs: Long, now: Long): String {
            val elapsed = (now - atMs).coerceAtLeast(0L)
            return when {
                elapsed < 60_000L -> "just now"
                elapsed < 60 * 60_000L -> "${elapsed / 60_000L} min ago"
                elapsed < 24 * 60 * 60_000L -> "${elapsed / 3_600_000L} h ago"
                else -> "${elapsed / (24 * 60 * 60_000L)} d ago"
            }
        }
        fun render(entries: List<MoodEntry>) {
            content.removeAllViews()
            content.addView(sectionLabel(getString(R.string.mood_title)))
            val note = EditText(this).apply {
                hint = getString(R.string.mood_note)
                setText(noteText)
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9E9E9E.toInt())
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        noteText = s?.toString().orEmpty()
                    }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }
            content.addView(labelWithField(getString(R.string.mood_note), note))
            val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (value in 1..5) {
                choices.addView(
                    actionButton(getString(valueLabel(value))) {
                        noteText = note.text.toString()
                        controller.record(value, noteText)
                        controller.showOnPet()
                    },
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            content.addView(choices)
            content.addView(View(this).apply { layoutParams = LayoutParams(1, dp(8)) })

            val latest = controller.latest()
            val average = controller.average(7)
            content.addView(sectionLabel(
                getString(R.string.mood_latest, latest?.let { getString(valueLabel(it.value)) } ?: getString(R.string.mood_empty)),
            ))
            content.addView(sectionLabel(
                getString(R.string.mood_average, average?.let { "%.1f".format(it) } ?: "—"),
            ))
            if (entries.isEmpty()) {
                content.addView(sectionLabel(getString(R.string.mood_empty)))
            } else {
                val now = System.currentTimeMillis()
                entries.forEach { entry ->
                    val line = buildString {
                        append(getString(valueLabel(entry.value)))
                        entry.note?.let { append(" · ").append(it) }
                        append(" · ").append(relativeTime(entry.atMs, now))
                    }
                    content.addView(TextView(this).apply {
                        text = line
                        setTextColor(0xFF9E9E9E.toInt())
                        setPadding(0, dp(4), 0, dp(4))
                        maxLines = 2
                    })
                }
            }
            content.addView(actionButton(getString(R.string.mood_nudge)) { controller.nudge() })
        }

        val listener: (List<MoodEntry>) -> Unit = { render(it) }
        moodDialogListener?.let { controller.removeListener(it) }
        moodDialogListener = listener
        moodDialogRender = { render(it) }
        controller.addListener(listener)
        dialog.setOnDismissListener {
            moodDialogListener?.let { controller.removeListener(it) }
            moodDialogListener = null
            moodDialogRender = null
            moodDialog = null
        }
        render(controller.recent())
        dialog.show()
        controller.refresh()
        controller.showOnPet()
    }

    private fun showFunDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        fun fullButton(label: Int, action: () -> Unit) = actionButton(getString(label), action).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.fun_title)
            .setView(content)
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        content.addView(sectionLabel(getString(R.string.fun_title)))
        content.addView(fullButton(R.string.fun_greet) {
            sayFunMessage(greetingForNow(), "greeting", "waving")
            dialog.dismiss()
        })
        content.addView(fullButton(R.string.fun_fortune) {
            val fortune = resources.getStringArray(R.array.fortunes).random()
            sayFunMessage(fortune, "fortune", "thinking")
            dialog.dismiss()
        })
        content.addView(fullButton(R.string.fun_eightball) {
            dialog.dismiss()
            showEightBallQuestion()
        })
        dialog.show()
    }

    private fun greetingForNow(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..20 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
        return getString(greeting)
    }

    private fun showEightBallQuestion() {
        val question = EditText(this).apply {
            hint = getString(R.string.eightball_question_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9E9E9E.toInt())
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.fun_eightball)
            .setView(question)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val answer = resources.getStringArray(R.array.eightball_answers).random()
                sayFunMessage("${getString(R.string.eightball_answer_title)} $answer", "eightball", "thinking")
            }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun sayFunMessage(message: String, source: String, reaction: String) {
        PetBus.sink.onSay(
            SayRequest(
                message = message,
                reaction = reaction,
                source = source,
                pinned = false,
            ),
        )
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
