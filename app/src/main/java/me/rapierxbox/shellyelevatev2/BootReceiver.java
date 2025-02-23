package me.rapierxbox.shellyelevatev2;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.legacy.content.WakefulBroadcastReceiver;

import java.util.Objects;

public class BootReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)){
            Log.i("ShellyElevateV2", "Starting...");

            Intent serviceIntent = new Intent(context, HttpServerService.class);
            startWakefulService(context, serviceIntent);

            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
