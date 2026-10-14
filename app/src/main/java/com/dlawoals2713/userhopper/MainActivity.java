package com.dlawoals2713.userhopper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
            String command = "am switch-user " + GUEST_USER_ID;
            _executeCommand(command, 4);

            binding.textview.setText(getString(R.string.run_guest_start));
        });

        binding.guestExit.setOnClickListener(v -> {
            String command = "am switch-user 0";
            _executeCommand(command, 5);

            binding.textview.setText(getString(R.string.run_guest_exit));
        });

        binding.guestDestroy.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.del_guest_title))
                .setMessage(getString(R.string.del_guest_message))
                .setPositiveButton(getString(R.string.del_guest_positive), (_dialog, _d) -> {
                    String command = "pm remove-user " + GUEST_USER_ID;
                    _executeCommand(command, 4);

                    binding.textview.setText(getString(R.string.run_guest_destroy));
                })
                .setNegativeButton(getString(R.string.del_guest_negative), null)
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

    private void updateStatus(int option) {
        binding.create.setEnabled(false);
        binding.installShizuku.setEnabled(false);
        binding.installManager.setEnabled(false);
        binding.guestStart.setEnabled(false);
        binding.guestExit.setEnabled(false);
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
                } else if (!stdout.isEmpty()) {
                    if (option == 0) {
                        binding.textview.setText(getString(R.string.suc_guest_created));
                        updateStatus(1);
                    } else if (option == 1) {
                        int parsedId = parseGuestUserId(stdout);
                        if (parsedId != -1) {
                            GUEST_USER_ID = parsedId;
                            binding.textview.setText(getString(R.string.suc_guest_detected, String.valueOf(GUEST_USER_ID)));
                            updateStatus(2);
                        } else {
                            binding.textview.setText(getString(R.string.err_guest_not_detected));
                            updateStatus(0);
                        }
                    } else if (option == 2) {
                        binding.textview.setText(getString(R.string.suc_shizuku_installed));
                        updateStatus(3);
                    } else if (option == 3) {
                        binding.textview.setText(getString(R.string.suc_manager_installed));
                        updateStatus(4);
                    } else if (option == 4) {
                        binding.textview.setText(getString(R.string.suc_guest_destroyed));
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
        getCurrentUserId(userId -> binding.guestDestroy.setEnabled(userId != -1 && userId != GUEST_USER_ID));
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