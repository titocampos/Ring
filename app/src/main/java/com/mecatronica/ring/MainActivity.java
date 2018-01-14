/*
 * Reference: https://github.com/bauerjj/Android-Simple-Bluetooth-Example
 */
package com.mecatronica.ring;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends AbstractActivity implements SensorEventListener, ServiceConnection {
    private ListView mDemoList;
    private boolean mShowAccelerometer = false;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private BluetoothListener mBluetoothListener = null;
    private BluetoothAdapter mBTAdapter;
    private boolean startedBT = false;
    private static final String TAG = MainActivity.class.getSimpleName();
    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy(){
        if (startedBT){
            mBTAdapter.disable();
        }

        if(mBluetoothListener != null) {
            unbindService(this);
            mBluetoothListener = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        if ((mBluetoothListener != null) && !mBluetoothListener.bluetoothIsOn())
            mBluetoothListener.connectDevice();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (mShowAccelerometer && (sensorEvent.sensor == mAccelerometer) && isBluetoothAvailable()) {
            double aX = (double) (sensorEvent.values[0]);
            double aY = (double) (sensorEvent.values[1]);
            double angle = Math.atan2(aX, aY) / (Math.PI / 180);

            if (angle > 135 && angle < 165) {
                mBluetoothListener.sendMessage("A");
            } else if (angle > 105 && angle < 135) {
                mBluetoothListener.sendMessage("B");
            } else if (angle > 75 && angle < 105) {
                mBluetoothListener.sendMessage("C");
            } else if (angle > 45 && angle < 75) {
                mBluetoothListener.sendMessage("D");
            } else if (angle > 15 && angle < 45) {
                mBluetoothListener.sendMessage("E");
            } else if (angle > -15 && angle < 15) {
                mBluetoothListener.sendMessage("F");
            } else if (angle > -45 && angle < -15) {
                mBluetoothListener.sendMessage("G");
            } else if (angle > -75 && angle < -45) {
                mBluetoothListener.sendMessage("H");
            } else if (angle > -105 && angle < -75) {
                mBluetoothListener.sendMessage("I");
            } else if (angle > -135 && angle < -105) {
                mBluetoothListener.sendMessage("J");
            } else if (angle > -165 && angle < -135) {
                mBluetoothListener.sendMessage("K");
            } else if (angle > 165 || angle < -165) {
                mBluetoothListener.sendMessage("L");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBTAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_LONG).show();
            finish();
            return;
        } else {
            if (!mBTAdapter.isEnabled() && getPreferenceStatus()) {
                startedBT = true;
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        StringAdapter itemsAdapter = new StringAdapter(this, listDemoOptions());

        mDemoList = (ListView) findViewById(R.id.demoList);
        mDemoList.setAdapter(itemsAdapter);
        mDemoList.setOnItemClickListener(mDemoClickListener);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        if (null == savedInstanceState) {
            if (mBluetoothListener == null) {
                Intent intent = new Intent(this, BluetoothService.class);
                bindService(intent, this, Context.BIND_AUTO_CREATE);
            }
        }
    }

    private boolean isBluetoothAvailable(){
        //ler das sharedpreferences
        return ((mBluetoothListener != null) && mBluetoothListener.bluetoothIsOn());
    }

    private ArrayList<StringItem> listDemoOptions() {
        final ArrayList<StringItem> items = new ArrayList<StringItem>();
            /*  0  */
        items.add(new StringItem("OFF", "Turn off all leds"));
            /*  1  */
        items.add(new StringItem("SIGNALING", "Directions pointer demo"));
            /*  2  */
        items.add(new StringItem("PULSE", "Simple PWM demo"));
            /*  3  */
        items.add(new StringItem("TIMER","A 12s timer indicator"));
            /*  4  */
        items.add(new StringItem("PROGRESS 1", "Progress bar demo"));
            /*  5  */
        items.add(new StringItem("PROGRESS 2","Progress bar demo"));
            /*  6  */
        items.add(new StringItem("FLASH","Flash lights"));
            /*  7  */
        items.add(new StringItem("LIGHT","You got a 12 times brighter light!"));
            /*  8  */
        items.add(new StringItem("ACCELEROMETER", "Gravity on led demo"));
            /*  9  */
        items.add(new StringItem("SELFIE ASSIST","Be guided while making a selfie"));
            /* A to L => Single led from 1 to 12 */
            /* u => Up    */
            /* d => Down  */
            /* l => Left  */
            /* r => Right */
        return items;
    }

    private void runCamera() {
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    private AdapterView.OnItemClickListener mDemoClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int position, long arg3) {
            if (null == mBluetoothListener)
                return;

            if (position <= 8 && !isBluetoothAvailable()) {
                Toast.makeText(getBaseContext(), "Bluetooth not connected!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (position <= 7) {
                mShowAccelerometer = false;
                mBluetoothListener.sendMessage("" + (char) (position + '0'));
            } else if (position == 8) {
                mShowAccelerometer = true;
            } else if (position == 9) {
                mShowAccelerometer = false;
                runCamera();
            }

            return;
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        BluetoothService.Controller c = (BluetoothService.Controller) service;
        mBluetoothListener = c.getListener();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public boolean getPreferenceStatus(){
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(getString(R.string.pref_status), false);
    }
}
