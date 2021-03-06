// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base.library_loader;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.JNINamespace;
import org.chromium.base.TraceEvent;

/**
 * This class provides functionality to load and register the native libraries.
 * Callers are allowed to separate loading the libraries from initializing them.
 * This may be an advantage for Android Webview, where the libraries can be loaded
 * by the zygote process, but then needs per process initialization after the
 * application processes are forked from the zygote process.
 *
 * The libraries may be loaded and initialized from any thread. Synchronization
 * primitives are used to ensure that overlapping requests from different
 * threads are handled sequentially.
 *
 * See also base/android/library_loader/library_loader_hooks.cc, which contains
 * the native counterpart to this class.
 */
@JNINamespace("base::android")
public class LibraryLoader {
    private static final String TAG = "LibraryLoader";

    // Guards all access to the libraries
    private static final Object sLock = new Object();

    // One-way switch becomes true when the libraries are loaded.
    private static boolean sLoaded = false;

    // One-way switch becomes true when the Java command line is switched to
    // native.
    private static boolean sCommandLineSwitched = false;

    // One-way switch becomes true when the libraries are initialized (
    // by calling nativeLibraryLoaded, which forwards to LibraryLoaded(...) in
    // library_loader_hooks.cc).
    private static boolean sInitialized = false;

    // One-way switches recording attempts to use Relro sharing in the browser.
    // The flags are used to report UMA stats later.
    private static boolean sIsUsingBrowserSharedRelros = false;
    private static boolean sLoadAtFixedAddressFailed = false;

    // One-way switch recording whether the device supports memory mapping
    // APK files with executable permissions. Only used in the browser.
    private static boolean sLibraryLoadFromApkSupported = false;

    // One-way switch becomes true if the system library loading failed,
    // and the right native library was found and loaded by the hack.
    // The flag is used to report UMA stats later.
    private static boolean sNativeLibraryHackWasUsed = false;

    /**
     * The same as ensureInitialized(null, false), should only be called
     * by non-browser processes.
     *
     * @throws ProcessInitException
     */
    public static void ensureInitialized() throws ProcessInitException {
        ensureInitialized(null, false);
    }

    /**
     *  This method blocks until the library is fully loaded and initialized.
     *
     *  @param context The context in which the method is called, the caller
     *    may pass in a null context if it doesn't know in which context it
     *    is running, or it doesn't need to work around the issue
     *    http://b/13216167.
     *
     *    When the context is not null and native library was not extracted
     *    by Android package manager, the LibraryLoader class
     *    will extract the native libraries from APK. This is a hack used to
     *    work around some Sony devices with the following platform bug:
     *    http://b/13216167.
     *
     *  @param shouldDeleteOldWorkaroundLibraries The flag tells whether the method
     *    should delete the old workaround libraries or not.
     */
    public static void ensureInitialized(
            Context context, boolean shouldDeleteOldWorkaroundLibraries)
            throws ProcessInitException {
        synchronized (sLock) {
            if (sInitialized) {
                // Already initialized, nothing to do.
                return;
            }
            loadAlreadyLocked(context, shouldDeleteOldWorkaroundLibraries);
            initializeAlreadyLocked();
        }
    }

    /**
     * Checks if library is fully loaded and initialized.
     */
    public static boolean isInitialized() {
        synchronized (sLock) {
            return sInitialized;
        }
    }

    /**
     * The same as loadNow(null, false), should only be called by
     * non-browser process.
     *
     * @throws ProcessInitException
     */
    public static void loadNow() throws ProcessInitException {
        loadNow(null, false);
    }

    /**
     * Loads the library and blocks until the load completes. The caller is responsible
     * for subsequently calling ensureInitialized().
     * May be called on any thread, but should only be called once. Note the thread
     * this is called on will be the thread that runs the native code's static initializers.
     * See the comment in doInBackground() for more considerations on this.
     *
     * @param context The context the code is running, or null if it doesn't have one.
     * @param shouldDeleteOldWorkaroundLibraries The flag tells whether the method
     *   should delete the old workaround libraries or not.
     *
     * @throws ProcessInitException if the native library failed to load.
     */
    public static void loadNow(Context context, boolean shouldDeleteOldWorkaroundLibraries)
            throws ProcessInitException {
        synchronized (sLock) {
            loadAlreadyLocked(context, shouldDeleteOldWorkaroundLibraries);
        }
    }

    /**
     * initializes the library here and now: must be called on the thread that the
     * native will call its "main" thread. The library must have previously been
     * loaded with loadNow.
     */
    public static void initialize() throws ProcessInitException {
        synchronized (sLock) {
            initializeAlreadyLocked();
        }
    }

    // Invoke System.loadLibrary(...), triggering JNI_OnLoad in native code
    private static void loadAlreadyLocked(
            Context context, boolean shouldDeleteOldWorkaroundLibraries)
            throws ProcessInitException {
        try {
            if (!sLoaded) {
                assert !sInitialized;

                long startTime = SystemClock.uptimeMillis();
                boolean useChromiumLinker = Linker.isUsed();

                if (useChromiumLinker) {
                    // Load libraries using the Chromium linker.
                    Linker.prepareLibraryLoad();

                    // Check if the device supports loading a library directly from the APK file.
                    String apkfile = context.getApplicationInfo().sourceDir;
                    if (Linker.isInBrowserProcess()) {
                        sLibraryLoadFromApkSupported = Linker.checkLibraryLoadFromApkSupport(
                                apkfile);
                    }

                    for (String library : NativeLibraries.LIBRARIES) {
                        String zipfile = null;
                        if (Linker.isInZipFile()) {
                            zipfile = apkfile;
                            Log.i(TAG, "Loading " + library + " from within " + zipfile);
                        } else {
                            Log.i(TAG, "Loading: " + library);
                        }

                        boolean isLoaded = false;
                        if (Linker.isUsingBrowserSharedRelros()) {
                            sIsUsingBrowserSharedRelros = true;
                            try {
                                if (zipfile != null) {
                                    Linker.loadLibraryInZipFile(zipfile, library);
                                } else {
                                    Linker.loadLibrary(library);
                                }
                                isLoaded = true;
                            } catch (UnsatisfiedLinkError e) {
                                Log.w(TAG, "Failed to load native library with shared RELRO, " +
                                      "retrying without");
                                Linker.disableSharedRelros();
                                sLoadAtFixedAddressFailed = true;
                            }
                        }
                        if (!isLoaded) {
                            if (zipfile != null) {
                                Linker.loadLibraryInZipFile(zipfile, library);
                            } else {
                                Linker.loadLibrary(library);
                            }
                        }
                    }

                    Linker.finishLibraryLoad();
                } else {
                    // Load libraries using the system linker.
                    for (String library : NativeLibraries.LIBRARIES) {
                        try {
                            System.loadLibrary(library);
                        } catch (UnsatisfiedLinkError e) {
                            if (context != null
                                && LibraryLoaderHelper.tryLoadLibraryUsingWorkaround(context,
                                                                                     library)) {
                                sNativeLibraryHackWasUsed = true;
                            } else {
                                throw e;
                            }
                        }
                    }
                }

                if (context != null
                    && shouldDeleteOldWorkaroundLibraries
                    && !sNativeLibraryHackWasUsed) {
                    LibraryLoaderHelper.deleteWorkaroundLibrariesAsynchronously(
                        context);
                }

                long stopTime = SystemClock.uptimeMillis();
                Log.i(TAG, String.format("Time to load native libraries: %d ms (timestamps %d-%d)",
                        stopTime - startTime,
                        startTime % 10000,
                        stopTime % 10000));

                sLoaded = true;
            }
        } catch (UnsatisfiedLinkError e) {
            throw new ProcessInitException(LoaderErrors.LOADER_ERROR_NATIVE_LIBRARY_LOAD_FAILED, e);
        }
        // Check that the version of the library we have loaded matches the version we expect
        Log.i(TAG, String.format(
                "Expected native library version number \"%s\"," +
                        "actual native library version number \"%s\"",
                NativeLibraries.sVersionNumber,
                nativeGetVersionNumber()));
        if (!NativeLibraries.sVersionNumber.equals(nativeGetVersionNumber())) {
            throw new ProcessInitException(LoaderErrors.LOADER_ERROR_NATIVE_LIBRARY_WRONG_VERSION);
        }
    }

    // The WebView requires the Command Line to be switched over before
    // initialization is done. This is okay in the WebView's case since the
    // JNI is already loaded by this point.
    public static void switchCommandLineForWebView() {
        synchronized (sLock) {
            ensureCommandLineSwitchedAlreadyLocked();
        }
    }

    // Switch the CommandLine over from Java to native if it hasn't already been done.
    // This must happen after the code is loaded and after JNI is ready (since after the
    // switch the Java CommandLine will delegate all calls the native CommandLine).
    private static void ensureCommandLineSwitchedAlreadyLocked() {
        assert sLoaded;
        if (sCommandLineSwitched) {
            return;
        }
        nativeInitCommandLine(CommandLine.getJavaSwitchesOrNull());
        CommandLine.enableNativeProxy();
        sCommandLineSwitched = true;
    }

    // Invoke base::android::LibraryLoaded in library_loader_hooks.cc
    private static void initializeAlreadyLocked() throws ProcessInitException {
        if (sInitialized) {
            return;
        }

        // Setup the native command line if necessary.
        if (!sCommandLineSwitched) {
            nativeInitCommandLine(CommandLine.getJavaSwitchesOrNull());
        }

        if (!nativeLibraryLoaded()) {
            Log.e(TAG, "error calling nativeLibraryLoaded");
            throw new ProcessInitException(LoaderErrors.LOADER_ERROR_FAILED_TO_REGISTER_JNI);
        }
        // From this point on, native code is ready to use and checkIsReady()
        // shouldn't complain from now on (and in fact, it's used by the
        // following calls).
        sInitialized = true;

        // The Chrome JNI is registered by now so we can switch the Java
        // command line over to delegating to native if it's necessary.
        if (!sCommandLineSwitched) {
            CommandLine.enableNativeProxy();
            sCommandLineSwitched = true;
        }

        // From now on, keep tracing in sync with native.
        TraceEvent.registerNativeEnabledObserver();
    }

    // Called after all native initializations are complete.
    public static void onNativeInitializationComplete() {
        recordBrowserProcessHistogram();
        nativeRecordNativeLibraryHack(sNativeLibraryHackWasUsed);
    }

    // Record Chromium linker histogram state for the main browser process. Called from
    // onNativeInitializationComplete().
    private static void recordBrowserProcessHistogram() {
        if (Linker.isUsed()) {
            assert Linker.isInBrowserProcess();
            nativeRecordChromiumAndroidLinkerBrowserHistogram(sIsUsingBrowserSharedRelros,
                                                              sLoadAtFixedAddressFailed,
                                                              sLibraryLoadFromApkSupported);
        }
    }

    // Register pending Chromium linker histogram state for renderer processes. This cannot be
    // recorded as a histogram immediately because histograms and IPC are not ready at the
    // time it are captured. This function stores a pending value, so that a later call to
    // RecordChromiumAndroidLinkerRendererHistogram() will record it correctly.
    public static void registerRendererProcessHistogram(boolean requestedSharedRelro,
                                                        boolean loadAtFixedAddressFailed) {
        if (Linker.isUsed()) {
            nativeRegisterChromiumAndroidLinkerRendererHistogram(requestedSharedRelro,
                                                                 loadAtFixedAddressFailed);
        }
    }

    private static native void nativeInitCommandLine(String[] initCommandLine);

    // Only methods needed before or during normal JNI registration are during System.OnLoad.
    // nativeLibraryLoaded is then called to register everything else.  This process is called
    // "initialization".  This method will be mapped (by generated code) to the LibraryLoaded
    // definition in base/android/library_loader/library_loader_hooks.cc.
    //
    // Return true on success and false on failure.
    private static native boolean nativeLibraryLoaded();

    // Method called to record statistics about the Chromium linker operation for the main
    // browser process. Indicates whether the linker attempted relro sharing for the browser,
    // and if it did, whether the library failed to load at a fixed address. Also records
    // support for memory mapping APK files with executable permissions.
    private static native void nativeRecordChromiumAndroidLinkerBrowserHistogram(
            boolean isUsingBrowserSharedRelros,
            boolean loadAtFixedAddressFailed,
            boolean apkMemoryMappingSupported);

    // Method called to register (for later recording) statistics about the Chromium linker
    // operation for a renderer process. Indicates whether the linker attempted relro sharing,
    // and if it did, whether the library failed to load at a fixed address.
    private static native void nativeRegisterChromiumAndroidLinkerRendererHistogram(
            boolean requestedSharedRelro,
            boolean loadAtFixedAddressFailed);

    // Get the version of the native library. This is needed so that we can check we
    // have the right version before initializing the (rest of the) JNI.
    private static native String nativeGetVersionNumber();

    private static native void nativeRecordNativeLibraryHack(boolean usedHack);
}
