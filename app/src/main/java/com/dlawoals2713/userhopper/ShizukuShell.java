package com.dlawoals2713.userhopper;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuShell {
    private String mStdOut = "";
    private String mStdErr = "";
    private String mCommand;
    private String mDir = "/";
    private final File logDir;
    private ShizukuRemoteProcess mProcess = null;
    private OnProcessCompleteListener mListener;

    public ShizukuShell(Context context, String command, OnProcessCompleteListener listener) {
        mCommand = command;
        mListener = listener;

        logDir = new File(context.getExternalFilesDir(null), "UserHopper");

        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    public void exec() {
        try {
            mProcess = Shizuku.newProcess(new String[]{"sh", "-c", mCommand}, null, mDir);
            BufferedReader mInput = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
            BufferedReader mError = new BufferedReader(new InputStreamReader(mProcess.getErrorStream()));
            StringBuilder outBuilder = new StringBuilder();
            StringBuilder errBuilder = new StringBuilder();
            String line;

            // 표준 출력 읽기
            while ((line = mInput.readLine()) != null) {
                outBuilder.append(line).append("\n");
            }

            // 표준 에러 읽기
            while ((line = mError.readLine()) != null) {
                errBuilder.append(line).append("\n");
            }

            mStdOut = outBuilder.toString();
            mStdErr = errBuilder.toString();

            // 로그 저장
            saveLogToFile(mCommand, mStdOut + mStdErr);

            mProcess.waitFor();

            if (mListener != null) {
                mListener.onProcessComplete(mStdOut, mStdErr);
            }
        } catch (Exception e) {
            if (mListener != null) {
                mListener.onProcessComplete("", e.toString());
            }
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    public void destroy() {
        if (mProcess != null) {
            mProcess.destroy();
        }
    }

    public interface OnProcessCompleteListener {
        void onProcessComplete(String stdout, String stderr);
    }

    private void saveLogToFile(String command, String output) {
        try {
            File logFile = new File(logDir, "command_log.txt");

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(">>manager$ " + command + "\n");
                writer.write(output + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}