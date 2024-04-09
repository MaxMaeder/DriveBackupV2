package ratismal.drivebackup.plugin;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

public class BstatsMetrics {
    private static final int METRICS_ID = 7537;

    public static void initMetrics() {
        if (ConfigParser.getConfig().advanced.metricsEnabled) {
            try {
                BstatsMetrics metrics = new BstatsMetrics(DriveBackup.getInstance());
                metrics.updateMetrics();
                MessageUtil.Builder().mmText(intl("metrics-started")).toConsole(true).send();
            } catch (IOException e) {
                MessageUtil.Builder().mmText(intl("metrics-error")).toConsole(true).send();
            }
        }
    }

    private final Metrics metrics;

    public BstatsMetrics(DriveBackup plugin) {
        metrics = new Metrics(plugin, METRICS_ID);
    }

    public void updateMetrics() throws IOException {
        Config config = ConfigParser.getConfig();
        metrics.addCustomChart(new SimplePie("automaticBackupType", () -> {
            if (config.backupScheduling.enabled) {
                return "Schedule Based";
            } else if (config.backupStorage.delay != -1) {
                return "Interval Based";
            } else {
                return "Not Enabled";
            }
        }));
        metrics.addCustomChart(new SimplePie("backupMethodEnabled", () -> enabled(
            config.backupMethods.googleDrive.enabled ||
            config.backupMethods.oneDrive.enabled ||
            config.backupMethods.dropbox.enabled ||
            config.backupMethods.webdav.enabled ||
            config.backupMethods.nextcloud.enabled ||
            config.backupMethods.ftp.enabled)));
        metrics.addCustomChart(new SimplePie("googleDriveEnabled", () -> enabled(config.backupMethods.googleDrive.enabled)));
        metrics.addCustomChart(new SimplePie("oneDriveEnabled", () -> enabled(config.backupMethods.oneDrive.enabled)));
        metrics.addCustomChart(new SimplePie("dropboxEnabled", () -> enabled(config.backupMethods.dropbox.enabled)));
        metrics.addCustomChart(new SimplePie("webdavEnabled", () -> enabled(config.backupMethods.webdav.enabled)));
        metrics.addCustomChart(new SimplePie("nextcloudEnabled", () -> enabled(config.backupMethods.nextcloud.enabled)));
        metrics.addCustomChart(new SimplePie("ftpEnabled", () -> enabled(config.backupMethods.ftp.enabled)));
        if (config.backupMethods.ftp.enabled) {
            metrics.addCustomChart(new SimplePie("sftpEnabledNew", () -> {
                if (config.backupMethods.ftp.sftp) {
                    return "FTP using SSH";
                }
                return "FTP";
            }));
        }
        metrics.addCustomChart(new SimplePie("updateCheckEnabled", () -> {
            if (config.advanced.updateCheckEnabled) {
                return "Enabled";
            }
            return "Disabled";
        }));
    }

    @NotNull
    @Contract (pure = true)
    private String enabled(boolean enabled) {
        if (enabled) {
            return "Enabled";
        }
        return "Disabled";
    }
}
