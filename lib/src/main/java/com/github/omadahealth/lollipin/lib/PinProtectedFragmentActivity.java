package com.github.omadahealth.lollipin.lib;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

/**
 * Created by stoyan and olivier on 1/12/15.
 * You must extend this Activity in order to support this library.
 * Then to enable PinCode blocking, you must call
 * {@link com.github.omadahealth.lollipin.lib.managers.LockManager#enableAppLock(android.content.Context, Class)}
 */
public class PinProtectedFragmentActivity extends FragmentActivity {

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
