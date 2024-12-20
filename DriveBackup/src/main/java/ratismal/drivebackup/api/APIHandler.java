package ratismal.drivebackup.api;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class APIHandler {
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(10);
    private static final long TIMEOUT = 10L;
    private final DriveBackupInstance instance;
    private static APIHandler instanceAPIHandler;
    
    @Contract (pure = true)
    public APIHandler(DriveBackupInstance instance) {
        this.instance = instance;
        setInstanceAPIHandler(this);
    }
    
    private static void setInstanceAPIHandler(APIHandler instanceAPIHandler) {
        APIHandler.instanceAPIHandler = instanceAPIHandler;
    }
    
    @Contract (pure = true)
    public static APIHandler getInstance() {
        if (instanceAPIHandler == null) {
            throw new IllegalStateException("APIHandler has not been initialized");
        }
        return instanceAPIHandler;
    }
    
    DriveBackupInstance getDBInstance() {
        return instance;
    }
    
    protected void startBackup() {
        new Thread(new UploadThread(instance, Initiator.API)).start();
    }
    
    protected String getNextAutoBackup() {
        //TODO
        return "";
    }
    
    protected void reloadConfig() {
        try {
            instance.getLangConfigHandler().reload();
            instance.getConfigHandler().reload();
        } catch (ConfigurateException e) {
            instance.getLoggingHandler().error("Failed to reload config", e);
        }
    }
    
    /**
     * gets whether to proceed with the backup by executing the {@code Callable}s specified by API users
     * @return whether to proceed
     */
    public boolean shouldStartBackup() {
        Map<String, Callable<Boolean>> callablesMap = DriveBackupApi.getBeforeBackupStartCallablesMap();
        Map<String, Future<Boolean>> futureMap = new HashMap<>(callablesMap.size());
        for (Map.Entry<String, Callable<Boolean>> entry : callablesMap.entrySet()) {
            futureMap.put(entry.getKey(), executor.submit(entry.getValue()));
        }
        boolean shouldStartBackup = true;
        for (Map.Entry<String, Future<Boolean>> entry : futureMap.entrySet()) {
            try {
                if (Boolean.FALSE.equals(entry.getValue().get(TIMEOUT, TimeUnit.SECONDS))) {
                    shouldStartBackup = false;
                    String message = "Not starting a backup due to " + entry.getKey() + " beforeBackupStart() Callable returning false";
                    instance.getLoggingHandler().info(message);
                    break;
                }
            } catch (InterruptedException | ExecutionException | TimeoutException exception) {
                instance.getLoggingHandler().error("Failed to execute a beforeBackupStart() Callable, ignoring", exception);
            }
        }
        return shouldStartBackup;
    }
    
    /**
     * Runs the {@code Callable}s specified by API users to be run after a backup is successfully completed.
     */
    public void backupDone() {
        final String DID_NOT_EXECUTE_SUCCESSFULLY = "A onBackupDone() Runnable returned false";
        final String FAILED_TO_EXECUTE = "Failed to execute an onBackupDone() Runnable, ignoring";
        processRunnables(DriveBackupApi.getOnBackupDoneRunnablesMap(), DID_NOT_EXECUTE_SUCCESSFULLY, FAILED_TO_EXECUTE);
    }
    
    /**
     * Runs the {@code Callable}s specified by API users to be run after an error occurs during a backup.
     */
    public void backupError() {
        final String DID_NOT_EXECUTE_SUCCESSFULLY = "A onBackupError() Runnable returned false";
        final String FAILED_TO_EXECUTE = "Failed to execute an onBackupError() Runnable, ignoring";
        processRunnables(DriveBackupApi.getOnBackupErrorRunnablesMap(), DID_NOT_EXECUTE_SUCCESSFULLY, FAILED_TO_EXECUTE);
    }
    
    private void processRunnables(@NotNull Map<String, Runnable>runnableMap, String didNotExecuteSuccessfully, String failedToExecuteMsg) {
        Map<String, Future<Boolean>> futures = new HashMap<>(runnableMap.size());
        for (Map.Entry<String, Runnable> entry : runnableMap.entrySet()) {
            futures.put(entry.getKey(), executor.submit(entry.getValue(), Boolean.TRUE));
        }
        for (Map.Entry<String, Future<Boolean>> entry : futures.entrySet()) {
            try {
                if (!Boolean.TRUE.equals(entry.getValue().get(TIMEOUT, TimeUnit.SECONDS))) {
                    instance.getLoggingHandler().warn(didNotExecuteSuccessfully);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                instance.getLoggingHandler().error(failedToExecuteMsg, e);
            }
        }
    }
    
}
