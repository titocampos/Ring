/*
 * Reference: https://github.com/bauerjj/Android-Simple-Bluetooth-Example
 */
package com.mecatronica.ring;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Set;

public class SettingsActivity extends AbstractActivity implements ServiceConnection {
    private Switch mBtSwitch;
    private Button mDiscoverBtn;
    private TextView mBtStatusText;
    private StringAdapter mDevListAdapter;
    private ListView mDevicesList;
    private BluetoothAdapter mBTAdapter;
    private BluetoothListener mBluetoothListener = null;

    @Override
    protected void onDestroy() {
        unregisterReceiver(mBTReceiver);

        if (mBluetoothListener != null) {
            unbindService(this);
            mBluetoothListener = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBtSwitch.isChecked() && (mBluetoothListener != null) && !mBluetoothListener.bluetoothIsOn())
            mBluetoothListener.connectDevice();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        mIntentFilter.addAction(AbstractActivity.ACTION_CONNECTED);
        mIntentFilter.addAction(AbstractActivity.ACTION_CONNECTION_FAILURE);
        registerReceiver(mBTReceiver, mIntentFilter);

        mDevListAdapter = new StringAdapter(this, new ArrayList<StringItem>());
        mDevicesList = (ListView)findViewById(R.id.devicesList);
        mDevicesList.setAdapter(mDevListAdapter);
        mDevicesList.setOnItemClickListener(mDevicesClickListener);

        mBtSwitch = (Switch) findViewById(R.id.btSwitch);
        mBtStatusText = (TextView)findViewById(R.id.btStatusText);
        mDiscoverBtn = (Button)findViewById(R.id.discoverBtn);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBTAdapter == null) {
            mBtStatusText.setText("Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        } else {
            if (mBTAdapter != null && mBTAdapter.isEnabled()) {
                setStateBluetoothOn();
            } else {
                setStateBluetoothOff();
            }

            mBtSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                    if(isChecked) {
                        bluetoothOn(v);
                    } else {
                        bluetoothOff(v);
                        mDevListAdapter.clear();
                    }
                    setPreferenceStatus(isChecked);
                }
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    discover();
                }
            });
        }
        if (null == savedInstanceState) {
            if (mBluetoothListener == null) {
                Intent intent = new Intent(this, BluetoothService.class);
                bindService(intent, this, Context.BIND_AUTO_CREATE);
            }
        }
    }

    private void setPreferenceStatus(boolean isOn){
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getString(R.string.pref_status), isOn);
        editor.apply();
    }

    private void setPreferenceDevice(String device){
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.pref_device), device);
        editor.apply();
    }

    private String getPreferenceDevice(){
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);
        return sharedPreferences.getString(getString(R.string.pref_device), "");
    }

    private void listPairedDevices() {
        mDevListAdapter.clear();
        if(mBTAdapter.isEnabled()) {
            Set<BluetoothDevice> mPairedDevices = mBTAdapter.getBondedDevices();
            String mDevice = getPreferenceDevice();
            for (BluetoothDevice device : mPairedDevices) {
                if (mDevice.equals(device.getAddress())) {
                    mDevListAdapter.add(new StringItem(device.getName(), device.getAddress(), R.drawable.ic_checked));
                } else {
                    mDevListAdapter.add(new StringItem(device.getName(), device.getAddress()));
                }
            }
        }
    }

    private void discover() {
        if(mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Discovery stopped",Toast.LENGTH_SHORT).show();
        } else {
            if(mBTAdapter.isEnabled()) {
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void bluetoothOn(View view) {
        if (mBTAdapter != null) {
            if (!mBTAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth is already on", Toast.LENGTH_SHORT).show();
            }
        } else {
            mBtStatusText.setText("Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
    }

    private void bluetoothOff(View view) {
        if (isBluetoothAvailable())
            mBluetoothListener.disconnectDevice();

        mBTAdapter.disable();
        mBtStatusText.setText("Bluetooth disabled");
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void setStateBluetoothOn(){
        mBtSwitch.setChecked(true);
        if (!isBluetoothAvailable())
            mBtStatusText.setText("Bluetooth enabled");
        else
            mBtStatusText.setText("Bluetooth connected to " + mBluetoothListener.getDeviceName());
        listPairedDevices();
        setPreferenceStatus(true);
    }

    private void setStateBluetoothOff(){
        mBtSwitch.setChecked(false);
        mBtStatusText.setText("Bluetooth Disabled");
        setPreferenceStatus(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                setStateBluetoothOn();
            } else {
                setStateBluetoothOff();
            }
        }
    }

    private boolean isBluetoothAvailable(){
        return ((mBluetoothListener != null) && mBluetoothListener.bluetoothIsOn());
    }

    private AdapterView.OnItemClickListener mDevicesClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int position, long arg3) {
            StringItem item = mDevListAdapter.getItem(position);
            setPreferenceDevice(item.getDescripcion());
            listPairedDevices();
            if (isBluetoothAvailable()) {
                mBluetoothListener.disconnectDevice();
                mBtStatusText.setText("Desconnected from radio");
            }
            else if (null != mBluetoothListener ){
                mBluetoothListener.connectDevice();
                mBtStatusText.setText("Connecting to radio");
            }
        }
    };

    final BroadcastReceiver mBTReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDevListAdapter.add(new StringItem(device.getName(), device.getAddress()));
                mDevListAdapter.notifyDataSetChanged();
            } else  if(AbstractActivity.ACTION_CONNECTED.equals(action)){
                String device = intent.getStringExtra("data");
                mBtStatusText.setText("Bluetooth connected to " + device);
            } else  if(AbstractActivity.ACTION_CONNECTION_FAILURE.equals(action)) {
                mBtStatusText.setText("Bluetooth failed to connect");
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        BluetoothService.Controller c = (BluetoothService.Controller) service;
        mBluetoothListener = c.getListener();
        if (isBluetoothAvailable())
            mBtStatusText.setText("Bluetooth connected to " + mBluetoothListener.getDeviceName());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}