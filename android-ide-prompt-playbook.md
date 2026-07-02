# Secure Android IDE — File-by-File Prompt Playbook

Architecture: Phase 1 (Base) → Phase 2 (Git Observer, read-only observer of Phase 1) → Phase 3 (C++ Security Boss, commands Java via JNI, Java never commands C++).

---

## PHASE 1 — BASE LAYER

### 1. activity_main.xml

**[File Name]**: activity_main.xml
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/res/layout/activity_main.xml
**[Dependencies / Calls]**: Inflated by MainActivity.java. Contains RecyclerView bound to FileAdapter. Contains a reserved, initially GONE ViewGroup/ViewStub anchor for Phase 2's injected Git button — Phase 1 must not reference Git classes, it only exposes the anchor's ID.
**[Core Logic Prompt]**: Write an XML layout for the IDE's main screen. Root: CoordinatorLayout or ConstraintLayout. Include: (a) a Toolbar at top, (b) a RecyclerView with id `recycler_files` filling the body for the file browser, (c) a FloatingActionButton `fab_ad_action` used by AdManager's alternating ad/gap logic, (d) an empty FrameLayout or ViewStub with id `git_button_anchor`, visibility="gone", positioned top-right of the toolbar area — this is a dumb placeholder container that Phase 2 will inflate a button into at runtime; it must contain zero Git-specific attributes, strings, or drawables in Phase 1. Do not import any Git or C++ related resource. Use ConstraintLayout constraints so `git_button_anchor` sits without overlapping the FAB.
**[Pseudo-Code]**: N/A

---ok

### 2. item_file.xml

**[File Name]**: item_file.xml
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/res/layout/item_file.xml
**[Dependencies / Calls]**: Inflated by FileAdapter.java's onCreateViewHolder. Bound to FileManager's file model per item.
**[Core Logic Prompt]**: Write a RecyclerView row layout for a single file/folder entry. Root: LinearLayout (horizontal) or ConstraintLayout. Include: ImageView `icon_file_type` (folder/zip/file icon), TextView `text_file_name`, TextView `text_file_meta` (size/date, smaller/greyed), and an optional trailing ImageView `icon_overflow` for a context menu. Keep this file completely generic — it has no knowledge of git state, security state, or ad state. Use padding of 12–16dp and a ripple selectable background on the root for touch feedback.
**[Pseudo-Code]**: N/A

---ok

### 3. MainActivity.java

**[File Name]**: MainActivity.java
**[Folder Path]**: app/src/main/java/com/yourpackage/ide/ui/MainActivity.java
**[Dependencies / Calls]**: Inflates activity_main.xml. Instantiates FileManager, ZipExtractor, FileAdapter, AdManager. Exposes a public, minimal "hook surface" (e.g. `getRootViewGroup()`, `getCurrentDirectoryState()`, a lightweight listener/observer registration method) that Phase 2's GitObserver will later attach to WITHOUT MainActivity importing anything from the git package. Must NOT import JniBossLayer or any C++ bridge class in Phase 1.
**[Core Logic Prompt]**: Write MainActivity as the single entry point of the IDE app for Phase 1 only. Responsibilities: (1) onCreate — inflate layout, set up Toolbar, initialize RecyclerView with a FileAdapter bound to FileManager's root directory listing, initialize AdManager and bind fab_ad_action's onClick to AdManager.handleClick(). (2) Handle file item clicks by delegating to FileManager (open file / navigate into folder) and ZipExtractor when a `.zip` is tapped, refreshing the adapter afterward. (3) Expose the `git_button_anchor` ViewGroup via a public getter ONLY — do not act on it, do not touch its visibility, do not reference any Git class. This is what allows Phase 2 to be bolted on later via observation instead of direct coupling. (4) Implement Android lifecycle methods cleanly (onDestroy should release FileManager resources). Strict constraint: this class must compile and run as a complete, working file browser IDE shell with zero references to git, JNI, or C++ symbols. Add a code comment block at the top of the class explicitly stating "PHASE 1 — NO GIT / NO NATIVE DEPENDENCIES" to enforce the decoupling rule for future maintainers.
**[Pseudo-Code]**: N/A

---ok

### 4. FileAdapter.java

**[File Name]**: FileAdapter.java
**[Folder Path]**: app/src/main/java/com/yourpackage/ide/ui/FileAdapter.java
**[Dependencies / Calls]**: Extends RecyclerView.Adapter. Inflates item_file.xml. Receives a List<FileItem> model from FileManager. Fires a click-listener callback interface back up to MainActivity — must not call FileManager or ZipExtractor methods directly itself (keep it a pure "dumb" view layer).
**[Core Logic Prompt]**: Write a RecyclerView.Adapter<FileAdapter.ViewHolder> that binds a list of file model objects to item_file.xml rows. Include a ViewHolder inner class caching the icon, name, and meta TextViews. Provide a public `updateData(List<FileItem> newItems)` method using either full notifyDataSetChanged or DiffUtil (prefer DiffUtil for performance). Define an interface `OnFileClickListener` with `onFileClick(FileItem item)` and `onFileLongClick(FileItem item)`, and require it via constructor injection so MainActivity supplies the listener — the adapter itself must contain NO business logic (no file I/O, no zip logic, no git awareness). Icon selection logic should be a simple switch on file extension/isDirectory flag done locally within the adapter using only Android resource drawables.
**[Pseudo-Code]**: N/A

---ok

### 5. FileManager.java

**[File Name]**: FileManager.java
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/core/file/FileManager.java
**[Dependencies / Calls]**: Uses java.io.File / java.nio APIs. Called by MainActivity for directory listing, navigation, create/rename/delete. Provides the FileItem model class consumed by FileAdapter. Later (Phase 2) GitObserver will read from FileManager's public getters only — never call into FileManager's private state.
**[Core Logic Prompt]**: Write FileManager as the sole owner of filesystem state for the app. Responsibilities: (1) Define a `FileItem` model (name, absolutePath, isDirectory, sizeBytes, lastModified). (2) `listFiles(File directory)` returning sorted List<FileItem> (folders first, then alphabetical). (3) `navigateInto(FileItem folder)` / `navigateUp()` maintaining an internal back-stack of the current path. (4) CRUD operations: `createFile`, `createFolder`, `rename`, `delete`, `move`, each with proper error handling (permission denials, IO exceptions) surfaced via a callback/Result wrapper rather than thrown checked exceptions leaking to the UI layer. (5) A read-only public getter `getCurrentDirectory()` and `getCurrentDirectoryPath()` since Phase 2's GitObserver will poll this to detect whether the current folder is a git repo — but FileManager itself must have zero knowledge of git; it doesn't import anything from the git package. (6) Use Android's Storage Access Framework or scoped storage-safe APIs where relevant to modern Android versions; note in comments if legacy External Storage permission fallback is needed for older API levels.
**[Pseudo-Code]**: N/A

---ok

### 6. ZipExtractor.java

**[File Name]**: ZipExtractor.java
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/core/file/ZipExtractor.java
**[Dependencies / Calls]**: Uses java.util.zip (ZipInputStream/ZipFile). Called by MainActivity when a `.zip` FileItem is tapped. Writes extracted output back through FileManager's directory conventions so the RecyclerView reflects new files after extraction.
**[Core Logic Prompt]**: Write ZipExtractor as a standalone utility class (can be static methods or a lightweight instantiable class) responsible only for zip extraction and creation. Responsibilities: (1) `extract(File zipFile, File destinationDir, ProgressCallback callback)` — stream entries out with zip-slip path traversal protection (validate that each extracted entry's canonical path stays within destinationDir before writing, to guard against malicious zip entries with `../` paths). (2) Report progress (bytes or entry count) via a callback interface so MainActivity/UI can show a progress bar without ZipExtractor knowing anything about Android UI widgets. (3) Optional `compress(List<File> files, File outputZip)` for creating archives. (4) Run extraction on a background thread/executor, never on the main thread — return results via callback/listener posted back to the main thread. (5) No dependency on FileManager's internals — it only takes/returns File paths, keeping it decoupled and reusable.
**[Pseudo-Code]**: N/A

---

### 7. AdManager.java

**[File Name]**: AdManager.java
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/core/ads/AdManager.java
**[Dependencies / Calls]**: Wraps a mediation/ad SDK (e.g., AdMob interstitial). Called exclusively by MainActivity's `fab_ad_action` click handler. Must not be called by, or aware of, Git or Security layers.
**[Core Logic Prompt]**: Write AdManager to implement a strict alternating gap-based monetization pattern: every OTHER user action triggers an interstitial ad before proceeding, and the in-between actions execute immediately with a cooldown/gap. Responsibilities: (1) Maintain a persistent (SharedPreferences-backed, survives process death) click counter or boolean toggle state — not just an in-memory field — so the alternation is consistent across app restarts. (2) Preload the next interstitial ad ahead of time so there's no loading delay when it's the "ad turn." (3) Expose a single public method `handleClick(Runnable action)`: on odd-numbered calls, show the preloaded interstitial and only invoke `action.run()` in the ad's onAdDismissed/onAdFailedToShow callback; on even-numbered calls, skip the ad and invoke `action.run()` immediately, then start preloading the next ad for the following odd turn. (4) Include a minimum time-gap guard (e.g., don't show ads more than once per N seconds) to protect against accidental rapid double-taps causing back-to-back ads. (5) Keep this class fully self-contained — no imports from git or native/security packages.
**[Pseudo-Code]**:
```
class AdManager:
    state: persisted counter (or boolean "nextIsAd") in SharedPreferences
    preloadedAd: InterstitialAd | null

    on init:
        load counter from prefs
        preloadNextAd()

    function handleClick(action: Runnable):
        if counter is odd (i.e. "ad turn"):
            if preloadedAd is ready:
                show preloadedAd
                on ad dismissed OR failed to show:
                    action.run()
                    preloadNextAd()
            else:
                # fallback: ad not ready, don't block user
                action.run()
                preloadNextAd()
        else:
            # gap turn — no ad
            action.run()
            preloadNextAd()  # get ready for the next ad turn

        counter += 1
        persist counter to SharedPreferences

    function preloadNextAd():
        request new InterstitialAd from SDK
        on loaded -> preloadedAd = ad
        on failed -> retry with backoff
```

---ok

## PHASE 2 — GIT OBSERVER LAYER
*(Rule: Analyzes Phase 1 state via read-only observation. Never modifies Phase 1 classes. Injects UI only into the pre-reserved anchor. Never called from within Phase 1 code — Phase 1 has zero imports of these classes.)*

### 8. GitObserver.java

**[File Name]**: GitObserver.java
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/git/
**[Dependencies / Calls]**: Reads MainActivity's public getters (`getCurrentDirectoryPath()`) and FileManager's public directory state — read-only, no setters called. Calls GitOperations to check repo/token validity. On state change, invokes GitUIInjector to add/remove the UI button. Registered onto MainActivity from OUTSIDE — e.g., MainActivity is instantiated in Phase 1 with no knowledge of GitObserver; the wiring (`new GitObserver(mainActivity)`) happens in a Phase 2 bootstrap/Application class or an optional attachment call added later, keeping Phase 1 compiling standalone.
**[Core Logic Prompt]**: Write GitObserver as a passive watcher that polls or listens (via a lightweight polling loop, FileObserver on the directory, or a periodic Handler) for changes to the current directory exposed by MainActivity/FileManager. Responsibilities: (1) On every directory-change event (folder navigation, app resume), check whether the current directory contains a `.git` folder AND whether GitOperations reports a valid stored auth token. (2) Based on the combination of {isGitRepo, hasValidToken, hasUncommittedChanges}, decide the required UI state: NONE / SHOW_UPLOAD (untracked repo needing init+push) / SHOW_COMMIT (existing repo with local changes) and call `GitUIInjector.updateButtonState(state)` accordingly — GitObserver contains the decision logic, GitUIInjector only renders it. (3) Never call any method that mutates MainActivity/FileManager internal state — this class is read-only with respect to Phase 1. (4) Wrap all GitOperations calls in try/catch, and on any error route it to GitUIInjector's `showErrorPopup(message)` rather than crashing or silently swallowing it. (5) Provide a `start()`/`stop()` lifecycle pair so it can be attached/detached from MainActivity's onResume/onPause externally without MainActivity itself importing GitObserver's type — attachment should be done via a generic interface or reflection-free composition root, e.g., a static `GitBootstrap.attach(Activity, ViewGroup anchor)` helper in the git package.
**[Pseudo-Code]**:
```
class GitObserver:
    lastPath: String
    listener: DirectoryChangeSource (from Phase 1's exposed getters, read-only)

    function start():
        register poll/observer on FileManager's current path
        runInitialCheck()

    function onDirectoryChanged(newPath):
        if newPath == lastPath: return
        lastPath = newPath
        runCheck(newPath)

    function runCheck(path):
        try:
            isRepo = GitOperations.isGitRepo(path)
            if not isRepo:
                GitUIInjector.updateButtonState(NONE)
                return

            hasToken = GitOperations.hasValidToken()
            if not hasToken:
                GitUIInjector.updateButtonState(NONE)
                GitUIInjector.showErrorPopup("No auth token — connect an account")
                return

            hasChanges = GitOperations.hasUncommittedChanges(path)
            if hasChanges:
                GitUIInjector.updateButtonState(SHOW_COMMIT)
            else:
                GitUIInjector.updateButtonState(SHOW_UPLOAD)

        catch GitException e:
            GitUIInjector.showErrorPopup(e.message)
            GitUIInjector.updateButtonState(NONE)

    function stop():
        unregister observer
```ok

---

### 9. GitOperations.java

**[File Name]**: GitOperations.java
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/git/
**[Dependencies / Calls]**: Wraps libgit2 (via JNI/JGit-equivalent bindings) or a native git binary invocation. Called by GitObserver (status checks) and GitUIInjector's button click handlers (commit/push actions). Reads/writes an encrypted token store (e.g., Android Keystore-backed EncryptedSharedPreferences) — do not store PATs in plaintext.
**[Core Logic Prompt]**: Write GitOperations as the stateless-per-call execution layer for all actual git actions. Responsibilities: (1) `isGitRepo(String path)` — check for `.git` directory presence. (2) `hasUncommittedChanges(String path)` — run git status equivalent via the libgit2 binding and return boolean. (3) `hasValidToken()` / `getToken()` / `saveToken(String pat)` — backed by EncryptedSharedPreferences or Android Keystore, never plain SharedPreferences, since this stores a Personal Access Token. (4) `commit(String path, String message)` and `push(String path)` — each returning a Result<Success, GitException> rather than throwing raw exceptions across the API boundary, with GitException carrying a user-displayable message. (5) `initRepo(String path)` for first-time repos before initial push. (6) All network/disk-heavy git calls must run off the main thread via an Executor, with results delivered via callback to the caller (GitObserver/GitUIInjector), which are responsible for posting back to the UI thread themselves. (7) This class does not touch any UI element directly and does not import Android View classes.
**[Pseudo-Code]**: N/A

---

### 10. GitUIInjector.java

**[File Name]**: GitUIInjector.java
**[Folder Path]**: /storage/emulated/0/Download/GitHub_aide/app/src/main/java/com/adobs/ide/git/
**[Dependencies / Calls]**: Inflates a dynamically created Button/MaterialButton into the `git_button_anchor` ViewGroup exposed by MainActivity/activity_main.xml. Calls GitOperations.commit()/push() on button click. Receives state instructions from GitObserver only — contains no independent decision logic about *when* to show a button, only *how*.
**[Core Logic Prompt]**: Write GitUIInjector as the pure rendering/injection layer for Git UI. Responsibilities: (1) `updateButtonState(GitUiState state)` where state is an enum {NONE, SHOW_UPLOAD, SHOW_COMMIT} — on NONE, remove/hide any previously injected button from the anchor ViewGroup; on SHOW_UPLOAD, inflate/show a Blue MaterialButton labeled "Upload" whose onClick calls `GitOperations.initRepo()` then `push()`; on SHOW_COMMIT, show a Blue MaterialButton labeled "Commit" whose onClick calls `GitOperations.commit()` then `push()`. (2) `showErrorPopup(String message)` — display a floating popup (Snackbar anchored to the root CoordinatorLayout, or a custom PopupWindow if Snackbar isn't available in context) with the error message and a dismiss action; do not use blocking AlertDialogs for transient git errors. (3) Never add more than one button instance into the anchor — always clear previous children before inflating a new state. (4) This class receives the `ViewGroup anchor` reference once at construction/attachment time (passed in externally, e.g. from `MainActivity.getGitAnchorViewGroup()`, called from the Phase 2 bootstrap, not from within MainActivity itself) — it never searches the view hierarchy itself, keeping the coupling to a single injected reference. (5) All state changes must be posted on the main thread (use runOnUiThread/Handler if called from a background callback).
**[Pseudo-Code]**: N/A

---

## PHASE 3 — C++ SECURITY BOSS LAYER
*(Rule: C++ is the commander — it inspects the environment and dictates outcomes to Java via JNI callbacks/upcalls. Java-side code must not poll or "ask permission" from C++; it only implements the interface that native code invokes.)*

### 11. JniBossLayer.java

**[File Name]**: JniBossLayer.java
**[Folder Path]**: app/src/main/java/com/yourpackage/ide/security/JniBossLayer.java
**[Dependencies / Calls]**: Loads the native library (`System.loadLibrary("security_boss")`). Declares native methods implemented in security_boss.cpp. Implements callback methods that C++ invokes via JNI upcalls (e.g., `onThreatDetected(int severity)`, `onKillProcessRequested()`, `onHideGitUiRequested()`) which internally call GitUIInjector.updateButtonState(NONE) and/or `android.os.Process.killProcess()` — but ONLY in response to a native-originated call, never proactively.
**[Core Logic Prompt]**: Write JniBossLayer as the thin Java-side receiver for native security commands — it must contain no independent threat-detection logic itself (all detection happens in C++). Responsibilities: (1) Static initializer block loading the native `.so` via `System.loadLibrary`. (2) Declare `native void startSecurityMonitor()` and `native void stopSecurityMonitor()` which Java calls once (e.g., from Application.onCreate or MainActivity.onCreate) purely to hand control to native code — this is the ONLY direction Java initiates anything; after this call, C++ drives all subsequent action. (3) Implement JNI-callable instance methods matching signatures the native code will invoke via `CallVoidMethod`/`FindClass`/`GetMethodID`: `public void onThreatDetected(int severityCode)`, `public void onForceHideGitButtons()` (calls into GitUIInjector.updateButtonState(NONE)), and `public void onForceKillProcess()` (calls `android.os.Process.killProcess(android.os.Process.myPid())` then `System.exit(10)`). (4) These callback methods must be lightweight and defensive — wrap any downstream call in try/catch so a failure in, say, GitUIInjector never crashes the JNI call stack back into native code. (5) Register this class/instance with native code via `registerNatives`/passing `this` as a global ref during `startSecurityMonitor()` so C++ can call back on it at any time, including from a background native thread — ensure UI-affecting callbacks (onForceHideGitButtons) post to the main thread via a Handler rather than assuming they're already on it.
**[Pseudo-Code]**:
```
class JniBossLayer:
    static { System.loadLibrary("security_boss") }

    native void startSecurityMonitor()   // Java -> Native, one-time handoff
    native void stopSecurityMonitor()

    // ---- Called BY native code (C++ commands Java) ----
    function onThreatDetected(severityCode):
        try:
            log threat
            if severityCode >= CRITICAL:
                onForceKillProcess()
            else:
                onForceHideGitButtons()
        catch e: log-only, never rethrow across JNI boundary

    function onForceHideGitButtons():
        runOnMainThread ->
            try: GitUIInjector.updateButtonState(NONE)
            catch e: log-only

    function onForceKillProcess():
        runOnMainThread ->
            try:
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            catch e: log-only
```

---

### 12. security_boss.cpp

**[File Name]**: security_boss.cpp
**[Folder Path]**: app/src/main/cpp/security_boss.cpp
**[Dependencies / Calls]**: Implements the native methods declared in JniBossLayer.java (`Java_com_yourpackage_ide_security_JniBossLayer_startSecurityMonitor`, etc.). Uses JNI's `JNIEnv`/`JavaVM` to obtain a global reference to the JniBossLayer instance and call back into it (`CallVoidMethod`) — this is the commanding direction of the architecture.
**[Core Logic Prompt]**: Write security_boss.cpp implementing the native side of the security layer. Responsibilities: (1) `JNIEXPORT void JNICALL Java_..._startSecurityMonitor(JNIEnv* env, jobject thiz)` — cache `JavaVM*` via `env->GetJavaVM()`, create a global reference to `thiz` (the JniBossLayer instance) so it remains valid across threads and calls, then spawn a dedicated native monitoring thread (std::thread or pthread) that runs the detection loop; return immediately (non-blocking) so Java's onCreate isn't stalled. (2) The monitoring thread loop should periodically perform environment checks: debugger-attached detection (e.g., reading `/proc/self/status` TracerPid, or ptrace self-attach trick), root/Frida detection (scanning `/proc/self/maps` for suspicious library names like `frida-agent`, checking for known root binaries/su paths), and basic tamper checks (e.g., verifying the app's own signature/checksum if feasible). (3) On detecting an anomaly, the native thread must attach itself to the JVM (`JavaVM->AttachCurrentThread`) since it's not the original JNI call thread, look up the JniBossLayer class/method via `FindClass`+`GetMethodID` (cached once, not per-call, for performance), and invoke `env->CallVoidMethod(globalRef, onThreatDetectedMethodId, severityCode)` — this is the literal "C++ commands Java" moment. (4) Detach the thread from the JVM before it exits the loop/thread function to avoid leaks. (5) `stopSecurityMonitor` sets an atomic bool flag to break the loop and joins the thread cleanly, releasing the global reference via `DeleteGlobalRef`. (6) Keep all detection logic self-contained in this file with no dependency on git-related symbols — Phase 3 only knows it has authority to command "hide git UI" or "kill process," not what git actually is.
**[Pseudo-Code]**:
```
// Global state
JavaVM* g_vm
jobject g_bossInstanceGlobalRef
jmethodID g_onThreatDetectedMethodId
atomic<bool> g_monitorRunning

JNIEXPORT void startSecurityMonitor(JNIEnv* env, jobject thiz):
    env->GetJavaVM(&g_vm)
    g_bossInstanceGlobalRef = env->NewGlobalRef(thiz)

    jclass cls = env->GetObjectClass(thiz)
    g_onThreatDetectedMethodId = env->GetMethodID(cls, "onThreatDetected", "(I)V")

    g_monitorRunning = true
    spawn thread(monitorLoop)   // detached from JNI call thread
    return immediately

function monitorLoop():
    JNIEnv* threadEnv
    g_vm->AttachCurrentThread(&threadEnv, null)

    while g_monitorRunning:
        if detectDebugger() or detectFridaOrRoot() or detectTamper():
            severity = computeSeverity(...)
            threadEnv->CallVoidMethod(g_bossInstanceGlobalRef, g_onThreatDetectedMethodId, severity)
        sleep(POLL_INTERVAL_MS)

    g_vm->DetachCurrentThread()

JNIEXPORT void stopSecurityMonitor(JNIEnv* env, jobject thiz):
    g_monitorRunning = false
    join thread
    env->DeleteGlobalRef(g_bossInstanceGlobalRef)

function detectFridaOrRoot() -> bool:
    read /proc/self/maps
    for each line:
        if contains "frida" or "gum-js-loop" or known root paths ("/system/xbin/su", "/system/bin/su", magisk artifacts):
            return true
    return false

function detectDebugger() -> bool:
    read /proc/self/status
    parse "TracerPid:" value
    return value != 0
```

---

### 13. CMakeLists.txt

**[File Name]**: CMakeLists.txt
**[Folder Path]**: app/src/main/cpp/CMakeLists.txt
**[Dependencies / Calls]**: Builds security_boss.cpp into the `security_boss` shared library that JniBossLayer.java loads via `System.loadLibrary("security_boss")`. Links against Android's `log` library for logging, and `libgit2` if Phase 2's GitOperations also uses native git bindings (kept as a separate target if libgit2 is C++-side, to avoid Phase 3 and Phase 2 sharing a single opaque `.so`).
**[Core Logic Prompt]**: Write a CMakeLists.txt for the Android Gradle NDK build. Responsibilities: (1) Set `cmake_minimum_required` and `project(security_boss)`. (2) `add_library(security_boss SHARED security_boss.cpp)` producing `libsecurity_boss.so`. (3) `find_library(log-lib log)` and `target_link_libraries(security_boss ${log-lib})` for `__android_log_print` debug output in the native monitor loop. (4) Set C++ standard to at least C++17 (`set(CMAKE_CXX_STANDARD 17)`) for `std::thread`/`std::atomic` usage. (5) If libgit2 is compiled as a separate native target for GitOperations, define it as its own `add_library(git2ops SHARED gitops_jni.cpp)` target with its own link step against a prebuilt libgit2 static/shared lib (via `add_library(libgit2 STATIC IMPORTED)` + `set_target_properties(... IMPORTED_LOCATION ...)`), keeping security_boss and git2ops as two independently buildable native modules so Phase 2's native code and Phase 3's native code remain physically separate binaries, reinforcing the decoupling rule at the build-system level. (6) Ensure ABI filters (`arm64-v8a`, `armeabi-v7a`, `x86_64`) are configured in the module-level `build.gradle`'s `ndk { abiFilters ... }` referencing this CMake file, and note that in comments since abiFilters live outside this file.
**[Pseudo-Code]**: N/A

---

## Cross-Cutting Notes for the AI Code Generator

- When generating Phase 1 files, explicitly instruct the generating AI to produce code that compiles with zero imports from `com.yourpackage.ide.git.*` or `com.yourpackage.ide.security.*` packages — treat this as a hard lint rule.
- When generating Phase 2 files, remind the AI that GitObserver/GitUIInjector are attached externally (composition root pattern) — MainActivity's source code itself should never be re-generated or edited to add git imports.
- When generating Phase 3 files, remind the AI that the JNI method direction is native-calls-Java (upcalls), not Java-polls-native — this is the opposite of typical beginner JNI tutorials and should be called out explicitly in the prompt to avoid the AI defaulting to a Java-driven polling pattern.
