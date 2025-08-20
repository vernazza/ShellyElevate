package me.rapierxbox.shellyelevatev2.mqtt;

import static me.rapierxbox.shellyelevatev2.Constants.INTENT_LIGHT_KEY;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_LIGHT_UPDATED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_PROXIMITY_KEY;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_PROXIMITY_UPDATED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_RELAY_STATUS_KEY;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_RELAY_UPDATED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STARTED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SCREEN_SAVER_STOPPED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SETTINGS_CHANGED;
import static me.rapierxbox.shellyelevatev2.Constants.INTENT_SWIPE_OCCURRED;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_CONFIG_DEVICE;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_HOME_ASSISTANT_STATUS;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_HUM_SENSOR;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_LUX_SENSOR;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_PROXIMITY_SENSOR;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_REBOOT_BUTTON;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_REFRESH_WEBVIEW_BUTTON;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_RELAY_COMMAND;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_RELAY_STATE;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_SLEEPING_BINARY_SENSOR;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_SLEEP_BUTTON;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_STATUS;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_SWIPE_EVENT;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_TEMP_SENSOR;
import static me.rapierxbox.shellyelevatev2.Constants.MQTT_TOPIC_WAKE_BUTTON;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MQTT_BROKER;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MQTT_DEVICE_ID;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MQTT_ENABLED;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MQTT_PASSWORD;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MQTT_PORT;
import static me.rapierxbox.shellyelevatev2.Constants.SP_MQTT_USERNAME;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mApplicationContext;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mDeviceHelper;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mDeviceSensorManager;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mScreenSaverManager;
import static me.rapierxbox.shellyelevatev2.ShellyElevateApplication.mSharedPreferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.rapierxbox.shellyelevatev2.DeviceModel;

public class MQTTServer extends BroadcastReceiver {
    private MqttClient mMqttClient;
    private final MemoryPersistence mMemoryPersistence;
    private final ShellyElevateMQTTCallback mShellyElevateMQTTCallback;
    private final MqttConnectionOptions mMqttConnectionsOptions;
    private final ScheduledExecutorService scheduler;
    private boolean connected;
    private String clientId;

    private boolean validForConnection;

    public MQTTServer() {
        mMemoryPersistence = new MemoryPersistence();
        mShellyElevateMQTTCallback = new ShellyElevateMQTTCallback();
        mMqttConnectionsOptions = new MqttConnectionOptions();

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mApplicationContext);

        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(INTENT_SETTINGS_CHANGED);

        intentFilter.addAction(INTENT_LIGHT_UPDATED);
        intentFilter.addAction(INTENT_PROXIMITY_UPDATED);
        intentFilter.addAction(INTENT_RELAY_UPDATED);
        intentFilter.addAction(INTENT_SCREEN_SAVER_STARTED);
        intentFilter.addAction(INTENT_SCREEN_SAVER_STOPPED);
        intentFilter.addAction(INTENT_SWIPE_OCCURRED);

        localBroadcastManager.registerReceiver(this, intentFilter);

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(this::publishTempAndHum, 0, 5, TimeUnit.SECONDS);

        connected = false;

        clientId = mSharedPreferences.getString(SP_MQTT_DEVICE_ID, "shellywalldisplay");
        if (clientId.equals("shellyelevate") || clientId.equals("shellywalldisplay") || clientId.length() <= 2) {
            clientId = "shellyelevate-" + UUID.randomUUID().toString().replaceAll("-", "").substring(2, 6);
            mSharedPreferences.edit().putString(SP_MQTT_DEVICE_ID, clientId).apply();
        }

        checkCredsAndConnect();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(INTENT_SETTINGS_CHANGED)){
            checkCredsAndConnect();
            return;
        }

        if (!shouldSend())
            return;

        switch (action) {
            case INTENT_LIGHT_UPDATED -> publishLux(intent.getFloatExtra(INTENT_LIGHT_KEY, 0.0f));
            case INTENT_PROXIMITY_UPDATED -> publishProximity(intent.getFloatExtra(INTENT_PROXIMITY_KEY, 0.0f));
            case INTENT_RELAY_UPDATED -> publishRelay(intent.getBooleanExtra(INTENT_RELAY_STATUS_KEY, false));
            case INTENT_SCREEN_SAVER_STARTED -> publishSleeping(true);
            case INTENT_SCREEN_SAVER_STOPPED -> publishSleeping(false);
            case INTENT_SWIPE_OCCURRED -> publishSwipeEvent();
        }
    }

    public void checkCredsAndConnect() {
        if (!mSharedPreferences.getBoolean(SP_MQTT_ENABLED, false)) {
            return;
        }

        validForConnection = !mSharedPreferences.getString(SP_MQTT_PASSWORD, "").isEmpty() && !mSharedPreferences.getString(SP_MQTT_USERNAME, "").isEmpty() && !mSharedPreferences.getString(SP_MQTT_BROKER, "").isEmpty();

        connect();
    }

    public void disconnect() {
        if (mMqttClient != null && mMqttClient.isConnected()) {
            try {
                deleteConfig();
                mMqttClient.publish(parseTopic(MQTT_TOPIC_STATUS), "offline".getBytes(), 1, true);
                mMqttClient.disconnect();

                connected = false;
            } catch (MqttException e) {
                Log.e("MQTT", "Error disconnecting MQTT client", e);
            }
        }
    }

    public void connect() {
        if (validForConnection) {
            try {
                mMqttConnectionsOptions.setUserName(mSharedPreferences.getString(SP_MQTT_USERNAME, ""));
                mMqttConnectionsOptions.setPassword(mSharedPreferences.getString(SP_MQTT_PASSWORD, "").getBytes());
                mMqttConnectionsOptions.setAutomaticReconnect(true);

                if (connected) {
                    disconnect();
                }

                mMqttClient = new MqttClient(mSharedPreferences.getString(SP_MQTT_BROKER, "") + ":" + mSharedPreferences.getInt(SP_MQTT_PORT, 1883), clientId, mMemoryPersistence);
                mMqttClient.setCallback(mShellyElevateMQTTCallback);
                mMqttClient.connect(mMqttConnectionsOptions);

                publishConfig();

                mMqttClient.publish(parseTopic(MQTT_TOPIC_STATUS), "online".getBytes(), 1, true);

                mMqttClient.subscribe("shellyelevatev2/#", 0);
                mMqttClient.subscribe("shellyelevatev2/#", 1);
                mMqttClient.subscribe("shellyelevatev2/#", 2);

                mMqttClient.subscribe(MQTT_TOPIC_HOME_ASSISTANT_STATUS, 0);
                mMqttClient.subscribe(MQTT_TOPIC_HOME_ASSISTANT_STATUS, 1);
                mMqttClient.subscribe(MQTT_TOPIC_HOME_ASSISTANT_STATUS, 2);

                connected = true;

                publishTempAndHum();
                publishRelay(mDeviceHelper.getRelay());
                publishLux(mDeviceSensorManager.getLastMeasuredLux());
                if (DeviceModel.getDevice(mSharedPreferences).hasProximitySensor) {
                    publishProximity(mDeviceSensorManager.getLastMeasuredDistance());
                }
                publishSleeping(mScreenSaverManager.isScreenSaverRunning());

            } catch (MqttException | JSONException e) {
                Log.e("MQTT", "Error connecting:", e);
            }
        }
    }

    public boolean isEnabled() {
        return mSharedPreferences.getBoolean(SP_MQTT_ENABLED, false);
    }

    public boolean shouldSend() {
        return connected && mSharedPreferences.getBoolean(SP_MQTT_ENABLED, false);
    }

    public void publishTempAndHum() {
        if (this.shouldSend()) {
            this.publishTemp((float) mDeviceHelper.getTemperature());
            this.publishHum((float) mDeviceHelper.getHumidity());
        }
    }

    public void publishTemp(float temp) {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_TEMP_SENSOR), String.valueOf(temp).getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing temperature", e);
        }
    }

    public void publishHum(float hum) {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_HUM_SENSOR), String.valueOf(hum).getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing humidity", e);
        }
    }

    private void publishLux(float lux) {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_LUX_SENSOR), String.valueOf(lux).getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing lux", e);
        }
    }

    private void publishProximity(float distance) {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_PROXIMITY_SENSOR), String.valueOf(distance).getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing proximity", e);
        }
    }

    private void publishRelay(boolean state) {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_RELAY_STATE), (state ? "ON" : "OFF").getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing relay state", e);
        }
    }

    private void publishSleeping(boolean state) {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_SLEEPING_BINARY_SENSOR), (state ? "ON" : "OFF").getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing sleeping state", e);
        }
    }

    public void publishSwipeEvent() {
        try {
            mMqttClient.publish(parseTopic(MQTT_TOPIC_SWIPE_EVENT), "{\"event_type\": \"swipe\"}".getBytes(), 1, false);
        } catch (MqttException e) {
            Log.e("MQTT", "Error publishing swipe event", e);
        }
    }

    private void deleteConfig() throws MqttException {
        mMqttClient.publish(parseTopic(MQTT_TOPIC_CONFIG_DEVICE), "".getBytes(), 1, false);
    }

    private void publishConfig() throws JSONException, MqttException {
        JSONObject configPayload = new JSONObject();

        JSONObject device = new JSONObject();
        device.put("ids", clientId);
        device.put("name", "Shelly Wall Display");
        device.put("mf", "Shelly");
        configPayload.put("dev", device);

        JSONObject origin = new JSONObject();
        origin.put("name", "ShellyElevateV2");
        origin.put("url", "https://github.com/RapierXbox/ShellyElevate");
        configPayload.put("o", origin);

        JSONObject components = new JSONObject();

        JSONObject tempSensorPayload = new JSONObject();
        tempSensorPayload.put("p", "sensor");
        tempSensorPayload.put("name", "Temperature");
        tempSensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_TEMP_SENSOR));
        tempSensorPayload.put("device_class", "temperature");
        tempSensorPayload.put("unit_of_measurement", "°C");
        tempSensorPayload.put("unique_id", clientId + "_temp");
        components.put(clientId + "_temp", tempSensorPayload);

        JSONObject humSensorPayload = new JSONObject();
        humSensorPayload.put("p", "sensor");
        humSensorPayload.put("name", "Humidity");
        humSensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_HUM_SENSOR));
        humSensorPayload.put("device_class", "humidity");
        humSensorPayload.put("unit_of_measurement", "%");
        humSensorPayload.put("unique_id", clientId + "_hum");
        components.put(clientId + "_hum", humSensorPayload);

        JSONObject luxSensorPayload = new JSONObject();
        luxSensorPayload.put("p", "sensor");
        luxSensorPayload.put("name", "Light");
        luxSensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_LUX_SENSOR));
        luxSensorPayload.put("device_class", "illuminance");
        luxSensorPayload.put("unit_of_measurement", "lx");
        luxSensorPayload.put("unique_id", clientId + "_lux");
        components.put(clientId + "_lux", luxSensorPayload);

        if (DeviceModel.getDevice(mSharedPreferences).hasProximitySensor) {
            JSONObject proximitySensorPayload = new JSONObject();
            proximitySensorPayload.put("p", "sensor");
            proximitySensorPayload.put("name", "Proximity");
            proximitySensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_PROXIMITY_SENSOR));
            proximitySensorPayload.put("device_class", "distance");
            proximitySensorPayload.put("unit_of_measurement", "cm");
            proximitySensorPayload.put("unique_id", clientId + "_proximity");
            components.put(clientId + "_proximity", proximitySensorPayload);
        }

        JSONObject relaySwitchPayload = new JSONObject();
        relaySwitchPayload.put("p", "switch");
        relaySwitchPayload.put("name", "Relay");
        relaySwitchPayload.put("state_topic", parseTopic(MQTT_TOPIC_RELAY_STATE));
        relaySwitchPayload.put("command_topic", parseTopic(MQTT_TOPIC_RELAY_COMMAND));
        relaySwitchPayload.put("device_class", "outlet");
        relaySwitchPayload.put("unique_id", clientId + "_relay");
        components.put(clientId + "_relay", relaySwitchPayload);

        JSONObject sleepButtonPayload = new JSONObject();
        sleepButtonPayload.put("p", "button");
        sleepButtonPayload.put("name", "Sleep");
        sleepButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_SLEEP_BUTTON));
        sleepButtonPayload.put("unique_id", clientId + "_sleep");
        components.put(clientId + "_sleep", sleepButtonPayload);

        JSONObject wakeButtonPayload = new JSONObject();
        wakeButtonPayload.put("p", "button");
        wakeButtonPayload.put("name", "Wake");
        wakeButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_WAKE_BUTTON));
        wakeButtonPayload.put("unique_id", clientId + "_wake");
        components.put(clientId + "_wake", wakeButtonPayload);

        JSONObject refreshWebviewButtonPayload = new JSONObject();
        refreshWebviewButtonPayload.put("p", "button");
        refreshWebviewButtonPayload.put("name", "Refresh Webview");
        refreshWebviewButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_REFRESH_WEBVIEW_BUTTON));
        refreshWebviewButtonPayload.put("device_class", "restart");
        refreshWebviewButtonPayload.put("unique_id", clientId + "_refresh_webview");
        components.put(clientId + "_refresh_webview", refreshWebviewButtonPayload);

        JSONObject rebootButtonPayload = new JSONObject();
        rebootButtonPayload.put("p", "button");
        rebootButtonPayload.put("name", "Reboot");
        rebootButtonPayload.put("command_topic", parseTopic(MQTT_TOPIC_REBOOT_BUTTON));
        rebootButtonPayload.put("device_class", "restart");
        rebootButtonPayload.put("unique_id", clientId + "_reboot");
        components.put(clientId + "_reboot", rebootButtonPayload);

        JSONObject swipeEventPayload = new JSONObject();
        swipeEventPayload.put("p", "event");
        swipeEventPayload.put("name", "Swipe Event");
        swipeEventPayload.put("state_topic", parseTopic(MQTT_TOPIC_SWIPE_EVENT));
        swipeEventPayload.put("device_class", "button");
        swipeEventPayload.put("event_types", new JSONArray().put("swipe"));
        swipeEventPayload.put("unique_id", clientId + "_swipe_event");
        components.put(clientId + "_swipe_event", swipeEventPayload);

        JSONObject sleepingBinarySensorPayload = new JSONObject();
        sleepingBinarySensorPayload.put("p", "binary_sensor");
        sleepingBinarySensorPayload.put("name", "Sleeping");
        sleepingBinarySensorPayload.put("state_topic", parseTopic(MQTT_TOPIC_SLEEPING_BINARY_SENSOR));
        sleepingBinarySensorPayload.put("unique_id", clientId + "_sleeping");
        components.put(clientId + "_sleeping", sleepingBinarySensorPayload);

        configPayload.put("cmps", components);

        configPayload.put("state_topic", MQTT_TOPIC_STATUS);

        mMqttClient.publish(parseTopic(MQTT_TOPIC_CONFIG_DEVICE), configPayload.toString().getBytes(), 1, true);
    }

    private String parseTopic(String topic) {
        return topic.replace("%s", clientId);
    }

    public String getClientId() {
        return clientId;
    }

    public void onDestroy() {
        disconnect();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
