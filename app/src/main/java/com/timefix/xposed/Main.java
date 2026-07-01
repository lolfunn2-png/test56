package com.timefix.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        // Hook ONLY package: "android"
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedBridge.log("AutoTimeFix: Initializing hooks for package 'android'...");

            // Hook class: com.android.server.am.ActivityManagerService
            // Hook method: systemReady()
            XposedHelpers.findAndHookMethod(
                "com.android.server.am.ActivityManagerService",
                lpparam.classLoader,
                "systemReady",
                new Object[] {
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("AutoTimeFix: systemReady() called. Starting background NTP sync task...");
                            
                            // Start NTP sync task in a background thread to avoid blocking system server (no ANR risk)
                            Thread syncThread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    runNtpSyncProcess();
                                }
                            }, "AutoTimeFix-Thread");
                            syncThread.start();
                        }
                    }
                }
            );

        } catch (Throwable t) {
            XposedBridge.log("AutoTimeFix: Error initializing hooks: " + t.getMessage());
        }
    }

    private void runNtpSyncProcess() {
        try {
            // 1. Wait 30 seconds (boot stabilization)
            XposedBridge.log("AutoTimeFix: Waiting 30 seconds for boot stabilization...");
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            XposedBridge.log("AutoTimeFix: Stabilization sleep interrupted.");
            return;
        } catch (Throwable t) {
            XposedBridge.log("AutoTimeFix: Error in stabilization sleep: " + t.getMessage());
        }

        // 2. Check internet connection using ping 8.8.8.8
        // 3. Retry internet check every 5 seconds (max 30 times)
        boolean internetAvailable = false;
        XposedBridge.log("AutoTimeFix: Checking internet connection...");
        for (int i = 1; i <= 30; i++) {
            try {
                if (checkInternet()) {
                    XposedBridge.log("AutoTimeFix: Internet is available!");
                    internetAvailable = true;
                    break;
                }
                XposedBridge.log("AutoTimeFix: Ping failed. Internet not available yet. Retry " + i + "/30 in 5 seconds...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                XposedBridge.log("AutoTimeFix: Internet check sleep interrupted.");
                return;
            } catch (Throwable t) {
                XposedBridge.log("AutoTimeFix: Error checking internet: " + t.getMessage());
            }
        }

        if (!internetAvailable) {
            XposedBridge.log("AutoTimeFix: Internet was not available after 30 retries. Aborting NTP sync.");
            return;
        }

        // 4. Once internet available:
        //    Run NTP sync command using: Runtime.getRuntime().exec("su -c busybox ntpd -p time.google.com");
        // 5. Retry NTP sync 10 times every 5 seconds
        XposedBridge.log("AutoTimeFix: Starting NTP sync...");
        for (int i = 1; i <= 10; i++) {
            try {
                XposedBridge.log("AutoTimeFix: Running NTP sync command, attempt " + i + "/10...");
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "busybox ntpd -p time.google.com"});
                
                // Wait for the command to finish to see if it returned successfully
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    XposedBridge.log("AutoTimeFix: NTP sync command executed successfully.");
                    break;
                } else {
                    XposedBridge.log("AutoTimeFix: NTP sync command returned exit code: " + exitCode);
                }
                
                // Wait 5 seconds before retrying
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                XposedBridge.log("AutoTimeFix: NTP sync sleep interrupted.");
                return;
            } catch (Throwable t) {
                XposedBridge.log("AutoTimeFix: Error during NTP sync command execution: " + t.getMessage());
            }
        }
    }

    private boolean checkInternet() {
        try {
            // Run "ping -c 1 -W 2 8.8.8.8" (1 ping packet, 2s timeout)
            Process pingProcess = Runtime.getRuntime().exec(new String[]{"ping", "-c", "1", "-W", "2", "8.8.8.8"});
            int exitValue = pingProcess.waitFor();
            return exitValue == 0;
        } catch (Throwable t) {
            XposedBridge.log("AutoTimeFix: Exception during ping: " + t.getMessage());
            return false;
        }
    }
}
