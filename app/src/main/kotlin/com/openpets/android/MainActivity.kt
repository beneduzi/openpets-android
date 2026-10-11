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
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import android.widget.LinearLayout.LayoutParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openpets.android.net.OpenPetsService
import com.openpets.android.companion.TimerController
import com.openpets.android.companion.TimerRunState
import com.openpets.android.companion.TimerSnapshot
import com.openpets.android.companion.Reminder
import com.openpets.android.companion.ReminderController
import com.openpets.android.companion.DeadlineController
import com.openpets.android.companion.Deadline
import com.openpets.android.companion.FocusController
import com.openpets.android.companion.SttState
import com.openpets.android.companion.FocusSnapshot
import com.openpets.android.companion.FocusState
import com.openpets.android.companion.FocusPhase
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
import com.openpets.android.companion.HostStatus
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
import com.openpets.android.settings.ResetScope
import com.openpets.android.settings.SettingsResetter
import com.openpets.android.settings.preferenceKeys
import com.openpets.android.settings.ui.SettingsDestination
import com.openpets.android.settings.ui.SettingsHost
import com.openpets.android.settings.ui.SettingsOverviewFragment
import com.openpets.android.settings.ui.SettingsSearchBackHandler
import com.openpets.android.settings.ui.ConnectionsFragment
import com.openpets.android.settings.ui.AppearanceFragment
import com.openpets.android.settings.ui.AdvancedFragment
import com.openpets.android.settings.ui.AnimationsFragment
import com.openpets.android.settings.ui.WalkaboutFragment
import com.openpets.android.settings.ui.PetsFragment
import com.openpets.android.settings.ui.ResourcesFragment
import com.openpets.android.settings.ui.CommandsFragment
import com.openpets.android.settings.ui.VirtualPetFragment
import com.openpets.android.settings.ui.FunFragment
import com.openpets.android.settings.ui.MoodFragment
import com.openpets.android.settings.ui.TimerFragment
import com.openpets.android.settings.ui.FocusFragment
import com.openpets.android.settings.ui.BreatheFragment
import com.openpets.android.settings.ui.RemindersFragment
import com.openpets.android.settings.ui.DeadlinesFragment
import com.openpets.android.settings.ui.ChecklistFragment
import com.openpets.android.settings.ui.WaterFragment
import com.openpets.android.settings.ui.RoutineFragment
import com.openpets.android.settings.ui.CalendarFragment
import com.openpets.android.settings.ui.VoiceFragment
import com.openpets.android.settings.ui.VocabularyFragment
import com.openpets.android.settings.ui.SettingsPermissionResultHandler
import com.openpets.android.vocab.VocabularyClient
import com.openpets.android.vocab.VocabDefinition
import com.openpets.android.view.PetStageView
import java.io.File
import java.util.Locale
import java.util.Calendar

private const val SETTINGS_CONTAINER_ID = 0x0a11ce
private const val STATE_SETTINGS_VISIBLE = "settings_visible"

/**
 * Always-on companion surface. Shows the pet full-screen, keeps the screen on,
 * and drives it from the IPC/remote servers via [PetBus].
 *
 * Tap the pet to wave; long-press to open settings (ports, tokens, pet picker).
 */
class MainActivity : AppCompatActivity(), PetBus.Listener, SettingsHost {

    private lateinit var stage: PetStageView
    private lateinit var settingsContainer: FrameLayout
    private var settingsVisible = false
    private lateinit var settings: AppSettings
    private lateinit var repository: PetRepository
    private val catalogClient = CatalogClient()
    private val vocabularyClient = VocabularyClient()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var timerController: TimerController? = null
    private var focusController: FocusController? = null
    private var reminderController: ReminderController? = null
    private var virtualPetController: VirtualPetController? = null
    private var waterController: WaterController? = null
    private var moodController: MoodController? = null
    private var routineController: RoutineController? = null
    private var breathingController: BreathingController? = null
    private var timerMaintenanceListener: ((TimerSnapshot) -> Unit)? = null
    private var focusMaintenanceListener: ((FocusSnapshot) -> Unit)? = null
    private var breathingMaintenanceListener: ((BreathSnapshot) -> Unit)? = null
    private var maintenanceListenersRegistered = false
    private var resourcesController: SystemResourcesController? = null
    private var calendarController: CalendarController? = null
    private var checklistController: ChecklistController? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var activityStarted = false
    private val timerTick = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed || !activityStarted) return
            val controller = timerController ?: return
            controller.refresh()
            val snapshot = controller.snapshot()
            if (snapshot.state == TimerRunState.RUNNING) {
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
                timerHandler.postDelayed(this, 1_000L)
            }
        }
    }

    private var pendingPetImportCallback:(()->Unit)?=null
    private val importZipLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri==null)finishPetImport(false) else importPet({ repository.importZip(uri) }) { finishPetImport(true) }
    }
    private val importFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri==null)finishPetImport(false) else importPet({ repository.importFolder(uri) }) { finishPetImport(true) }
    }
    private fun finishPetImport(success:Boolean){val callback=pendingPetImportCallback;pendingPetImportCallback=null;if(success)callback?.invoke()}
    private val customSoundLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { } ?: throw IllegalStateException(getString(R.string.settings_sound_not_readable))
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                settings.customSoundUri = uri.toString()
                OpenPetsService.reload(this)
                Toast.makeText(this, getString(R.string.ui_custom_sound_set), Toast.LENGTH_SHORT).show()
                (supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? AppearanceFragment)?.onSoundSelected()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.settings_sound_file_error, e.message ?: getString(R.string.settings_sound_not_readable)), Toast.LENGTH_LONG).show()
            }
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
            contentDescription = getString(R.string.settings_pet_a11y_description)
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            onTap = { react("waving") }
            onLongPress = { showSettings() }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, getString(R.string.settings_open_settings_action)))
                }
                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) { showSettings(); return true }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        }
        val root = FrameLayout(this)
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))
        settingsContainer = FrameLayout(this).apply {
            id = SETTINGS_CONTAINER_ID
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            val value = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, value, true)
            setBackgroundColor(value.data)
        }
        root.addView(settingsContainer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val screen=supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? com.openpets.android.settings.ui.SettingsScreenFragment
                if (screen?.requestClose()==true) return
                val overview=supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? SettingsSearchBackHandler
                if(overview?.closeSearchOnBack()==true)return
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack() else closeSettings()
            }
        }.also { backCallback = it })
        settingsVisible = savedInstanceState?.getBoolean(STATE_SETTINGS_VISIBLE) ?: false
        if (settingsVisible) restoreSettingsShell()
        timerController = TimerController(this)
        focusController = FocusController(this)
        reminderController = ReminderController(this)
        virtualPetController = VirtualPetController(this)
        waterController = WaterController(this)
        moodController = MoodController(this)
        routineController = RoutineController(this)
        breathingController = BreathingController(this)
        timerMaintenanceListener = { timerController?.showOnPet() }
        focusMaintenanceListener = { focusController?.showOnPet() }
        breathingMaintenanceListener = { breathingController?.showOnPet() }
        timerController?.addListener(timerMaintenanceListener!!)
        focusController?.addListener(focusMaintenanceListener!!)
        breathingController?.addListener(breathingMaintenanceListener!!)
        resourcesController = SystemResourcesController(this)
        calendarController = CalendarController(this)
        checklistController = ChecklistController(this)
        reloadPet()
        applyAnimationConfig()

        OpenPetsService.start(this)
        requestNotificationPermission()
    }
    private lateinit var backCallback: OnBackPressedCallback

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SETTINGS_VISIBLE, settingsVisible)
        super.onSaveInstanceState(outState)
    }

    fun selectedPetName(): String = currentPetLabel()
    fun chooseCustomSound() { customSoundLauncher.launch(arrayOf("audio/*")) }
    fun chooseDefaultSound() { settings.customSoundUri=null; OpenPetsService.reload(this);(supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? AppearanceFragment)?.onSoundSelected() }
    fun refreshWalkabout(mode:String,speed:Float){ stage.applyWalkabout(mode,speed) }
    fun refreshAnimationConfig(){ applyAnimationConfig() }
    fun availablePets():List<PetDescriptor> = repository.listPets().sortedWith(compareByDescending<PetDescriptor>{it.builtIn}.thenBy{it.displayName.lowercase(Locale.ROOT)})
    fun selectedPetId():String=settings.selectedPetId
    fun isImportedPet(id:String)=repository.isImported(id)
    fun selectPetById(id:String){ availablePets().firstOrNull{it.id==id}?.let(::selectPet) }
    fun openOnlinePetCatalog(onComplete:()->Unit){ showCatalogBrowser(onComplete) }
    fun launchPetZipImport(onComplete:()->Unit={}){ pendingPetImportCallback=onComplete;try{importZipLauncher.launch(arrayOf("application/zip","application/octet-stream","*/*"))}catch(e:Exception){pendingPetImportCallback=null;Toast.makeText(this,getString(R.string.settings_pet_import_error,e.message?:""),Toast.LENGTH_LONG).show()} }
    fun launchPetFolderImport(onComplete:()->Unit={}){ pendingPetImportCallback=onComplete;try{importFolderLauncher.launch(null)}catch(e:Exception){pendingPetImportCallback=null;Toast.makeText(this,getString(R.string.settings_pet_folder_error,e.message?:""),Toast.LENGTH_LONG).show()} }
    fun deleteSelectedImportedPet(onComplete:()->Unit):Boolean {
        val id=settings.selectedPetId
        if(!repository.isImported(id) || !repository.deleteImported(id))return false
        settings.selectedPetId="tux";reloadPet();OpenPetsService.reload(this);Toast.makeText(this,getString(R.string.ui_imported_pet_removed),Toast.LENGTH_SHORT).show();onComplete();return true
    }

    override fun openSettingsDestination(destination: SettingsDestination) {
        when (destination) {
            SettingsDestination.PETS -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, PetsFragment()).addToBackStack("pets").commit()
            SettingsDestination.ANIMATIONS -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, AnimationsFragment()).addToBackStack("animations").commit()
            SettingsDestination.WALKABOUT -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, WalkaboutFragment()).addToBackStack("walkabout").commit()
            SettingsDestination.TIMER -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, TimerFragment()).addToBackStack("timer").commit()
            SettingsDestination.REMINDERS -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, RemindersFragment()).addToBackStack("reminders").commit()
            SettingsDestination.DEADLINES -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, DeadlinesFragment()).addToBackStack("deadlines").commit()
            SettingsDestination.FOCUS -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, FocusFragment()).addToBackStack("focus").commit()
            SettingsDestination.CALENDAR -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, CalendarFragment()).addToBackStack("calendar").commit()
            SettingsDestination.CHECKLIST -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, ChecklistFragment()).addToBackStack("checklist").commit()
            SettingsDestination.WATER -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, WaterFragment()).addToBackStack("water").commit()
            SettingsDestination.MOOD -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, MoodFragment()).addToBackStack("mood").commit()
            SettingsDestination.ROUTINE -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, RoutineFragment()).addToBackStack("routine").commit()
            SettingsDestination.BREATHE -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, BreatheFragment()).addToBackStack("breathe").commit()
            SettingsDestination.VIRTUAL_PET -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, VirtualPetFragment()).addToBackStack("virtual-pet").commit()
            SettingsDestination.FUN -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, FunFragment()).addToBackStack("fun").commit()
            SettingsDestination.VOCABULARY -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, VocabularyFragment()).addToBackStack("vocabulary").commit()
            SettingsDestination.VOICE -> supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, VoiceFragment()).addToBackStack("voice").commit()
            SettingsDestination.RESOURCES -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, ResourcesFragment()).addToBackStack("resources").commit()
            SettingsDestination.COMMANDS -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, CommandsFragment()).addToBackStack("commands").commit()
            SettingsDestination.SERVER_SETTINGS -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, ConnectionsFragment()).addToBackStack("connections").commit()
            SettingsDestination.SOUND_OUTPUT, SettingsDestination.HOST_STATUS_PENDING -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, AppearanceFragment()).addToBackStack("appearance").commit()
            SettingsDestination.START_ON_BOOT -> supportFragmentManager.beginTransaction()
                .replace(SETTINGS_CONTAINER_ID, AdvancedFragment()).addToBackStack("startup").commit()
            SettingsDestination.ABOUT -> AlertDialog.Builder(this).setTitle(getString(R.string.settings_about_title))
                .setMessage(getString(R.string.settings_about_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(getString(R.string.settings_about_open_app)) { _, _ ->
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/beneduzi/openpets-android")))
                }
                .setNeutralButton(getString(R.string.settings_about_open_upstream)) { _, _ ->
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/OpenPetsHQ/openpets")))
                }
                .setNegativeButton(android.R.string.cancel, null).show()
            SettingsDestination.RESET_PREFERENCES -> showPreferenceResetChooser()
        }
    }

    override fun closeSettings() {
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.fragments.filter { it.id == SETTINGS_CONTAINER_ID }.forEach { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        }
        settingsContainer.visibility = View.GONE
        settingsVisible = false
        backCallback.isEnabled = false
        stage.isEnabled = true
        stage.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        stage.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        stage.isFocusable = true
        applyImmersive(true)
        stage.requestFocus()
        stage.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    override fun revealStage() = closeSettings()
    override fun requestSettingsPermission(permission:String,requestCode:Int){ActivityCompat.requestPermissions(this,arrayOf(permission),requestCode)}
    override fun openSettingsSearchResult(destination:SettingsDestination,targetKey:String?){openSettingsDestination(destination);if(targetKey!=null){supportFragmentManager.executePendingTransactions();(supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? com.openpets.android.settings.ui.SettingsScreenFragment)?.setSearchTarget(targetKey)}}
    override fun showPreferenceResetChooser(){
        val scopes=ResetScope.values();val labels=arrayOf(R.string.ui_reset_scopes,R.string.ui_reset_scope_appearance,R.string.ui_reset_scope_connections,R.string.ui_reset_scope_companion,R.string.ui_reset_scope_startup,R.string.ui_reset_scope_all).map(::getString).toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.ui_reset_preferences).setItems(labels){_,which->confirmPreferenceReset(scopes[which])}.setNegativeButton(R.string.dialog_close,null).show()
    }
    private fun confirmPreferenceReset(scope:ResetScope){
        val consequences=when(scope){
            ResetScope.PET->getString(R.string.settings_reset_scope_pet_detail)
            ResetScope.APPEARANCE->getString(R.string.settings_reset_scope_appearance_detail)
            ResetScope.CONNECTIONS->getString(R.string.settings_reset_scope_connections_detail)
            ResetScope.COMPANION->getString(R.string.settings_reset_scope_companion_detail)
            ResetScope.STARTUP->getString(R.string.settings_reset_scope_startup_detail)
            ResetScope.ALL->"Resets all preferences, including enabling IPC and Remote on all network interfaces."
        }
        val title=getString(R.string.ui_reset_confirm_title)
        val message=if(scope==ResetScope.ALL)getString(R.string.settings_reset_all_detail)+"\n\n"+getString(R.string.settings_reset_kept) else "$consequences\n\n${getString(R.string.ui_reset_preserves)}"
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.ui_reset_preferences){_,_->applyPreferenceReset(scope)}.setNegativeButton(R.string.dialog_close,null).show()
    }
    private fun applyPreferenceReset(scope:ResetScope){
        SettingsResetter(settings,waterController,routineController,calendarController).apply(scope)
        if("selectedPetId" in preferenceKeys(scope)){reloadPet();applyAnimationConfig()}
        if("hostStatusEnabled" in preferenceKeys(scope))PetBus.hostMetrics(null)
        if(preferenceKeys(scope).any{it in setOf("ipcEnabled","remoteEnabled","wsEnabled","bindAddress","ipcPort","remotePort","wsPort","requireLease","autoStartOnBoot","customSoundUri")})OpenPetsService.reload(this)
        supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID)?.let{(it.view as? ViewGroup)?.invalidate()}
        (supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? SettingsOverviewFragment)?.refreshOverview()
    }

    fun resourcesControllerForSettings(): SystemResourcesController? = resourcesController
    fun pluginCommandsForSettings() = CommandRegistry.list()
    fun virtualPetControllerForSettings(): VirtualPetController? = virtualPetController
    fun moodControllerForSettings(): MoodController? = moodController
    fun timerControllerForSettings():TimerController?=timerController
    fun focusControllerForSettings():FocusController?=focusController
    fun breathingControllerForSettings():BreathingController?=breathingController
    fun remindersControllerForSettings():ReminderController?=reminderController
    fun deadlinesControllerForSettings():com.openpets.android.companion.DeadlineController=com.openpets.android.companion.CompanionRuntime.deadline
    fun checklistControllerForSettings():ChecklistController?=checklistController
    fun waterControllerForSettings():WaterController?=waterController
    fun routineControllerForSettings():RoutineController?=routineController
    fun checklistGreetingForSettings()=settings.launchGreeting
    fun persistChecklistGreetingForSettings(value:String){settings.launchGreeting=value}
    fun calendarControllerForSettings():CalendarController?=calendarController
    fun vocabularySettingsForSettings()=if(::settings.isInitialized) settings.vocabTargetLang to settings.ankiHost else "en" to ""
    fun saveVocabularySettingsForSettings(language:String,ankiHost:String){settings.vocabTargetLang=language;settings.ankiHost=ankiHost}
    fun vocabularyClientForSettings()=vocabularyClient
    fun ankiAvailableForSettings()=settings.ankiHost.isNotBlank()
    fun breathingPhaseLabelForSettings(phase:BreathPhase):String {
        val label=when(phase){BreathPhase.INHALE->R.string.breathe_in;BreathPhase.HOLD_IN,BreathPhase.HOLD_OUT->R.string.breathe_hold;BreathPhase.EXHALE->R.string.breathe_out;BreathPhase.DONE->R.string.breathe_done}
        return getString(label)
    }
    fun focusStatusLabelForSettings(snapshot:FocusSnapshot):String=when(snapshot.state){
        FocusState.FOCUS->getString(R.string.focus_cycles,snapshot.cyclesCompleted)
        FocusState.BREAK->getString(R.string.focus_break_label)
        FocusState.PAUSED->getString(if(snapshot.pausedPhase==FocusPhase.BREAK)R.string.focus_paused_break else R.string.focus_paused_focus)
        FocusState.IDLE->""
    }
    fun focusSkipLabelForSettings(state:FocusState):String=getString(if(state==FocusState.FOCUS)R.string.focus_skip_break else R.string.focus_skip_focus)
    fun moodRatingLabelForSettings(value:Int):String = getString(when(value){1->R.string.mood_level_1;2->R.string.mood_level_2;3->R.string.mood_level_3;4->R.string.mood_level_4;else->R.string.mood_level_5})
    fun funGreetingForSettings() = greetingForNow()
    fun funFortuneForSettings() = resources.getStringArray(R.array.fortunes).random()
    fun funEightBallAnswerForSettings() = "${getString(R.string.eightball_answer_title)} ${resources.getStringArray(R.array.eightball_answers).random()}"
    fun sayFunMessageForSettings(message:String,source:String,reaction:String) = sayFunMessage(message,source,reaction)
    fun showEightBallForSettings(onAnswer:()->Unit) {
        val question=EditText(this).apply { hint=getString(R.string.eightball_question_hint);setTextColor(Color.WHITE);setHintTextColor(0xFF9E9E9E.toInt());setPadding(dp(20),dp(8),dp(20),dp(8)) }
        AlertDialog.Builder(this).setTitle(R.string.fun_eightball).setView(question)
            .setPositiveButton(android.R.string.ok){_,_->sayFunMessageForSettings(funEightBallAnswerForSettings(),"eightball","thinking");onAnswer()}
            .setNegativeButton(R.string.dialog_close,null).show()
    }

    private fun showSettings() {
        currentFocus?.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        stage.cancelActiveGesture()
        settingsVisible = true
        applySettingsShellState()
        if (supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) == null) {
            supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, SettingsOverviewFragment()).commit()
        }
    }

    private fun restoreSettingsShell() {
        applySettingsShellState()
        if (supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) == null) {
            supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID, SettingsOverviewFragment()).commit()
        }
    }

    private fun applySettingsShellState() {
        settingsContainer.visibility = View.VISIBLE
        settingsContainer.isClickable = true
        settingsContainer.isFocusable = true
        stage.isEnabled = false
        stage.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        stage.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        applyImmersive(false)
        backCallback.isEnabled = true
        currentFocus?.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.content.Intent.ACTION_SEND) {
            intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let{ text -> intent.removeExtra(android.content.Intent.EXTRA_TEXT);intent.action=null;openSharedVocabulary(text) }
        }
    }

    private fun openSharedVocabulary(text:String){
        showSettings()
        if(supportFragmentManager.isStateSaved){
            window.decorView.post{if(!isFinishing&&!supportFragmentManager.isStateSaved)supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID,VocabularyFragment.newInstance(text)).addToBackStack("vocabulary").commit()}
        }else supportFragmentManager.beginTransaction().replace(SETTINGS_CONTAINER_ID,VocabularyFragment.newInstance(text)).addToBackStack("vocabulary").commit()
    }

    private fun sharedText(): String? = if (intent?.action == android.content.Intent.ACTION_SEND) {
        intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    } else null

    override fun onStart() {
        super.onStart()
        activityStarted = true
        AppState.foreground = true
        PetBus.register(this)
        if(!maintenanceListenersRegistered){
            timerMaintenanceListener?.let{timerController?.addListener(it)}
            focusMaintenanceListener?.let{focusController?.addListener(it)}
            breathingMaintenanceListener?.let{breathingController?.addListener(it)}
            maintenanceListenersRegistered=true
        }
        timerController?.refresh()
        startTimerTickIfRunning()
        reminderController?.refresh()
        focusController?.refresh()
        virtualPetController?.refresh()
        waterController?.refresh()
        moodController?.refresh()
        routineController?.refresh()
        breathingController?.refresh()
        resourcesController?.refresh()
        checklistController?.refresh()
        calendarController?.refresh()
        startFocusTickIfRunning()
        startBreathingTickIfActive()
        timerController?.showOnPet()
        focusController?.showOnPet()
        breathingController?.showOnPet()
        if (::stage.isInitialized) applyAnimationConfig()
    }

    override fun onStop() {
        activityStarted = false
        timerHandler.removeCallbacks(timerTick)
        timerHandler.removeCallbacks(focusTick)
        timerHandler.removeCallbacks(breathingTick)
        timerMaintenanceListener?.let{timerController?.removeListener(it)}
        focusMaintenanceListener?.let{focusController?.removeListener(it)}
        breathingMaintenanceListener?.let{breathingController?.removeListener(it)}
        maintenanceListenersRegistered=false
        PetBus.unregister(this)
        AppState.foreground = false
        super.onStop()
    }

    override fun onReact(reaction: String, source: String?) {
        stage.react(reaction, source)
    }

    override fun onSay(request: SayRequest) {
        stage.say(request)
        if (!request.silent && settings.ttsEnabled) speak(request.message)
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

    override fun onHostMetrics(status: HostStatus?) { if (::stage.isInitialized) stage.onHostMetrics(status) }

    override fun onAction(event: PetActionEvent) {}
    override fun onInputText(text: String, captureId: String?) {}
    override fun onMove(request: MoveRequest) { if (::stage.isInitialized) stage.movePet(request) }
    override fun onScale(scale: Float) { if (::stage.isInitialized) stage.applyPetScale(scale) }
    override fun onVisibility(visible: Boolean) { if (::stage.isInitialized) stage.setPetVisible(visible) }
    override fun onPointer(kind: String, phase: String, x: Float, y: Float) {}
    override fun onNotify(request: com.openpets.android.protocol.NotificationRequest) {}
    override fun onStatus(text: String, tone: String?) {}
    override fun onStatusClear() {}
    override fun onPower(plugged: Boolean, level: Int) {}
    override fun onCommandInvoked(event: CommandInvoked) {}
    override fun onFormSubmit(event: com.openpets.android.FormSubmit) {}
    override fun onCommandsChanged() {}

    private fun speak(text: String) {
        if (!ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openpets")
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        timerMaintenanceListener?.let { timerController?.removeListener(it) }; timerMaintenanceListener=null
        focusMaintenanceListener?.let { focusController?.removeListener(it) }; focusMaintenanceListener=null
        breathingMaintenanceListener?.let { breathingController?.removeListener(it) }; breathingMaintenanceListener=null
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onPostResume(){
        super.onPostResume()
        if(!::settings.isInitialized)return
        sharedText()?.let{ text -> intent?.removeExtra(android.content.Intent.EXTRA_TEXT); if(intent?.action==android.content.Intent.ACTION_SEND)intent?.action=null; openSharedVocabulary(text) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive(!settingsVisible)
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive(enabled: Boolean) {
        if (enabled) window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                ) else window.decorView.systemUiVisibility = 0
    }

    // ---- pet loading / selection ----

    private fun applyAnimationConfig() {
        stage.setAnimationConfig(settings.reactionOverrides, settings.waitingDurationMs)
        stage.applyWalkabout(settings.walkaboutMode, settings.walkaboutSpeed)
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
            Toast.makeText(this, "Failed to load pet: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun importPet(block: () -> PetDescriptor, onComplete:()->Unit = {}) {
        Toast.makeText(this, getString(R.string.ui_importing), Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val descriptor = block()
                runOnUiThread {
                    settings.selectedPetId = descriptor.id
                    reloadPet()
                    OpenPetsService.reload(this)
                    Toast.makeText(this, "Pet imported: ${descriptor.displayName}", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    finishPetImport(false)
                }
            }
        }.start()
    }


    fun showCatalogBrowser(onComplete:()->Unit = {}) {
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val search = EditText(this).apply { hint = getString(R.string.pet_search_hint) }
        val searchButton = actionButton(getString(R.string.pet_search)) { }
        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(6))
        }
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val prevButton = actionButton(getString(R.string.ui_previous)) { }
        val nextButton = actionButton(getString(R.string.ui_next)) { }
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
            status.text = getString(R.string.ui_loading)
            listContainer.removeAllViews()
            Thread {
                try {
                    val result = catalogClient.listPets(query, target)
                    runOnUiThread {
                        page = result.page
                        totalPages = result.totalPages
                        status.text = "Page $page/$totalPages · ${result.total} pets"
                        listContainer.removeAllViews()
                        if (result.pets.isEmpty()) {
                            listContainer.addView(sectionLabel("No pets found."))
                        }
                        result.pets.forEach { pet ->
                            listContainer.addView(actionButton("${pet.displayName}  ·  ${pet.author}") {
                                 downloadAndImport(pet) { dialogRef[0]?.dismiss(); onComplete() }
                            })
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Failed to load: ${e.message}" }
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
            .setTitle("Catalog · codex-pets.net")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dialog_close, null)
            .create()
        dialogRef[0] = dialog
        dialog.show()
        load(1)
    }

    private fun downloadAndImport(pet: CatalogPet, onDone: () -> Unit) {
        Toast.makeText(this, getString(R.string.ui_downloading_named, pet.displayName), Toast.LENGTH_SHORT).show()
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
                runOnUiThread { Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ---- settings ----









    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        (supportFragmentManager.findFragmentById(SETTINGS_CONTAINER_ID) as? SettingsPermissionResultHandler)
            ?.onSettingsPermissionResult(requestCode,grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)
    }


    fun rearmSessionMaintenance() {
        startTimerTickIfRunning(); startFocusTickIfRunning(); startBreathingTickIfActive()
    }
    private fun startTimerTickIfRunning() {
        timerHandler.removeCallbacks(timerTick)
        if (timerController?.snapshot()?.state == TimerRunState.RUNNING && activityStarted && !isFinishing) {
            timerHandler.postDelayed(timerTick, 1_000L)
        }
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

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(8), 0, dp(2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
