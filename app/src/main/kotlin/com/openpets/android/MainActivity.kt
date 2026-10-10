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
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openpets.android.net.OpenPetsService
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
        reloadPet()
        applyAnimationConfig()

        OpenPetsService.start(this)
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
        PetBus.register(this)
        if (::stage.isInitialized) applyAnimationConfig()
    }

    override fun onStop() {
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
