package ratismal.drivebackup.plugin;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.bstats.bukkit.*;

import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.util.MessageUtil;

public class BstatsMetrics {
    public static final int METRICS_ID = 7537;

    public static void initMetrics() {
        if (ConfigParser.getConfig().advanced.metricsEnabled) {
            try {
                BstatsMetrics metrics = new BstatsMetrics(DriveBackup.getInstance());
                metrics.updateMetrics();

                new MessageUtil("Metrics started").toConsole(true).send();
            } catch (IOException e) {
                new MessageUtil("Metrics failed to start").toConsole(true).send();
            }
        }
    }

    private Metrics metrics;

    public BstatsMetrics(DriveBackup plugin) {
        metrics = new Metrics(plugin, METRICS_ID);
    }

    public void updateMetrics() throws IOException {
        Config config = ConfigParser.getConfig();

        metrics.addCustomChart(new Metrics.SimplePie("automaticBackupType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (config.backupScheduling.enabled) {
                    return "Schedule Based";
                } else if (config.backupStorage.delay != -1) {
                    return "Interval Based";
                } else {
                    return "Not Enabled";
                }
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("backupMethodEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return enabled(
                    config.backupMethods.googleDrive.enabled || 
                    config.backupMethods.oneDrive.enabled || 
                    config.backupMethods.dropbox.enabled ||
                    config.backupMethods.ftp.enabled);
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("googleDriveEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return enabled(config.backupMethods.googleDrive.enabled);
            }
        }));
        
        metrics.addCustomChart(new Metrics.SimplePie("oneDriveEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return enabled(config.backupMethods.oneDrive.enabled);
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("dropboxEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return enabled(config.backupMethods.dropbox.enabled);
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("ftpEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return enabled(config.backupMethods.ftp.enabled);
            }
        }));

        if (config.backupMethods.ftp.enabled) {
            metrics.addCustomChart(new Metrics.SimplePie("sftpEnabledNew", new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return config.backupMethods.ftp.sftp ? "FTP using SSH" : "FTP";
                }
            }));
        }

        metrics.addCustomChart(new Metrics.SimplePie("updateCheckEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return config.advanced.updateCheckEnabled ? "Enabled" : "Disabled";
            }
        }));
    }

    private String enabled(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }
}
