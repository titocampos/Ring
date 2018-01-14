/*
 * Reference: https://github.com/googlesamples/android-Camera2Basic
 */
package com.mecatronica.ring;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

public class CameraActivity extends AbstractActivity implements ServiceConnection{
    Camera2SelfieFragment mCamera2SelfieFragment = null;
    private BluetoothListener mBluetoothListener = null;
    private static final String TAG = CameraActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(flags);
                }
            }
        });

        if (null == savedInstanceState) {
            mCamera2SelfieFragment = Camera2SelfieFragment.newInstance();
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, mCamera2SelfieFragment)
                    .commit();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        BluetoothService.Controller c = (BluetoothService.Controller) service;
        mBluetoothListener = c.getListener();

        if (mCamera2SelfieFragment != null)
            mCamera2SelfieFragment.setBluetoothListener(mBluetoothListener);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        if(mBluetoothListener != null) {
            unbindService(this);
            mBluetoothListener = null;
        }
    }


    protected void onResume() {
        super.onResume();

        if (mBluetoothListener == null) {
            Intent intent = new Intent(this, BluetoothService.class);
            bindService(intent, this, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    onVolumeKeyDown();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    onVolumeKeyDown();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void onVolumeKeyDown() {
        if (mCamera2SelfieFragment != null) {
            mCamera2SelfieFragment.takePicture();
        }
    }
}
