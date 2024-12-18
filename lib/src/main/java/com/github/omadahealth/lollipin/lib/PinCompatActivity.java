package com.github.omadahealth.lollipin.lib;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by callmepeanut on 16-1-14.
 * You must extend this Activity in order to support this library.
 * Then to enable PinCode blocking, you must call
 * {@link com.github.omadahealth.lollipin.lib.managers.LockManager#enableAppLock(android.content.Context, Class)}
 */
public class PinCompatActivity extends AppCompatActivity {
    private PinProtectorLifecycleObserver pinProtector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pinProtector = new PinProtectorLifecycleObserver(this);
        getLifecycle().addObserver(pinProtector);
    }

    @Override
    public void onUserInteraction() {
        pinProtector.onUserInteraction();
        super.onUserInteraction();
    }
}
