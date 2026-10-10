package com.openpets.android.companion

import android.content.Context

/** Application-scoped shared instances for companion feature state. */
object CompanionRuntime {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun ctx(): Context = appContext ?: error("CompanionRuntime.init() not called")

    val timer: TimerController by lazy { TimerController.createRuntime(ctx()) }
    val reminders: ReminderController by lazy { ReminderController.createRuntime(ctx()) }
    val deadline: DeadlineController by lazy { DeadlineController.createRuntime(ctx()) }
    val focus: FocusController by lazy { FocusController.createRuntime(ctx()) }
    val virtualPet: VirtualPetController by lazy { VirtualPetController.createRuntime(ctx()) }
    val water: WaterController by lazy { WaterController.createRuntime(ctx()) }
    val mood: MoodController by lazy { MoodController.createRuntime(ctx()) }
    val routine: RoutineController by lazy { RoutineController.createRuntime(ctx()) }
    val breathing: BreathingController by lazy { BreathingController.createRuntime(ctx()) }
    val resources: SystemResourcesController by lazy { SystemResourcesController.createRuntime(ctx()) }
    val calendar: CalendarController by lazy { CalendarController.createRuntime(ctx()) }
    val checklist: ChecklistController by lazy { ChecklistController.createRuntime(ctx()) }
}
