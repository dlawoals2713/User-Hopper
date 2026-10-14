package com.dlawoals2713.userhopper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.dlawoals2713.userhopper.databinding.ActivityMainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private int GUEST_USER_ID = -1;
    private final String shizukuPackage = "moe.shizuku.privileged.api";
    private boolean guestSessionRunning = false;
    private boolean resumedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
    }

    private void initialize() {
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            try {
                int version = Build.VERSION.class.getDeclaredField("SEM_PLATFORM_INT").getInt(null) - 90000;

                if (version >= 80500) {
                    change();
                    return;
                } else if (version >= 70000 && version <= 80000) {
                    binding.change.setVisibility(View.VISIBLE);
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(shizukuPackage, PackageManager.GET_ACTIVITIES);
            if (packageInfo != null) {
                if (Shizuku.pingBinder()) {
                    Shizuku.requestPermission(69);
                } else {
                    Toast.makeText(this, getString(R.string.err_shizuku_not_started), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.err_shizuku_not_installed), Toast.LENGTH_LONG).show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_shizuku_not_installed), Toast.LENGTH_LONG).show();
        }

        binding.change.setOnClickListener(v -> change());
        binding.refresh.setOnClickListener(v -> recreate());

        binding.create.setOnClickListener(v -> {
            String command = "pm create-user --guest Guest";
            _executeCommand(command, 0);
        });

        binding.installShizuku.setOnClickListener(v -> {
            String command = "pm install-existing --user " + GUEST_USER_ID + " " + shizukuPackage;
            _executeCommand(command, 2);
        });

        binding.installManager.setOnClickListener(v -> {
            String command = "pm install-existing --user " + GUEST_USER_ID + " " + getPackageName();
            _executeCommand(command, 3);
        });

        binding.guestStart.setOnClickListener(v -> {
            binding.textview.setText(getString(R.string.run_guest_start, getString(R.string.app_name_guest)));

            String command = "am switch-user " + GUEST_USER_ID;
            _executeCommand(command, 4);
        });

        binding.guestExit.setOnClickListener(v -> {
            binding.textview.setText(getString(R.string.run_exit));

            String command = "am switch-user 0";
            _executeCommand(command, 5);

            finish();
        });

        binding.guestEnd.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ui_guest_end))
                .setMessage(getString(R.string.ui_terminate_message, getString(R.string.app_name_guest)))
                .setPositiveButton(getString(R.string.ui_terminate_ok), (d, w) -> _executeCommand("am stop-user -w -f " + GUEST_USER_ID, 6))
                .setNegativeButton(getString(R.string.ui_terminate_no), null)
                .show());

        binding.guestDestroy.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.del_title))
                .setMessage(getString(R.string.del_message, getString(R.string.app_name_guest)))
                .setPositiveButton(getString(R.string.del_ok), (_dialog, _d) -> {
                    binding.textview.setText(getString(R.string.run_destroy, getString(R.string.app_name_guest)));

                    String command = "pm remove-user " + GUEST_USER_ID;
                    _executeCommand(command, 4);
                })
                .setNegativeButton(getString(R.string.del_no), null)
                .show());
    }

    private void change() {
        startActivity(new Intent(this, AlterMainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        if (resumedOnce && hasShizukuPermission()) {
            updateStatus(1);
        }
        resumedOnce = true;
    }

    private boolean hasShizukuPermission() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void updateStatus(int option) {
        binding.create.setEnabled(false);
        binding.installShizuku.setEnabled(false);
        binding.installManager.setEnabled(false);
        binding.guestStart.setEnabled(false);
        binding.guestExit.setEnabled(false);
        binding.guestEnd.setEnabled(false);
        binding.guestDestroy.setEnabled(false);

        changeStatus(option);
        if (option == 1) {
            _executeCommand("pm list users", 1);
        }
    }

    private void changeStatus(int option) {
        if (option == 0) {
            binding.create.setEnabled(true);
        } else if (option == 1) {
            enableDestroyButton();
        } else if (option == 2) {
            enableDestroyButton();
            checkPackageInstalledForUser(shizukuPackage, isInstalled -> {
                if (isInstalled) {
                    changeStatus(3);
                } else {
                    binding.installShizuku.setEnabled(true);
                }
            });
        } else if (option == 3) {
            enableDestroyButton();
            checkPackageInstalledForUser(getPackageName(), isInstalled -> {
                if (isInstalled) {
                    changeStatus(4);
                } else {
                    binding.installManager.setEnabled(true);
                }
            });
        } else if (option == 4) {
            enableDestroyButton();
            getCurrentUserId(userId -> {
                if (userId == -1) {
                    return;
                }

                if (userId == GUEST_USER_ID) {
                    binding.guestExit.setEnabled(true);
                    binding.change.setVisibility(View.GONE);
                } else {
                    binding.guestStart.setEnabled(true);
                }
            });
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = (requestCode, grantResult) -> {
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            binding.textview.setText(R.string.per_granted);
            updateStatus(1);
        } else {
            Toast.makeText(this, getString(R.string.per_denied), Toast.LENGTH_SHORT).show();
        }
    };

    private void _executeCommand(final String _cmd, int option) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.run_cmd_running));
        progressDialog.setCancelable(false);
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }

        new Thread(() -> {
            ShizukuShell sh = new ShizukuShell(this, _cmd, (stdout, stderr) -> runOnUiThread(() -> {
                progressDialog.dismiss();

                if (!stderr.isEmpty()) {
                    Toast.makeText(this, getString(R.string.err_accoured, stderr), Toast.LENGTH_LONG).show();
                    saveLogToFile(_cmd, stderr);
                } else if (option == 6) {
                    binding.textview.setText(getString(R.string.suc_terminated, getString(R.string.app_name_guest)));
                    updateStatus(1);
                } else if (!stdout.isEmpty()) {
                    if (option == 0) {
                        binding.textview.setText(getString(R.string.suc_guest_created));
                        updateStatus(1);
                    } else if (option == 1) {
                        int parsedId = parseGuestUserId(stdout);
                        if (parsedId != -1) {
                            GUEST_USER_ID = parsedId;
                            guestSessionRunning = isUserRunning(stdout, parsedId);
                            binding.textview.setText(getString(R.string.suc_detected, getString(R.string.app_name_guest), String.valueOf(GUEST_USER_ID)));
                            updateStatus(2);
                        } else {
                            parsedId = parseLegacyUserId(stdout);
                            if (parsedId != -1) {
                                change();
                            } else {
                                guestSessionRunning = false;
                                binding.textview.setText(getString(R.string.err_not_detected, getString(R.string.app_name_guest)));
                                updateStatus(0);
                            }
                        }
                    } else if (option == 2) {
                        binding.textview.setText(getString(R.string.suc_shizuku_installed));
                        updateStatus(3);
                    } else if (option == 3) {
                        binding.textview.setText(getString(R.string.suc_manager_installed));
                        updateStatus(4);
                    } else if (option == 4) {
                        binding.textview.setText(getString(R.string.suc_destroyed, getString(R.string.app_name_guest)));
                        recreate();
                    } else {
                        Toast.makeText(this, getString(R.string.suc_responded, stdout), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.err_cmd_not_response), Toast.LENGTH_SHORT).show();
                }
            }));
            sh.exec();
        }).start();
    }

    private int parseGuestUserId(String stdout) {
        Pattern pattern = Pattern.compile("UserInfo\\{(\\d+):([^:]+):");
        Matcher matcher = pattern.matcher(stdout);

        while (matcher.find()) {
            String userId = matcher.group(1);
            String userName = matcher.group(2);

            if ("Guest".equalsIgnoreCase(userName)) {
                try {
                    return Integer.parseInt(userId);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return -1;
    }

    private int parseLegacyUserId(String stdout) {
        Pattern pattern = Pattern.compile("UserInfo\\{(\\d+):([^:]+):");
        Matcher matcher = pattern.matcher(stdout);

        while (matcher.find()) {
            String userId = matcher.group(1);
            String userName = matcher.group(2);

            if (getString(R.string.app_name_digitallegacy).equalsIgnoreCase(userName)) {
                try {
                    return Integer.parseInt(userId);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return -1;
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

    private interface OnPackageCheckListener {
        void onResult(boolean isInstalled);
    }

    private void checkPackageInstalledForUser(String packageName, OnPackageCheckListener listener) {
        String cmd = "pm list packages --user " + GUEST_USER_ID + " " + packageName;

        new Thread(() -> {
            ShizukuShell sh = new ShizukuShell(getApplicationContext(), cmd, (stdout, stderr) -> runOnUiThread(() -> {
                boolean isInstalled = stdout.contains("package:" + packageName);
                if (listener != null) {
                    listener.onResult(isInstalled);
                }
            }));
            sh.exec();
        }).start();
    }

    private void getCurrentUserId(OnUserIdResultListener listener) {
        ShizukuShell sh = new ShizukuShell(this, "am get-current-user", (stdout, stderr) -> runOnUiThread(() -> {
            if (!stderr.isEmpty()) {
                listener.onResult(-1);
                return;
            }

            try {
                listener.onResult(Integer.parseInt(stdout.trim()));
            } catch (NumberFormatException e) {
                listener.onResult(-1);
            }
        }));

        new Thread(sh::exec).start();
    }

    private interface OnUserIdResultListener {
        void onResult(int userId);
    }

    private void enableDestroyButton() {
        getCurrentUserId(userId -> {
            boolean inOwnerUser = userId != -1 && userId != GUEST_USER_ID;
            binding.guestDestroy.setEnabled(inOwnerUser);
            binding.guestEnd.setEnabled(inOwnerUser && guestSessionRunning);
        });
    }

    private void saveLogToFile(String command, String output) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "Documents/UserHopper");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File logFile = new File(dir, "command_log.txt");
            FileWriter writer = new FileWriter(logFile, true); // append 모드

            writer.write(">>saio$ " + command + "\n");
            writer.write(output + "\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}