package ratismal.drivebackup;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DriveBackupApi {
    private static final Map<String, Callable<Boolean>> beforeBackupStartCallablesMap = new HashMap<>(2);
    private static final Map<String, Runnable> onBackupDoneRunnablesMap = new HashMap<>(2);
    private static final Map<String, Runnable> onBackupErrorRunnablesMap = new HashMap<>(2);
    private static final ExecutorService executor = Executors.newFixedThreadPool(10);
    private static final long TIMEOUT = 10L;
    
    private static int deprecatedIdentifier = 1;
    
    @Contract (pure = true)
    private DriveBackupApi() {}
    
    /**
     * Gets whether to proceed with the backup by executing the {@code Callable}s specified by API users.
     * @return whether to proceed
     */
    static boolean shouldStartBackup() {
        Map<String, Future<Boolean>> futureMap = new HashMap<>(beforeBackupStartCallablesMap.size());
        for (Map.Entry<String, Callable<Boolean>> entry : beforeBackupStartCallablesMap.entrySet()) {
            futureMap.put(entry.getKey(), executor.submit(entry.getValue()));
        }
        boolean shouldStartBackup = true;
        for (Map.Entry<String, Future<Boolean>> entry : futureMap.entrySet()) {
            try {
                if (Boolean.FALSE.equals(entry.getValue().get(TIMEOUT, TimeUnit.SECONDS))) {
                    shouldStartBackup = false;
                    String message = "Not starting a backup due to " + entry.getKey() + " beforeBackupStart() Callable returning false";
                    MessageUtil.Builder().text(message).toConsole(true).send();
                    break;
                }
            } catch (InterruptedException | ExecutionException | TimeoutException exception) {
                MessageUtil.Builder().text("Failed to execute a beforeBackupStart() Callable, ignoring").toConsole(true).send();
                MessageUtil.sendConsoleException(exception);
            }
        }
        return shouldStartBackup;
    }

    /**
     * Runs the {@code Callable}s specified by API users to be run after a backup is successfully completed.
     */
    static void backupDone() {
        final String DID_NOT_EXECUTE_SUCCESSFULLY = "A onBackupDone() Runnable returned false";
        final String FAILED_TO_EXECUTE = "Failed to execute an onBackupDone() Runnable, ignoring";
        processRunnables(onBackupDoneRunnablesMap, DID_NOT_EXECUTE_SUCCESSFULLY, FAILED_TO_EXECUTE);
    }

    /**
     * Runs the {@code Callable}s specified by API users to be run after an error occurs during a backup.
     */
    static void backupError() {
        final String DID_NOT_EXECUTE_SUCCESSFULLY = "A onBackupError() Runnable returned false";
        final String FAILED_TO_EXECUTE = "Failed to execute an onBackupError() Runnable, ignoring";
        processRunnables(onBackupErrorRunnablesMap, DID_NOT_EXECUTE_SUCCESSFULLY, FAILED_TO_EXECUTE);
    }
    
    private static void processRunnables(@NotNull Map<String, Runnable>runnableMap, String didNotExecuteSuccessfully, String failedToExecuteMsg) {
        Map<String, Future<Boolean>> futures = new HashMap<>(runnableMap.size());
        for (Map.Entry<String, Runnable> entry : runnableMap.entrySet()) {
            futures.put(entry.getKey(), executor.submit(entry.getValue(), Boolean.TRUE));
        }
        for (Map.Entry<String, Future<Boolean>> entry : futures.entrySet()) {
            try {
                if (!Boolean.TRUE.equals(entry.getValue().get(TIMEOUT, TimeUnit.SECONDS))) {
                    MessageUtil.Builder().text(didNotExecuteSuccessfully).toConsole(true).send();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                MessageUtil.Builder().text(failedToExecuteMsg).toConsole(true).send();
                MessageUtil.sendConsoleException(e);
            }
        }
    }
    
    private static void testIdentifier(String identifier) throws IllegalArgumentException {
        try {
            Integer.parseInt(identifier);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identifier must be an integer");
        }
    }
    
    /**
     * Gets the plugin's parsed config as an object
     * @return the config
     */
    @Contract (pure = true)
    public static Config getConfig() {
        return ConfigParser.getConfig();
    }

    /**
     * Runs the specified {@code Callable} after a backup has been initiated
     * (either manually using {@code /drivebackup backup} or with the API,
     * or automatically with scheduled or interval-based backups),
     * but before the backup process has been started.
     * <p>
     * Multiple {@code Callable}s can be specified by calling this method multiple times.
     * <p>
     * If any {@code Callable} returns {@code false}, the backup will be canceled
     * <p>
     * If the {@code Callable} doesn't return in 10 seconds, the {@code Callable} will be ignored.
     * @param callable the {@code Callable}
     */
    @Deprecated
    public static void beforeBackupStart(Callable<Boolean> callable) {
        beforeBackupStartCallablesMap.put(String.valueOf(deprecatedIdentifier++), callable);
    }
    
    /**
     * Runs the specified {@code Callable} after a backup has been initiated
     * (either manually using {@code /drivebackup backup} or with the API,
     * or automatically with scheduled or interval-based backups),
     * but before the backup process has been started.
     * <p>
     * Multiple {@code Callable}s can be specified by calling this method multiple times.
     * <p>
     * If any {@code Callable} returns {@code false}, the backup will be canceled
     * <p>
     * If the {@code Callable} doesn't return in 10 seconds, the {@code Callable} will be ignored.
     *
     * @param identifier the identifier
     *                   <p>
     *                   This identifier is used to identify the {@code Callable} in the logs
     *                   <p>
     *                   If the identifier is an integer, an {@code IllegalArgumentException} will be thrown
     * @param callable the {@code Callable}
     */
    public static void beforeBackupStart(String identifier, Callable<Boolean> callable) {
        testIdentifier(identifier);
        beforeBackupStartCallablesMap.put(identifier, callable);
    }

    /**
     * Runs the specified {@code Runnable} after a backup is successfully completed
     * @param runnable the {@code Runnable}
     */
    @Deprecated
    public static void onBackupDone(Runnable runnable) {
        onBackupDoneRunnablesMap.put(String.valueOf(deprecatedIdentifier++), runnable);
    }
    
    /**
     * Runs the specified {@code Runnable} after a backup is successfully completed
     * <p>
     * Multiple {@code Runnable}s can be specified by calling this method multiple times.
     *
     * @param identifier the identifier
     *                   <p>
     *                   This identifier is used to identify the {@code Runnable} in the logs
     *                   <p>
     *                   If the identifier is an integer, an {@code IllegalArgumentException} will be thrown
     * @param runnable the {@code Runnable}
     */
    public static void onBackupDone(String identifier, Runnable runnable) {
        testIdentifier(identifier);
        onBackupDoneRunnablesMap.put(identifier, runnable);
    }

    /**
     * Runs the specified {@code Runnable} after an error occurs during a backup
     * @param runnable the {@code Runnable}
     */
    @Deprecated
    public static void onBackupError(Runnable runnable) {
        onBackupErrorRunnablesMap.put(String.valueOf(deprecatedIdentifier++), runnable);
    }
    
    /**
     * Runs the specified {@code Runnable} after an error occurs during a backup
     * <p>
     * Multiple {@code Runnable}s can be specified by calling this method multiple times.
     *
     * @param identifier the identifier
     *                   <p>
     *                   This identifier is used to identify the {@code Runnable} in the logs
     *                   <p>
     *                   If the identifier is an integer, an {@code IllegalArgumentException} will be thrown
     * @param runnable the {@code Runnable}
     */
    public static void onBackupError(String identifier, Runnable runnable) {
        testIdentifier(identifier);
        onBackupErrorRunnablesMap.put(identifier, runnable);
    }

    /**
     * Starts a backup
     * <p>
     * Behaves identically to running {@code /drivebackup backup}
     */
    public static void startBackup() {
        new Thread(new UploadThread()).start();
    }

    /**
     * Reloads the DriveBackupV2 plugin's {@code config.yml}
     * <p>
     * Behaves identically to running {@code /drivebackup reloadconfig}
     */
    public static void reloadConfig() {
        DriveBackup.reloadLocalConfig();
    }

    /**
     * Returns the message sent to chat when {@code /drivebackup nextbackup} is run
     * <p>
     * For more information about the {@code /drivebackup nextbackup} command,
     * see <a href="https://github.com/MaxMaeder/DriveBackupV2/wiki/Commands#drivebackup-nextbackup">this wiki page</a>
     * @return the message
     */
    public static String getNextAutoBackup() {
        return UploadThread.getNextAutoBackup();
    }
}
