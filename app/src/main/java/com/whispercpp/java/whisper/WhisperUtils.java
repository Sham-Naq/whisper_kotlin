package com.whispercpp.java.whisper;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class WhisperUtils {
  private static final String LOG_TAG = "LibWhisper";


  public static boolean isArmEabiV7a() {
    return Build.SUPPORTED_ABIS[0].equals("armeabi-v7a");
  }

  public static boolean isArmEabiV8a() {
    return Build.SUPPORTED_ABIS[0].equals("arm64-v8a");
  }

  public static String cpuInfo() {
    try {
      // Use classic IO for broad API compatibility
      File f = new File("/proc/cpuinfo");
      StringBuilder sb = new StringBuilder();
      try (BufferedReader br = new BufferedReader(new FileReader(f))) {
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line).append('\n');
        }
      }
      return sb.toString();
    } catch (Exception e) {
      Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e);
      return null;
    }

  }
}
