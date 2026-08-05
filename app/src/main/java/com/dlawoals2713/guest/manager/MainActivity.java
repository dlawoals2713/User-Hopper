package com.dlawoals2713.guest.manager;

import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.dlawoals2713.guest.manager.databinding.ActivityMainBinding;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
    }

    private void initialize() {
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo("moe.shizuku.privileged.api", PackageManager.GET_ACTIVITIES);
            if (packageInfo != null) {
                if (Shizuku.pingBinder()) {
                    Shizuku.requestPermission(69);
                } else {
                    Toast.makeText(this, "Shizuku 서비스가 시작되지 않았습니다!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Shizuku가 설치되어 있지 않습니다. 실행할 수 없습니다.", Toast.LENGTH_LONG).show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "Shizuku가 설치되어 있지 않습니다. 실행할 수 없습니다.", Toast.LENGTH_LONG).show();
        }

        binding.create.setOnClickListener(v -> {
            String command = "pm create-user --guest Guest";
            _executeCommand(command, 0);
        });

        binding.installShizuku.setOnClickListener(v -> {
            String command = "pm install-existing --user " + GUEST_USER_ID + " moe.shizuku.privileged.api";
            _executeCommand(command, 2);
        });

        binding.installManager.setOnClickListener(v -> {
            String command = "pm install-existing --user " + GUEST_USER_ID + " " + getPackageName();
            _executeCommand(command, 3);
        });

        binding.guestStart.setOnClickListener(v -> {
            String command = "am switch-user " + GUEST_USER_ID;
            _executeCommand(command, 4);

            binding.textview.setText("게스트를 시작하는 중\n절대 세션을 다시 시작 하지 마세요!");
        });

        binding.guestExit.setOnClickListener(v -> {
            String command = "am switch-user 0";
            _executeCommand(command, 5);

            binding.textview.setText("게스트를 종료하는 중");
        });

        binding.guestDestroy.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("주의")
                .setMessage("게스트 사용자를 제거하면 게스트 사용자 내에 저장된 모든 데이터가 영구적으로 삭제됩니다!")
                .setPositiveButton("삭제", (_dialog, _d) -> {
                    String command = "pm remove-user " + GUEST_USER_ID;
                    _executeCommand(command, 4);

                    binding.textview.setText("게스트를 삭제하는 중");
                })
                .setNegativeButton("취소", null)
                .show());
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
            checkPackageInstalledForUser("moe.shizuku.privileged.api", isInstalled -> {
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
            if (getCurrentUserId() == GUEST_USER_ID) {
                binding.guestExit.setEnabled(true);
            } else {
                binding.guestStart.setEnabled(true);
            }
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = (requestCode, grantResult) -> {
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            Toast.makeText(this, "권한 허용됨", Toast.LENGTH_SHORT).show();
            updateStatus(1);
        } else {
            Toast.makeText(this, "권한이 거부되었어요", Toast.LENGTH_SHORT).show();
        }
    };

    private void _executeCommand(final String _cmd, int option) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("실행하는 중...");
        progressDialog.setCancelable(false);
        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.show();
        }

        new Thread(() -> {
            ShizukuShell sh = new ShizukuShell(_cmd, (stdout, stderr) -> runOnUiThread(() -> {
                progressDialog.dismiss();

                if (!stderr.isEmpty()) {
                    Toast.makeText(this, "오류 발생:\n" + stderr, Toast.LENGTH_LONG).show();
                    saveLogToFile(_cmd, stderr);
                } else if (!stdout.isEmpty()) {
                    if (option == 0) {
                        binding.textview.setText("게스트 프로필 생성 완료");
                        updateStatus(1);
                    } else if (option == 1) {
                        int parsedId = parseGuestUserId(stdout);
                        if (parsedId != -1) {
                            GUEST_USER_ID = parsedId;
                            binding.textview.setText( "게스트 ID 감지됨: " + GUEST_USER_ID);
                            updateStatus(2);
                        } else {
                            binding.textview.setText("게스트 유저를 찾을 수 없습니다.");
                            updateStatus(0);
                        }
                    } else if (option == 2) {
                        binding.textview.setText("Shizuku 설치 완료");
                        updateStatus(3);
                    } else if (option == 3) {
                        binding.textview.setText("매니저 설치 완료");
                        updateStatus(4);
                    } else if (option == 4) {
                        binding.textview.setText("게스트 삭제 완료");
                        recreate();
                    } else {
                        Toast.makeText(this, "성공:\n" + stdout, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "출력이 없습니다.", Toast.LENGTH_SHORT).show();
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
            ShizukuShell sh = new ShizukuShell(cmd, (stdout, stderr) -> runOnUiThread(() -> {
                boolean isInstalled = stdout.contains("package:" + packageName);
                if (listener != null) {
                    listener.onResult(isInstalled);
                }
            }));
            sh.exec();
        }).start();
    }

    private int getCurrentUserId() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();

        Pattern pattern = Pattern.compile("/storage/emulated/(\\d+)");
        Matcher matcher = pattern.matcher(path);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 0;
    }

    private void enableDestroyButton() {
        binding.guestDestroy.setEnabled(getCurrentUserId() != GUEST_USER_ID);
    }

    private void saveLogToFile(String command, String output) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "Documents/manager");
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