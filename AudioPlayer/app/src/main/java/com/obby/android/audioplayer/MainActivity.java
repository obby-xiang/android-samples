package com.obby.android.audioplayer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    private SwitchCompat mFloatingWindowSwitchView;

    private SharedPreferences mSharedPreferences;

    private SharedPreferences.Editor mSharedPreferencesEditor;

    private final ActivityResultLauncher<String> mReadExternalStoragePermissionRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
                if (Boolean.TRUE.equals(result)) {
                    startAudioPlayerService();
                    finish();
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE
                )) {
                    finish();
                } else {
                    showReadExternalStoragePermissionSettingsDialog();
                }
            });

    private final ActivityResultLauncher<Intent> mReadExternalStoragePermissionSettingsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), result -> {
                        if (ContextCompat.checkSelfPermission(
                                this, Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED) {
                            startAudioPlayerService();
                        }
                        finish();
                    }
            );

    private final ActivityResultLauncher<Intent> mDisplayOverlayPermissionRequestLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> updateFloatingWindowChecked()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            final String type = intent.getType();
            if (type != null && (type.startsWith("audio/") || "application/ogg".equals(type))) {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED) {
                    startAudioPlayerService();
                    finish();
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE
                )) {
                    showReadExternalStoragePermissionRequestDialog();
                } else {
                    mReadExternalStoragePermissionRequestLauncher.launch(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    );
                }
                return;
            }
        }

        setContentView(R.layout.activity_main);

        mFloatingWindowSwitchView = findViewById(R.id.floating_window);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferencesEditor = mSharedPreferences.edit();

        mFloatingWindowSwitchView.setChecked(
                mSharedPreferences.getBoolean("floating_window", false)
        );
        updateFloatingWindowChecked();

        findViewById(R.id.close).setOnClickListener(v -> finish());
        mFloatingWindowSwitchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !Settings.canDrawOverlays(this)) {
                showDisplayOverlayPermissionRequestDialog();
            } else {
                mSharedPreferencesEditor.putBoolean("floating_window", isChecked).apply();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFloatingWindowChecked();
    }

    private void updateFloatingWindowChecked() {
        if (mSharedPreferences == null || mFloatingWindowSwitchView == null) {
            return;
        }

        final boolean isFloatingWindowEnabled = mFloatingWindowSwitchView.isChecked()
                && Settings.canDrawOverlays(this);

        if (mFloatingWindowSwitchView.isChecked() != isFloatingWindowEnabled) {
            mFloatingWindowSwitchView.setChecked(isFloatingWindowEnabled);
        }

        if (mSharedPreferences.getBoolean("floating_window", false) != isFloatingWindowEnabled) {
            mSharedPreferencesEditor.putBoolean("floating_window", isFloatingWindowEnabled).apply();
        }
    }

    private void startAudioPlayerService() {
        final Intent intent = new Intent(this, AudioPlayerService.class)
                .setData(getIntent().getData());
        ContextCompat.startForegroundService(this, intent);
    }

    private void showReadExternalStoragePermissionRequestDialog() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.read_external_storage_permission_request)
                .setPositiveButton(
                        R.string.ok,
                        (dialog, which) -> mReadExternalStoragePermissionRequestLauncher.launch(
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                )
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setTextColor(getColor(R.color.red_500));
    }

    private void showReadExternalStoragePermissionSettingsDialog() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.read_external_storage_permission_settings)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null)
                    );
                    mReadExternalStoragePermissionSettingsLauncher.launch(intent);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setTextColor(getColor(R.color.red_500));
    }

    private void showDisplayOverlayPermissionRequestDialog() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.display_overlay_permission_request)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.fromParts("package", getPackageName(), null)
                    );
                    mDisplayOverlayPermissionRequestLauncher.launch(intent);
                })
                .setNegativeButton(
                        R.string.cancel, (dialog, which) -> updateFloatingWindowChecked()
                )
                .setCancelable(false)
                .show();
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setTextColor(getColor(R.color.red_500));
    }
}
