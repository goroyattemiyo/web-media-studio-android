package com.goroyattemiyo.wms

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import java.lang.ref.WeakReference

class WmsApplication : Application() {
    private var resumedActivity = WeakReference<Activity>(null)

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    applySystemBarInsets(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    resumedActivity = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumedActivity.get() === activity) {
                        resumedActivity.clear()
                    }
                }

                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    if (resumedActivity.get() === activity) {
                        resumedActivity.clear()
                    }
                }
            },
        )
    }

    fun requestNotificationPermissionForSave() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val activity = resumedActivity.get() ?: return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)) return

        preferences.edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true)
            .apply()

        activity.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION,
        )
    }

    private fun applySystemBarInsets(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return

        content.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
        content.requestApplyInsets()
    }

    private companion object {
        const val PREFS_NAME = "wms_permissions"
        const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
        const val REQUEST_NOTIFICATION_PERMISSION = 2104
    }
}
