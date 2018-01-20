package com.mecatronica.ring;
/*
*  Based in https://stackoverflow.com/questions/15025852/how-to-move-bluetooth-activity-into-a-service
* */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService extends Service implements BluetoothListener {
    private final int STATE_NONE = 0;
    private final int STATE_CONNECTING = 1;
    private final int STATE_CONNECTED = 2;
    private static final String TAG = BluetoothService.class.getSimpleName();

    private Controller controller = new Controller();
    private BluetoothAdapter mBTAdapter;
    private BluetoothSocket mBTSocket = null;
    private BluetoothDevice mDevice;
    private UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState = STATE_NONE;
    private static Object obj = new Object();

    public class Controller extends Binder {
        public BluetoothListener getListener(){
            return(BluetoothService.this);
        }
    }

    public String getDeviceName(){
        return ((null != mDevice)?mDevice.getName():null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return controller;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    @Override
    public void sendMessage(String input) {
        ConnectedThread r;

        synchronized (obj) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
        }

        r.write(input);
    }

    @Override
    public boolean bluetoothIsOn() {
        return (mState == STATE_CONNECTED);
    }

    @Override
    public void connectDevice() {
        String deviceAddress = getPreferenceDevice();

        if (mBTAdapter.isEnabled() && !deviceAddress.equals("")) {
            this.mDevice = mBTAdapter.getRemoteDevice(deviceAddress);
            if (mState == STATE_CONNECTING) {
                if (mConnectThread != null) {
                    mConnectThread.cancel();
                    mConnectThread = null;
                }
            }

            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            mConnectThread = new ConnectThread(mDevice);
            mConnectThread.start();
            setState(STATE_CONNECTING);
        }else{
            broadcastFailure();
        }
    }

    @Override
    public void disconnectDevice() {
        if (mState == STATE_CONNECTING || mState == STATE_CONNECTED) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }

            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
        }
    }

    private void setState(int state) {

        if (mState == STATE_CONNECTING && state == STATE_NONE)
            broadcastFailure();
        mState = state;
    }

    private String getPreferenceDevice(){
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);
        return sharedPreferences.getString(getString(R.string.pref_device), "");
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mSocket;
        private final BluetoothDevice mDevice;

        public ConnectThread(BluetoothDevice device) {
            this.mDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(BTMODULEUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = tmp;
        }

        @Override
        public void run() {
            setName("ConnectThread");
            mBTAdapter.cancelDiscovery();
            try {
                mSocket.connect();
            } catch (IOException e) {
                try {
                    mSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                setState(STATE_NONE);
                return;
            }
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }
            connected(mSocket, mDevice);
        }

        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private synchronized void connected(BluetoothSocket mSocket, BluetoothDevice mDevice) {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(mSocket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);

        Intent intent = new Intent();
        intent.setAction(AbstractActivity.ACTION_CONNECTED);
        intent.setPackage("com.mecatronica.ring");
        intent.putExtra("data", mDevice.getName());
        sendBroadcast(intent);
    }

    private void broadcastFailure() {
        Intent intent = new Intent();
        intent.setAction(AbstractActivity.ACTION_CONNECTION_FAILURE);
        intent.setPackage("com.mecatronica.ring");
        sendBroadcast(intent);
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    setState(STATE_NONE);
                    break;
                }
            }
        }

        public void write(String input) {
            byte[] bytes = input.getBytes();
            if (!mmSocket.isConnected()){
                setState(STATE_NONE);
                return;
            }
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        public void cancel() {
            try {
                mmSocket.close();
                setState(STATE_NONE);

            } catch (IOException e) {
            }
        }
    }

    private synchronized void stop() {
        setState(STATE_NONE);
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }
}
