package com.obby.android.audioplayer;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    private SwitchCompat mFloatingWindowSwitchView;

    private SwitchCompat mLoopSwitchView;

    private SwitchCompat mCloseWhenFinishedSwitchView;

    private SharedPreferences mSharedPreferences;

    private SharedPreferences.Editor mSharedPreferencesEditor;

    private final ActivityResultLauncher<Intent> requestDisplayOverlayPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> updateFloatingWindowChecked()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFloatingWindowSwitchView = findViewById(R.id.floating_window);
        mLoopSwitchView = findViewById(R.id.loop);
        mCloseWhenFinishedSwitchView = findViewById(R.id.close_when_finished);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferencesEditor = mSharedPreferences.edit();

        updateFloatingWindowChecked();
        mLoopSwitchView.setChecked(mSharedPreferences.getBoolean("loop", false));
        mCloseWhenFinishedSwitchView.setChecked(
                mSharedPreferences.getBoolean("close_when_finished", false)
        );

        mFloatingWindowSwitchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !Settings.canDrawOverlays(this)) {
                showDisplayOverlayPermissionRequestDialog();
            } else {
                mSharedPreferencesEditor.putBoolean("floating_window", isChecked).apply();
            }
        });
        mLoopSwitchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mSharedPreferencesEditor.putBoolean("loop", isChecked).apply();
        });
        mCloseWhenFinishedSwitchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mSharedPreferencesEditor.putBoolean("close_when_finished", isChecked).apply();
        });
    }

    private void updateFloatingWindowChecked() {
        if (mSharedPreferences.getBoolean("floating_window", false)) {
            if (Settings.canDrawOverlays(this)) {
                mFloatingWindowSwitchView.setChecked(true);
            } else {
                mFloatingWindowSwitchView.setChecked(false);
                mSharedPreferencesEditor.putBoolean("floating_window", false).apply();
            }
        } else {
            mFloatingWindowSwitchView.setChecked(false);
        }
    }

    private void showDisplayOverlayPermissionRequestDialog() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.display_overlay_permission_request)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.fromParts("package", getPackageName(), null)
                    );
                    requestDisplayOverlayPermissionLauncher.launch(intent);
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
