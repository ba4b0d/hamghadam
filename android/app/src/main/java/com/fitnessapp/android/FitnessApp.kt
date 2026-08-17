package com.fitnessapp.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fitnessapp.android.data.HealthConnectRepository
import com.fitnessapp.android.data.cache.DailySummaryCache
import com.fitnessapp.android.data.cache.PrefsDailySummaryCache
import com.fitnessapp.android.data.fcm.FcmTokenManager
import com.fitnessapp.android.data.fcm.NotificationRouter
import com.fitnessapp.android.data.network.ApiClient
import com.fitnessapp.android.data.network.AuthStore
import com.fitnessapp.android.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tracks whether any app activity is in the foreground. The notification
 * router uses this to decide navigate-in-place vs. post-a-notification.
 */
class AppForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    var foreground: Boolean = true
        private set

    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started > 0) foreground = true
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        if (started <= 0) {
            started = 0
            foreground = false
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** Manual DI container — kept small and explicit for a v1 shell. */
class AppContainer(context: android.content.Context) {
    val authStore: AuthStore = AuthStore(context)
    val healthRepository: HealthConnectRepository = HealthConnectRepository(context)
    val dailyCache: DailySummaryCache =
        PrefsDailySummaryCache(context.getSharedPreferences("fitness_app_daily_cache", android.content.Context.MODE_PRIVATE))
    val apiClient: ApiClient = ApiClient { authStore.baseUrl }
    val syncScheduler: SyncScheduler = SyncScheduler

    private val foregroundTracker = AppForegroundTracker()
    val notificationRouter: NotificationRouter =
        NotificationRouter(context.applicationContext) { foregroundTracker.foreground }
    val fcmTokenManager: FcmTokenManager =
        FcmTokenManager(context.applicationContext, authStore, apiClient)

    /**
     * Deep links coming from outside the UI (MainActivity VIEW intents).
     * MainScreen observes this flow and navigates; consumer sets it back to null.
     */
    val externalDeepLinks = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    fun registerForegroundTracker(app: Application) {
        app.registerActivityLifecycleCallbacks(foregroundTracker)
    }
}

class FitnessApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.registerForegroundTracker(this)
        // Periodic WorkManager sync survives reboots; schedule once per process.
        SyncScheduler.schedulePeriodic(this)
        // Register the FCM token with the backend when a session exists
        // (Firebase token, or dev token while no Firebase project is wired).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.fcmTokenManager.registerIfSignedIn()
        }
    }
}
