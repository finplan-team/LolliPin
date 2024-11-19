/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.github.omadahealth.lollipin.lib.managers;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
// import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.github.omadahealth.lollipin.lib.R;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.LocalDate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * Small helper class to manage
 * - cipher keys generation and use
 * - text/icon around fingerprint authentication UI.
 */
@TargetApi(Build.VERSION_CODES.M)
public class FingerprintUiHelper extends BiometricPrompt.AuthenticationCallback {

    /**
     * The timeout for the error to be displayed. Returns to the normal UI after this.
     */
    private static final long ERROR_TIMEOUT_MILLIS = 1600;
    /**
     * The timeout for the success to be displayed. Calls {@link Callback#onAuthenticated()} after this.
     */
    private static final long SUCCESS_DELAY_MILLIS = 1300;
    /**
     * Alias for our key in the Android Key Store
     **/
    private static final String KEY_NAME = "my_key";

    /**
     * The {@link Cipher} used to init {@link FingerprintManager}
     */
    private Cipher mCipher;
    /**
     * The {@link KeyStore} used to initiliaze the key {@link #KEY_NAME}
     */
    private KeyStore mKeyStore;
    /**
     * The {@link KeyGenerator} used to generate the key {@link #KEY_NAME}
     */
    private KeyGenerator mKeyGenerator;
    /**
     * The {@link android.hardware.fingerprint.FingerprintManager.CryptoObject}
     */
    //private final FingerprintManager mFingerprintManager;
    /**
     * The {@link ImageView} that is used to show the authent state
     */
    private final ImageView mIcon;
    /**
     * The {@link TextView} that is used to show the authent state
     */
    private final TextView mErrorTextView;
    /**
     * The {@link com.github.omadahealth.lollipin.lib.managers.FingerprintUiHelper.Callback} used to return success or error.
     */
    private final Callback mCallback;
    /**
     * The {@link CancellationSignal} used after an error happens
     */
    private CancellationSignal mCancellationSignal;
    /**
     * Used if the user cancelled the authentication by himself
     */
    private boolean mSelfCancelled;

    private final Context context;

    private final BiometricPrompt biometricPrompt;

    private final BiometricPrompt.PromptInfo promptInfo;

    /**
     * Builder class for {@link FingerprintUiHelper} in which injected fields from Dagger
     * holds its fields and takes other arguments in the {@link #build} method.
     */
    public static class FingerprintUiHelperBuilder {
        //private final FingerprintManager mFingerPrintManager;
        private FragmentActivity context;

        public FingerprintUiHelperBuilder() {
            // mFingerPrintManager = fingerprintManager;
        }

        public FingerprintUiHelperBuilder setContext(FragmentActivity context) {
            this.context = context;
            return this;
        }

        public FingerprintUiHelper build(ImageView icon, TextView errorTextView, Callback callback) {
            return new FingerprintUiHelper(context, icon, errorTextView,
                    callback);
        }
    }

    /**
     * Constructor for {@link FingerprintUiHelper}. This method is expected to be called from
     * only the {@link FingerprintUiHelperBuilder} class.
     */
    private FingerprintUiHelper(
            FragmentActivity context,
            // FingerprintManager fingerprintManager,
                                ImageView icon, TextView errorTextView, Callback callback) {
        // mFingerprintManager = fingerprintManager;
        mIcon = icon;
        mErrorTextView = errorTextView;
        mCallback = callback;
        this.context = context;
        this.biometricPrompt = new BiometricPrompt(
                context,
                ContextCompat.getMainExecutor(context),
                this
        );
        this.promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.fingerprint_dialog_title))
                .setSubtitle(context.getString(R.string.fingerprint_dialog_subtitle))
                .setDescription(context.getString(R.string.fingerprint_dialog_description))
                // Authenticate without requiring the user to press a "confirm"
                // button after satisfying the biometric check
                .setConfirmationRequired(false)
                .setNegativeButtonText(context.getString(R.string.fingerprint_dialog_negative_btn_text))
                .build();
    }

    /**
     * Starts listening to {@link FingerprintManager}
     *
     * @throws SecurityException If the hardware is not available, or the permission are not set
     */
    public void startListening() throws SecurityException {
        if (initCipher()) {
            BiometricPrompt.CryptoObject cryptoObject = new BiometricPrompt.CryptoObject(mCipher);
            if (!isFingerprintAuthAvailable()) {
                Log.d("fui", "fingerprintAuthAvailable() returned false");
                return;
            }
            mCancellationSignal = new CancellationSignal();
            mSelfCancelled = false;
            biometricPrompt.authenticate(promptInfo, cryptoObject);
            mIcon.setImageResource(R.drawable.ic_fp_40px);
            Log.d("fui", "successfully started listening for something. ");
        } else {
            Log.d("fui", "initCipher() returned false");
        }
    }

    /**
     * Stops listening to {@link FingerprintManager}
     */
    public void stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true;
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    /**
     * Called by {@link FingerprintManager} if the authentication threw an error.
     */
    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        Log.d("fui", "onAuthenticationError");
        if (!mSelfCancelled) {
            showError(errString);
            mIcon.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCallback.onError();
                }
            }, ERROR_TIMEOUT_MILLIS);
        }
    }

    /**
     * Called by {@link FingerprintManager} if the user asked for help.
     */
    /*@Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        Log.d("fui", "onAuthenticationHelp");
        showError(helpString);
    }*/

    /**
     * Called by {@link FingerprintManager} if the authentication failed (bad finger etc...).
     */
    @Override
    public void onAuthenticationFailed() {
        Log.d("fui", "onAuthenticationFailed");
        showError(mIcon.getResources().getString(
                R.string.pin_code_fingerprint_not_recognized));
    }

    /**
     * Called by {@link FingerprintManager} if the authentication succeeded.
     */
    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
        Log.d("fui", "onAuthenticationSucceeded");
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable);
        mIcon.setImageResource(R.drawable.ic_fingerprint_success);
        mErrorTextView.setTextColor(
                mErrorTextView.getResources().getColor(R.color.success_color, null));
        mErrorTextView.setText(
                mErrorTextView.getResources().getString(R.string.pin_code_fingerprint_success));
        mIcon.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCallback.onAuthenticated();
            }
        }, SUCCESS_DELAY_MILLIS);
    }

    /**
     * Tells if the {@link FingerprintManager#isHardwareDetected()}, {@link FingerprintManager#hasEnrolledFingerprints()},
     * and {@link KeyguardManager#isDeviceSecure()}
     * 
     * @return true if yes, false otherwise
     * @throws SecurityException If the hardware is not available, or the permission are not set
     */
    public boolean isFingerprintAuthAvailable() throws SecurityException {
        return BiometricManager.from(context)
                .canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Initialize the {@link Cipher} instance with the created key in the {@link #createKey()}
     * method.
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    private boolean initCipher() {
        try {
            Log.d("fui", "inside initCipher()");
            if (mKeyStore == null) {
                mKeyStore = KeyStore.getInstance("AndroidKeyStore");
            }
            createKey();
            mKeyStore.load(null);
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
            mCipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            mCipher.init(Cipher.ENCRYPT_MODE, key);
            Log.d("fui", "about to return true");
            return true;
        } catch (NoSuchPaddingException | KeyStoreException | CertificateException | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            Log.d("fui", "caught " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     */
    public void createKey() {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.
        try {
            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            mKeyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            mKeyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            // Require the user to authenticate with a fingerprint to authorize every use
                            // of the key
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            mKeyGenerator.generateKey();
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Show an error on the UI using {@link #mIcon} and {@link #mErrorTextView}
     */
    private void showError(CharSequence error) {
        mIcon.setImageResource(R.drawable.ic_fingerprint_error);
        mErrorTextView.setText(error);
        mErrorTextView.setTextColor(
                mErrorTextView.getResources().getColor(R.color.warning_color, null));
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable);
        mErrorTextView.postDelayed(mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    /**
     * Run by {@link #showError(CharSequence)} with delay to reset the original UI after an error.
     */
    Runnable mResetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            mErrorTextView.setTextColor(
                    mErrorTextView.getResources().getColor(R.color.hint_color, null));
            mErrorTextView.setText(
                    mErrorTextView.getResources().getString(R.string.pin_code_fingerprint_text));
            mIcon.setImageResource(R.drawable.ic_fp_40px);
        }
    };

    /**
     * The interface used to call the original Activity/Fragment... that uses this helper.
     */
    public interface Callback {
        void onAuthenticated();

        void onError();
    }
}
