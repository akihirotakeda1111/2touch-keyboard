// Copyright 2010-2018, Google Inc.
// Adapted for 2touch-keyboard PoC (upstream mozcjni.cc API).

package com.google.android.apps.inputmethod.libs.mozc.session;

/**
 * JNI wrapper for Mozc session handler.
 *
 * <p>Native method signatures must match {@code src/android/jni/mozcjni.cc}.
 */
public final class MozcJNI {

    private static volatile boolean isLoaded = false;

    private MozcJNI() {
    }

    /**
     * Loads libmozc.so and initializes the engine with dictionary data.
     *
     * @param userProfileDirectoryPath writable directory for Mozc user profile
     * @param dataFilePath absolute path to mozc.data, or empty string for minimal engine
     */
    public static synchronized void load(String userProfileDirectoryPath, String dataFilePath) {
        if (userProfileDirectoryPath == null || userProfileDirectoryPath.isEmpty()) {
            throw new IllegalArgumentException("userProfileDirectoryPath is required");
        }

        if (!isLoaded) {
            System.loadLibrary("mozc");
            if (!initialize()) {
                throw new RuntimeException("MozcJNI.initialize failed");
            }
            isLoaded = true;
        }

        String dataPath = dataFilePath != null ? dataFilePath : "";
        if (!onPostLoad(userProfileDirectoryPath, dataPath)) {
            throw new RuntimeException("MozcJNI.onPostLoad failed");
        }
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static synchronized native byte[] evalCommand(byte[] command);

    private static native boolean initialize();

    private static synchronized native boolean onPostLoad(
            String userProfileDirectoryPath,
            String dataFilePath);

    public static native String getDataVersion();
}
