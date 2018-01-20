package com.mecatronica.ring;

import android.support.v7.app.AppCompatActivity;

public abstract class AbstractActivity extends AppCompatActivity {
    public static final int REQUEST_ENABLE_BT = 1;
    public static final String ACTION_CONNECTED =
            "com.mecatronica.ring.action.CONNECTED";
    public static final String ACTION_CONNECTION_FAILURE =
            "com.mecatronica.ring.action.FAILURE";


}
