package com.github.omadahealth.lollipin.lib;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.omadahealth.lollipin.lib.interfaces.LifeCycleInterface;
import com.github.omadahealth.lollipin.lib.managers.AppLockActivity;

public class PinProtectorLifecycleObserver implements DefaultLifecycleObserver {

    private final Activity activity;
    private static LifeCycleInterface mLifeCycleListener;
    private final BroadcastReceiver mPinCancelledReceiver;

    public PinProtectorLifecycleObserver(Activity activity) {
        this.activity = activity;
        mPinCancelledReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                activity.finish();
            }
        };
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        IntentFilter filter = new IntentFilter(AppLockActivity.ACTION_CANCEL);
        LocalBroadcastManager.getInstance(activity).registerReceiver(mPinCancelledReceiver, filter);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (mLifeCycleListener != null) {
            mLifeCycleListener.onActivityResumed(activity);
        }
    }

    public void onUserInteraction() {
        if (mLifeCycleListener != null){
            mLifeCycleListener.onActivityUserInteraction(activity);
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (mLifeCycleListener != null) {
            mLifeCycleListener.onActivityPaused(activity);
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(mPinCancelledReceiver);
    }

    public static void setListener(LifeCycleInterface listener) {
        if (mLifeCycleListener != null) {
            mLifeCycleListener = null;
        }
        mLifeCycleListener = listener;
    }

    public static void clearListeners() {
        mLifeCycleListener = null;
    }

    public static boolean hasListeners() {
        return (mLifeCycleListener != null);
    }

}
