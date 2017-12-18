package de.htwg.moco.bulbdj.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeResourcesCache;
import com.philips.lighting.model.PHHueParsingError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import butterknife.ButterKnife;
import butterknife.OnClick;
import de.htwg.moco.bulbdj.R;
import de.htwg.moco.bulbdj.bridge.BridgeController;
import de.htwg.moco.bulbdj.data.ConnectionProperties;
import de.htwg.moco.bulbdj.detector.AudioManager;
import de.htwg.moco.bulbdj.detector.BeatDetector;
import de.htwg.moco.bulbdj.renderers.LEDRenderer;
import de.htwg.moco.bulbdj.views.DemoView;
import de.htwg.moco.bulbdj.views.VisualizerView;

/**
 * Class represents main activity that is shown when application is started.
 * <br>
 * Functionalities of app:
 * <p>Setup of connection to the bridge
 * <p>Listening to the surrounding sound and tweeting the lights depending on the rhythm
 * and user preferences.
 *
 * ...TO DO...
 *
 * @author Mislav Jurić
 * @version 1.0
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Result code identificator.
     */
    public static final int DISPLAY_RESULT_CODE = 500;

    /**
     * Permission request for record audio.
     */
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;


    private AudioManager audioManager;
    private LEDRenderer ledRenderer;

    private VisualizerView visualizerView;
    private DemoView demoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        BridgeController.setContext(this);
        BridgeController.getInstance().registerPhsdkListener(phsdkListener);

        if (BridgeController.getInstance().getConnectionProperties().isAutoStart() &&
                BridgeController.getInstance().propertiesDefined()) {
            autoConnect();
        }

        Button recordButton = (Button) findViewById(R.id.start_stop_btn);
        visualizerView = (VisualizerView) findViewById(R.id.visualizerView);
        visualizerView.setRadius(recordButton.getWidth());
        demoView = (DemoView) findViewById(R.id.demoView);
        ledRenderer = new LEDRenderer();
        audioManager = AudioManager.getInstance();

        audioManager.setAudioMangerListener(new AudioManager.AudioManagerListener() {
            @Override
            public void onBeatDetected(ArrayList<BeatDetector.BEAT_TYPE> beats) {
                if (audioManager.isDetectorOn())
                    ledRenderer.updateBeats(beats);
            }

            @Override
            public void onStop() {
                visualizerView.stop();
                ledRenderer.stop();
            }

            @Override
            public void onUpdated(double[] result) {
                visualizerView.updateVisualizer(result);
                if (!audioManager.isDetectorOn())
                    ledRenderer.updateFrequency(result);
            }

        });

        ledRenderer.setLEDRendererListener(new LEDRenderer.LEDRendererListener() {

            @Override
            public void onUpdate(int r, int g, int b) {
                if (!BridgeController.getInstance().isConnected()) {
                    // Show LEDs on Display
                    demoView.updateVisualizer(r, g, b);
                } else {
                    // TODO test
                    // SHow LEDs on PHBridge
                    PHBridge bridge = BridgeController.getInstance().getPHHueSDK().getSelectedBridge();
                    PHBridgeResourcesCache cache = bridge.getResourceCache();

                    List<PHLight> allLights = cache.getAllLights();

                    if (allLights.size() == 3) {
                        PHLight lightR = allLights.get(0);
                        PHLightState lightStateR = new PHLightState();
                        lightStateR.setHue(0);
                        lightStateR.setBrightness(lightR.getLastKnownLightState().getBrightness());
                        bridge.updateLightState(lightR, lightStateR);

                        PHLight lightG = allLights.get(1);
                        PHLightState lightStateG = new PHLightState();
                        lightStateG.setHue(25500);
                        lightStateG.setBrightness(lightG.getLastKnownLightState().getBrightness());
                        bridge.updateLightState(lightG, lightStateG);

                        PHLight lightB = allLights.get(2);
                        PHLightState lightStateB = new PHLightState();
                        lightStateB.setHue(46920);
                        lightStateB.setBrightness(lightB.getLastKnownLightState().getBrightness());
                        bridge.updateLightState(lightB, lightStateB);
                    } else {
                        Random rand = new Random();
                        for (PHLight light : allLights) {
                            PHLightState lightState = new PHLightState();
                            lightState.setHue(rand.nextInt(65535));
                            lightState.setBrightness(light.getLastKnownLightState().getBrightness());
                            bridge.updateLightState(light, lightState);
                        }
                    }
                }
            }

            @Override
            public void onStop() {
                demoView.stop();
            }
        });

        loadSettings();

        if (!BridgeController.getInstance().isConnected()) {
            demoView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Load all settings.
     */
    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences("beatDetection", MODE_PRIVATE);
        int sensitivity = settings.getInt("sensitivity", -1);
        audioManager.setSettings(sensitivity);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecorder();

                } else {
                    stopRecorder();
                }
                return;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, DISPLAY_RESULT_CODE);
                return true;
            case R.id.action_manual:
                Intent i = new Intent(this, ManualActivity.class);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Method called if properties are set. Automatically connects to the bridge, without interrupting the user.
     */
    private void autoConnect() {
        ConnectionProperties connectionProperties = BridgeController.getInstance().getConnectionProperties();
        String ipAddress = connectionProperties.getIpAddress();
        String userName = connectionProperties.getUserName();

        PHAccessPoint accessPoint = new PHAccessPoint(ipAddress, userName, null);
        BridgeController.getInstance().getPHHueSDK().connect(accessPoint);
    }

    /**
     * Start the recorder.
     * @param button
     */
    private void startRecorder(Button button)
    {
        button.setText(R.string.stop);
        startRecorder();
    }

    /**
     * Start the recorder.
     */
    private void startRecorder()
    {
        audioManager.start();
    }

    /**
     * Stop the recorder.
     * @param button
     */
    private void stopRecorder(Button button)
    {
        button.setText(R.string.record);
        stopRecorder();
    }

    /**
     * Stop the recorder.
     */
    private void stopRecorder()
    {
        audioManager.stop();
    }

    @OnClick(R.id.start_stop_btn)
    void startStop(Button button) {
        if (audioManager.isRunning()) {
            stopRecorder(button);
        } else {
            checkMicPermission();
            startRecorder(button);
        }
    }

    /**
     * Check for microphone permissions.
     */
    private void checkMicPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
    }

    /*
    @OnClick(R.id.random_lights)
    void randomLights() {
        if (!BridgeController.getInstance().isConnected()) return;
        PHBridge bridge = BridgeController.getInstance().getPHHueSDK().getSelectedBridge();
        PHBridgeResourcesCache cache = bridge.getResourceCache();

        List<PHLight> allLights = cache.getAllLights();
        Random rand = new Random();

        for (PHLight light : allLights) {

            PHLightState lightState = new PHLightState();
            lightState.setHue(rand.nextInt(65535));
            lightState.setBrightness(light.getLastKnownLightState().getBrightness());
            bridge.updateLightState(light, lightState);
        }
    }
    */

    /**
     * Method initializes listener for the seek bar.
     */
    /*
    void setSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (!BridgeController.getInstance().isConnected()) return;
                PHBridge bridge = BridgeController.getInstance().getPHHueSDK().getSelectedBridge();
                PHBridgeResourcesCache cache = bridge.getResourceCache();

                List<PHLight> allLights = cache.getAllLights();
                Random rand = new Random();

                for (PHLight light : allLights) {

                    PHLightState lightState = new PHLightState();
                    lightState.setHue(light.getLastKnownLightState().getHue());
                    lightState.setBrightness((int) (2.55 * i));
                    bridge.updateLightState(light, lightState);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }
    */

    /**
     * Method is executed on activity result.
     * <br>In case result code equals SetupActivity.STATUS_OK, app is connected to bridge.
     *
     * @param requestCode request code
     * @param resultCode  result code
     * @param data        intent data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DISPLAY_RESULT_CODE && resultCode == SettingsActivity.STATUS_OK) {
            Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
        }
        if (requestCode == DISPLAY_RESULT_CODE && resultCode == SettingsActivity.STATUS_DISCONNECTED) {
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Listener for notifications from Philips Hue SDK.
     */
    private PHSDKListener phsdkListener = new PHSDKListener() {

        @Override
        public void onAccessPointsFound(List<PHAccessPoint> accessPointsList) {
        }

        @Override
        public void onAuthenticationRequired(PHAccessPoint accessPoint) {
        }

        /**
         * If bridge connected, user is notified by toast message.
         * @param bridge connected bridge
         * @param username given username by bridge, which is needed to authenticate for auto connection
         */
        @Override
        public void onBridgeConnected(PHBridge bridge, String username) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                }
            });
            BridgeController.getInstance().setConnected(true);
        }

        @Override
        public void onCacheUpdated(List<Integer> cache, PHBridge bridge) {
        }

        @Override
        public void onConnectionLost(PHAccessPoint accessPoint) {
        }

        @Override
        public void onConnectionResumed(PHBridge bridge) {
        }

        @Override
        public void onError(int code, final String message) {
        }

        @Override
        public void onParsingErrors(List<PHHueParsingError> parsingErrorsList) {
        }
    };

    /**
     * Before destroying, {@link BridgeController} instance is terminated.
     */
    @Override
    protected void onDestroy() {
        BridgeController.getInstance().terminate();
        super.onDestroy();
    }

}
