/*

 * Copyright (C) 2026 dlawoals2713
 *
 * This file is part of User-Hopper.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */

package com.dlawoals2713.userhopper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.dlawoals2713.userhopper.databinding.ActivityAltermainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class AlterMainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SHIZUKU = 69;
    private static final long POLL_INTERVAL_MS = 3000;
    private static final int MAX_INSTALL_RETRY = 10;
    private static final int OWNER_USER_ID = 0;
    private static final String LEGACY_PACKAGE = "com.samsung.android.digitallegacy";

    private final String shizukuPackage = "moe.shizuku.privileged.api";

    private ActivityAltermainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::refresh;

    private int guestUserId = -1;
    private boolean isLegacyUser;
    private boolean waitingForCreation = false;
    private boolean busy = false;
    private int installRetry = 0;

    private static final long CREATE_WAIT_TIMEOUT_MS = 10 * 60 * 1000;
    private long createRequestedAt = 0;
    private boolean guestSessionRunning = false;

    private boolean isWaitingForCreation() {
        return waitingForCreation && System.currentTimeMillis() - createRequestedAt < CREATE_WAIT_TIMEOUT_MS;
    }

    private interface CommandCallback {
        void onResult(String stdout, String stderr);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAltermainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UserManager um = getSystemService(UserManager.class);
        isLegacyUser = um != null && !um.isSystemUser();

        initialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasShizukuPermission()) {
            refresh();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        Shizuku.removeBinderReceivedListener(BINDER_RECEIVED_LISTENER);
        super.onDestroy();
    }

    private void initialize() {
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            try {
                int version = Build.VERSION.class.getDeclaredField("SEM_PLATFORM_INT").getInt(null) - 90000;

                if (version >= 70000 && version <= 80000) {
                    binding.change.setVisibility(View.VISIBLE);
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }

        setButtons(false, false, false, false);

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        try {
            getPackageManager().getPackageInfo(shizukuPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_shizuku_not_installed), Toast.LENGTH_LONG).show();
            setupButtons();
            return;
        }

        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, getString(R.string.err_shizuku_not_started), Toast.LENGTH_SHORT).show();
        }

        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED_LISTENER);

        setupButtons();
    }

    private void change() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private final Shizuku.OnBinderReceivedListener BINDER_RECEIVED_LISTENER = () -> runOnUiThread(() -> {
        if (hasShizukuPermission()) {
            refresh();
        } else if (!Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU);
        }
    });

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = (requestCode, grantResult) -> runOnUiThread(() -> {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            binding.textview.setText(R.string.per_granted);
            refresh();
        } else {
            Toast.makeText(this, getString(R.string.per_denied), Toast.LENGTH_SHORT).show();
        }
    });

    private boolean hasShizukuPermission() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void setupButtons() {
        binding.change.setOnClickListener(v -> change());
        binding.refresh.setOnClickListener(v -> recreate());

        binding.create.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + LEGACY_PACKAGE));
            startActivity(intent);

            Toast.makeText(this, "상단의 '디지털 유산 설정'을 진입 후, '디지털 유산 요청'을 누르세요.", Toast.LENGTH_LONG).show();

            waitingForCreation = true;
            createRequestedAt = System.currentTimeMillis();
            installRetry = 0;
            handler.removeCallbacks(pollRunnable);
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        });

        binding.guestStart.setOnClickListener(v -> {
            setButtons(false, false, false, false);  // 중복 클릭 방지
            binding.textview.setText(getString(R.string.pre_legacy));

            disableLegacy(guestUserId, true, () -> ensureInstalled(shizukuPackage, () -> ensureInstalled(getPackageName(), this::switchToGuest, this::abortStart), this::abortStart));
        });

        binding.guestExit.setOnClickListener(v -> exec("am get-current-user", true, (out, err) -> {
            int currentId = -1;
            try {
                currentId = Integer.parseInt(out.trim());
            } catch (NumberFormatException ignored) {}

            if (!err.isEmpty() || currentId == -1) {
                handleError("am get-current-user", err.isEmpty() ? out : err);
                return;
            }

            enableLegacy(currentId, () -> {
                binding.textview.setText(getString(R.string.run_exit));

                String command = "am switch-user " + OWNER_USER_ID;
                exec(command, true, (stdout, stderr) -> {
                    if (!stderr.isEmpty()) {
                        handleError(command, stderr);
                        return;
                    }
                    finish();
                });
            });
        }));

        binding.guestEnd.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ui_legacy_end))
                .setMessage(getString(R.string.ui_terminate_message, getString(R.string.app_name_digitallegacy)))
                .setPositiveButton(getString(R.string.ui_terminate_ok), (d, w) -> {
                    String command = "am stop-user -w -f " + guestUserId;
                    exec(command, true, (stdout, stderr) -> {
                        if (!stderr.isEmpty()) {
                            handleError(command, stderr);
                            return;
                        }
                        binding.textview.setText(getString(R.string.suc_terminated, getString(R.string.app_name_digitallegacy)));
                        refresh();
                    });
                })
                .setNegativeButton(getString(R.string.ui_terminate_no), null)
                .show());

        binding.guestDestroy.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.del_title))
                .setMessage(getString(R.string.del_message, getString(R.string.app_name_digitallegacy)))
                .setPositiveButton(getString(R.string.del_ok), (d, w) -> {
                    binding.textview.setText(getString(R.string.run_destroy, getString(R.string.app_name_digitallegacy)));

                    String command = "pm remove-user " + guestUserId;
                    exec(command, true, (stdout, stderr) -> {
                        if (!stderr.isEmpty()) {
                            handleError(command, stderr);
                            return;
                        }
                        guestUserId = -1;
                        binding.textview.setText(getString(R.string.suc_destroyed, getString(R.string.app_name_digitallegacy)));
                        refresh();
                    });
                })
                .setNegativeButton(getString(R.string.del_no), null)
                .show());
    }

    private void refresh() {
        handler.removeCallbacks(pollRunnable);
        if (busy || isFinishing() || isDestroyed()) return;

        if (!hasShizukuPermission()) {
            setButtons(false, false, false, false);
            return;
        }


        detectGuestUser(isLegacyUser);
    }

    private void detectGuestUser(boolean isLegacyUser) {
        busy = true;
        exec("pm list users", false, (stdout, stderr) -> {
            int id = stderr.isEmpty() ? parseGuestUserId(stdout) : -1;

            boolean creating = isWaitingForCreation();
            if (!creating) waitingForCreation = false;

            if (id == -1) {
                guestUserId = -1;
                guestSessionRunning = false;
                busy = false;
                binding.textview.setText(getString(R.string.err_not_detected, getString(R.string.app_name_digitallegacy)));
                setButtons(true, false, false, false);
                if (creating) {
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                }
                return;
            }

            guestUserId = id;
            guestSessionRunning = isUserRunning(stdout, id);
            binding.textview.setText(getString(R.string.suc_detected, getString(R.string.app_name_digitallegacy), String.valueOf(id)));

            if (isLegacyUser) {
                setButtons(false, false, true, false);
            } else if (creating) {
                setButtons(false, false, false, false);
                disableLegacy(id, false, () -> ensureInstalledWithRetry(shizukuPackage, () -> ensureInstalledWithRetry(getPackageName(), this::onGuestReady)));
            } else {
                busy = false;
                setButtons(false, true, false, true);
            }
        });
    }

    private boolean isUserRunning(String stdout, int userId) {
        String prefix = "UserInfo{" + userId + ":";
        for (String line : stdout.split("\n")) {
            if (!line.contains(prefix)) continue;
            int end = line.lastIndexOf('}');
            return end >= 0 && line.substring(end + 1).contains("running");
        }
        return false;
    }

    private void disableLegacy(int userId, boolean showProgress, Runnable next) {
        String command = "pm disable-user --user " + userId + " " + LEGACY_PACKAGE;
        exec(command, showProgress, (stdout, stderr) -> {
            if (!stderr.isEmpty()) {
                saveLogToFile(command, stderr);
            }
            next.run();
        });
    }

    private void enableLegacy(int userId, Runnable next) {
        String command = "pm enable --user " + userId + " " + LEGACY_PACKAGE;
        exec(command, true, (stdout, stderr) -> {
            if (!stderr.isEmpty()) {
                saveLogToFile(command, stderr);
            }
            next.run();
        });
    }

    private interface FailCallback {
        void onFail(String command, String error);
    }

    private void ensureInstalled(String packageName, Runnable onDone, FailCallback onFail) {
        exec("pm list packages --user " + guestUserId + " " + packageName, false, (stdout, stderr) -> {
            if (stderr.isEmpty() && containsPackage(stdout, packageName)) {
                onDone.run();
                return;
            }

            String installCmd = "pm install-existing --user " + guestUserId + " " + packageName;
            exec(installCmd, false, (out, err) -> {
                boolean failed = !err.isEmpty() || out.contains("Failure") || out.contains("Error");
                if (!failed) {
                    binding.textview.setText(getString(packageName.equals(shizukuPackage) ? R.string.suc_shizuku_installed : R.string.suc_manager_installed));
                    onDone.run();
                } else {
                    onFail.onFail(installCmd, err.isEmpty() ? out : err);
                }
            });
        });
    }

    private void ensureInstalledWithRetry(String packageName, Runnable onDone) {
        ensureInstalled(packageName, onDone, (cmd, error) -> {
            busy = false;
            if (++installRetry <= MAX_INSTALL_RETRY) {
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            } else {
                waitingForCreation = false;
                handleError(cmd, error);
            }
        });
    }

    private void onGuestReady() {
        busy = false;
        installRetry = 0;
        waitingForCreation = false;
        setButtons(false, true, false, true);
    }

    private void setButtons(boolean create, boolean start, boolean exit, boolean destroy) {
        binding.create.setEnabled(create);
        binding.guestStart.setEnabled(start);
        binding.guestExit.setEnabled(exit);
        binding.guestEnd.setEnabled(destroy && guestSessionRunning);
        binding.guestDestroy.setEnabled(destroy);
    }

    private void exec(final String cmd, boolean showProgress, CommandCallback callback) {
        if (isFinishing() || isDestroyed()) return;

        final ProgressDialog progressDialog;
        if (showProgress) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(getString(R.string.run_cmd_running));
            progressDialog.setCancelable(false);
            progressDialog.show();
        } else {
            progressDialog = null;
        }

        new Thread(() -> {
            ShizukuShell sh = new ShizukuShell(this, cmd, (stdout, stderr) -> runOnUiThread(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (isFinishing() || isDestroyed()) return;
                callback.onResult(stdout == null ? "" : stdout, stderr == null ? "" : stderr);
            }));
            sh.exec();
        }).start();
    }

    private void handleError(String command, String stderr) {
        busy = false;
        Toast.makeText(this, getString(R.string.err_accoured, stderr), Toast.LENGTH_LONG).show();
        saveLogToFile(command, stderr);
    }

    private int parseGuestUserId(String stdout) {
        Pattern pattern = Pattern.compile("UserInfo\\{(\\d+):([^:]+):");
        Matcher matcher = pattern.matcher(stdout);
        String target = getString(R.string.app_name_digitallegacy).trim();

        while (matcher.find()) {
            String userName = matcher.group(2).trim();
            if (target.equalsIgnoreCase(userName)) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return -1;
    }

    private boolean containsPackage(String stdout, String packageName) {
        for (String line : stdout.split("\n")) {
            if (line.trim().equals("package:" + packageName)) return true;
        }
        return false;
    }

    private void switchToGuest() {
        String command = "am switch-user " + guestUserId;
        exec(command, true, (stdout, stderr) -> {
            if (!stderr.isEmpty()) {
                handleError(command, stderr);
            } else {
                guestSessionRunning = true;
                binding.textview.setText(getString(R.string.run_legacy_start, getString(R.string.app_name_digitallegacy)));
            }
            setButtons(false, true, false, true);
        });
    }

    private void abortStart(String command, String error) {
        handleError(command, error);
        binding.textview.setText("Shizuku/Manager 설치에 실패했습니다.");
        enableLegacy(guestUserId, () -> setButtons(false, true, false, true));
    }

    private void saveLogToFile(String command, String output) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "Documents/UserHopper");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(new File(dir, "command_log.txt"), true)) {
                writer.write(">>manager$ " + command + "\n");
                writer.write(output + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}