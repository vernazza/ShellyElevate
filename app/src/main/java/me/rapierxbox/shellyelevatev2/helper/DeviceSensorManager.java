package me.rapierxbox.shellyelevatev2.helper;

import static me.rapierxbox.shellyelevatev2.Constants.SP_AUTOMATIC_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MIN_BRIGHTNESS;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mMQTTServer;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import me.rapierxbox.shellyelevatev2.ShellyElevateApplication;


public class DeviceSensorManager implements SensorEventListener {
    private static final String TAG = "DeviceSensorManager";
    private float lastMeasuredLux = 0.0f;
    private boolean automaticBrightness = true;

    //region Fade Manager
    private long lastUpdateTime = 0;
    private static final long HYSTERESIS_DELAY_MS = 3000; // 3 seconds
    private static final long FADE_DURATION_MS = 1000;

    private int currentBrightness = -1;
    private int targetBrightness = -1;

    private final Handler fadeHandler = new Handler(Looper.getMainLooper());
    private Runnable fadeRunnable;
    //endregion

    public float getLastMeasuredLux() {
        return lastMeasuredLux;
    }

    public void updateValues() {
        automaticBrightness = mSharedPreferences.getBoolean(SP_AUTOMATIC_BRIGHTNESS, true);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lastMeasuredLux = event.values[0];
            Log.d(TAG, "Light sensor value: " + lastMeasuredLux);

            if (automaticBrightness) {
                int desiredBrightness = getScreenBrightnessFromLux(lastMeasuredLux);
                if (desiredBrightness != targetBrightness) {
                    targetBrightness = desiredBrightness;
                    lastUpdateTime = System.currentTimeMillis();
                    fadeHandler.removeCallbacks(fadeRunnable);
                    fadeRunnable = this::checkAndApplyBrightness;
                    fadeHandler.postDelayed(fadeRunnable, HYSTERESIS_DELAY_MS);
                }
            }

            if (mMQTTServer.shouldSend()) {
                mMQTTServer.publishLux(lastMeasuredLux);
            }
        }
    }

    private void checkAndApplyBrightness() {
        if (System.currentTimeMillis() - lastUpdateTime >= HYSTERESIS_DELAY_MS) {
            if (currentBrightness == -1) {
                currentBrightness = targetBrightness;
                ShellyElevateApplication.mDeviceHelper.setScreenBrightness(currentBrightness);
            } else {
                animateBrightnessTransition(currentBrightness, targetBrightness, FADE_DURATION_MS);
            }
        }
    }

    private void animateBrightnessTransition(int from, int to, long duration) {
        long startTime = System.currentTimeMillis();
        fadeHandler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float fraction = Math.min(1f, (float) elapsed / duration);
                int interpolated = (int) (from + (to - from) * fraction);
                ShellyElevateApplication.mDeviceHelper.setScreenBrightness(interpolated);

                if (fraction < 1f) {
                    fadeHandler.postDelayed(this, 20); // 50fps
                } else {
                    currentBrightness = to;
                }
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Ignore
    }

    public static int getScreenBrightnessFromLux(float lux) {
        int minBrightness = mSharedPreferences.getInt(SP_MIN_BRIGHTNESS, 48);
        if (lux >= 500) return 255;
        if (lux <= 30) return minBrightness;

        double slope = (255.0 - minBrightness) / (500.0 - 30.0);
        return (int) (minBrightness + slope * (lux - 30));
    }
}
