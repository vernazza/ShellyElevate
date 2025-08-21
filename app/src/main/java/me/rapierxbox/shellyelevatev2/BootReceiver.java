package me.rapierxbox.shellyelevatev2;

import static android.content.Context.MODE_PRIVATE;
import static me.rapierxbox.shellyelevatev2.Constants.BACK_BUTTON_NEVER;
import static me.rapierxbox.shellyelevatev2.Constants.SHARED_PREFERENCES_NAME;
import static me.rapierxbox.shellyelevatev2.Constants.SP_LITE_MODE;
import static me.rapierxbox.shellyelevatev2.Constants.SP_SHOW_BACK_BUTTON;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Objects;

import me.rapierxbox.shellyelevatev2.backbutton.FloatingBackButtonService;


public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            Log.i("ShellyElevateV2", "Starting... (If not already started)");

            SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);

            if (preferences.getBoolean(SP_LITE_MODE, false)) {
                Intent appIntent = new Intent(context, ShellyElevateApplication.class);
                context.startService(appIntent);

                if (preferences.getInt(SP_SHOW_BACK_BUTTON, BACK_BUTTON_NEVER) != BACK_BUTTON_NEVER) {
                    Intent backButtonIntent = new Intent(context, FloatingBackButtonService.class);
                    backButtonIntent.setAction(FloatingBackButtonService.SHOW_FLOATING_BUTTON);
                    context.startService(backButtonIntent);
                }
            } else {
                Log.i("ShellyElevateV2", "Starting MainActivity");
                Intent activityIntent = new Intent(context, MainActivity.class);
                context.startActivity(activityIntent);
            }
        }
    }
}
