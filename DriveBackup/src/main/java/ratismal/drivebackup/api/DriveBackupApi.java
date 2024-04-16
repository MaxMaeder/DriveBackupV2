package ratismal.drivebackup.api;

import org.jetbrains.annotations.Contract;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Represents the DriveBackupV2 API
 *
 */
public final class DriveBackupApi {
    private static final Map<String, Callable<Boolean>> beforeBackupStartCallablesMap = new HashMap<>(2);
    private static final Map<String, Runnable> onBackupDoneRunnablesMap = new HashMap<>(2);
    private static final Map<String, Runnable> onBackupErrorRunnablesMap = new HashMap<>(2);
    
    private static int deprecatedIdentifier = 1;
    
    @Contract (pure = true)
    private DriveBackupApi() {}
    
    @Contract (pure = true)
    static Map<String, Callable<Boolean>> getBeforeBackupStartCallablesMap() {
        return beforeBackupStartCallablesMap;
    }
    
    @Contract (pure = true)
    static Map<String, Runnable> getOnBackupDoneRunnablesMap() {
        return onBackupDoneRunnablesMap;
    }
    
    @Contract (pure = true)
    static Map<String, Runnable> getOnBackupErrorRunnablesMap() {
        return onBackupErrorRunnablesMap;
    }
    
    private static void testIdentifier(String identifier) throws IllegalArgumentException {
        try {
            Integer.parseInt(identifier);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identifier must be an integer");
        }
    }
    
    /**
     * Gets the plugin's config in a CommendedConfigurationNode object
     * @return the config object
     */
    @Contract (pure = true)
    public static CommentedConfigurationNode getConfig() {
        return APIHandler.getInstance().getDBInstance().getConfigHandler().getConfig().getConfig();
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
        APIHandler.getInstance().startBackup();
    }

    /**
     * Reloads the DriveBackupV2 plugin's {@code config.yml}
     * <p>
     * Behaves identically to running {@code /drivebackup reloadconfig}
     */
    public static void reloadConfig() {
        APIHandler.getInstance().reloadConfig();
    }

    /**
     * Returns the message sent to chat when {@code /drivebackup nextbackup} is run
     * <p>
     * For more information about the {@code /drivebackup nextbackup} command,
     * see <a href="https://github.com/MaxMaeder/DriveBackupV2/wiki/Commands#drivebackup-nextbackup">this wiki page</a>
     * @return the message
     */
    public static String getNextAutoBackup() {
        return APIHandler.getInstance().getNextAutoBackup();
    }
}
