package me.rapierxbox.shellyelevatev2.backbutton

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STARTED
import me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STOPPED
import me.rapierxbox.shellyelevatev2.R

class FloatingBackButtonService : Service() {

    private val myLocalBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Check for the specific action you're interested in
            when (intent?.action) {
                //When the screen saver starts, we pause the floating point
                INTENT_SCREEN_SAVER_STARTED -> pauseFloatingButton()

                //When the screen saver stops, we resume the floating point (we show it if it was visible)
                INTENT_SCREEN_SAVER_STOPPED -> resumeFloatingButton()
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private lateinit var prefs: SharedPreferences

    private var wasVisibleBeforePause = false

    fun pauseFloatingButton() {
        wasVisibleBeforePause = (floatingView != null)
        hideFloatingButton()
    }

    fun resumeFloatingButton() {
        if (wasVisibleBeforePause) showFloatingButton()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(FLOATING_BUTTON_PREFS, MODE_PRIVATE)
        showFloatingButton()

        val intentFilter = IntentFilter().apply {
            addAction(INTENT_SCREEN_SAVER_STOPPED)
            addAction(INTENT_SCREEN_SAVER_STARTED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(myLocalBroadcastReceiver, intentFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingBackButtonService", "onStartCommand() called with: intent = $intent, flags = $flags, startId = $startId, action = ${intent?.action}")

        when (intent?.action) {
            SHOW_FLOATING_BUTTON -> showFloatingButton()
            HIDE_FLOATING_BUTTON -> hideFloatingButton()

            else -> showFloatingButton()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingButton() {

        if (!Settings.canDrawOverlays(this)) {
            Log.w("FloatingBackButtonService", "Can't draw overlays without permission")
            return
        }

        //This overrides pause status
        wasVisibleBeforePause = true

        if (floatingView != null) return

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button_layout, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = prefs.getInt(POS_X, 0)
        layoutParams.y = prefs.getInt(POS_Y, 300)

        val button = floatingView!!.findViewById<ImageView>(R.id.floating_back_button)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, layoutParams)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy

                    floatingView?.let {
                        windowManager.updateViewLayout(it, layoutParams)
                    }

                    if (dx != 0 || dy != 0) isClick = false
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        performClick()
                    } else {
                        prefs.edit {
                            putInt(POS_X, layoutParams.x)
                            putInt(POS_Y, layoutParams.y)
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun performClick() {
        if (BackAccessibilityService.isAccessibilityEnabled(this)) {
            sendBroadcast(Intent(BackAccessibilityService.ACTION_BACK))
        } else {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, getString(R.string.accessibility_service_not_enabled), Toast.LENGTH_SHORT).show()
        }
    }

    fun hideFloatingButton() {
        //This overrides pause status
        wasVisibleBeforePause = false

        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
    }

    override fun onDestroy() {
        hideFloatingButton()
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myLocalBroadcastReceiver)
    }


    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val SHOW_FLOATING_BUTTON = "SHOW_FLOATING_BUTTON"
        const val HIDE_FLOATING_BUTTON = "HIDE_FLOATING_BUTTON"

        const val POS_X = "pos_x"
        const val POS_Y = "pos_y"

        const val FLOATING_BUTTON_PREFS = "floating_button_prefs"
    }
}
