package me.rapierxbox.shellyelevatev2.helper;

import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SWIPE_OCCURRED;
import static me.rapierxbox.shellyelevatev2.Constants.SP_SWITCH_ON_SWIPE;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mDeviceHelper;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences;

import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SwipeHelper {
    private float touchStartY = 0;
    private long touchStartEventTime = 0;

    public float minVel = 2.5F;
    public float minDist = 250.0F;

    public boolean onTouchEvent(Context ctx, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = event.getY();
                touchStartEventTime = event.getEventTime();
            case MotionEvent.ACTION_UP:
                float deltaY = Math.abs(touchStartY - event.getY());
                float deltaT = Math.abs(touchStartEventTime - event.getEventTime());
                float velocity = deltaY / deltaT;
                if (velocity > minVel && deltaY > minDist) {
                    if (mSharedPreferences.getBoolean(SP_SWITCH_ON_SWIPE, true)) {
                        mDeviceHelper.setRelay(!mDeviceHelper.getRelay());
                    }

                    //Let everyone know we are starting the screensaver
                    LocalBroadcastManager.getInstance(ctx).sendBroadcast(new Intent(INTENT_SWIPE_OCCURRED));
                }
        }
        return true;
    }
}
