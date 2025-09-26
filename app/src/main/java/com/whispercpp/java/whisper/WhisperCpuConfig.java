package com.whispercpp.java.whisper;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class WhisperCpuConfig {
  private static final String LOG_TAG = "WhisperCpuConfig";
  
  @RequiresApi(api = Build.VERSION_CODES.N)
  public static int getPreferredThreadCount() {
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    int highPerfCount = CpuInfo.getHighPerfCpuCount();
    
    // Use more aggressive threading for better performance
    int threadCount = Math.max(highPerfCount, Math.min(availableProcessors, 4));
    
    Log.d(LOG_TAG, "Available processors: " + availableProcessors);
    Log.d(LOG_TAG, "High performance CPU count: " + highPerfCount);
    Log.d(LOG_TAG, "Selected thread count: " + threadCount);
    
    return threadCount;
  }
}
