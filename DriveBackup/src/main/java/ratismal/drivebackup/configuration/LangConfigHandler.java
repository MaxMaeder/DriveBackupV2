package ratismal.drivebackup.configuration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.platforms.DriveBackupInstance;

public final class LangConfigHandler {
    private static final String LANG_FILE_NAME = "lang";
    private static final String FAILED_TO_LOAD = "Failed to load intl, using defaults";
    
    private final DriveBackupInstance instance;
    private ConfigurationObject configurationObject;
    private final PrefixedLogger logger;
    
    public LangConfigHandler(@NotNull DriveBackupInstance instance) {
        this.instance = instance;
        logger = instance.getLoggingHandler().getPrefixedLogger("LangConfigHandler");
        try {
            configurationObject = new ConfigurationObject(logger, instance.getDataDirectory(), LANG_FILE_NAME, instance, getDefaults());
            ConfigurationUtils.loadConfig(configurationObject);
        } catch (ConfigurateException e) {
            logger.error(FAILED_TO_LOAD, e);
        }
    }
    
    @Contract (pure = true)
    public ConfigurationObject getConfig() {
        return configurationObject;
    }
    
    public void generateNewConfig() {
        try {
            configurationObject = ConfigurationUtils.generateNewConfig(configurationObject);
        } catch (ConfigurateException e) {
            instance.getLoggingHandler().error(FAILED_TO_LOAD, e);
        }
    }
    
    @Contract (pure = true)
    private static @NotNull CommentedConfigurationNode getDefaults() throws SerializationException {
        CommentedConfigurationNode defaults = CommentedConfigurationNode.root();
        defaults.node("auto-backups-disabled").set("Auto backups are disabled");
        defaults.node("backup-already-running").set("A backup is already running\n<backup-statu");
        defaults.node("backup-complete").set("Backup complete");
        defaults.node("backup-disabled-inactivity").set("Disabling automatic backups due to inactivity");
        defaults.node("backup-empty-list").set("The backup list is empty");
        String backupFailedAbsolutePath = "Failed to create a backup, path to folder to backup is absolute, expected a relative path"
                                                + "\n"
                                                + "An absolute path can overwrite sensitive files, see the docs for more information"
                                                + "\n"
                                                + "Skipping backup location";
        defaults.node("backup-failed-absolute-path").set(backupFailedAbsolutePath);
        defaults.node("backup-file-upload-complete").set("Upload(s) for file \"<file-name>\" complete");
        defaults.node("backup-file-upload-start").set("Starting upload(s) for file \"<file-name>\"");
        defaults.node("backup-forced").set("Forcing a backup");
        defaults.node("backup-list-blacklist-invalid").set("Blacklist invalid in backup entry <entry>, leaving blank");
        defaults.node("backup-list-format-invalid").set("Format invalid, skipping backup list entry <entry>");
        defaults.node("backup-list-glob-invalid").set("Glob invalid, skipping backup list entry <entry>");
        defaults.node("backup-list-no-dest-specified").set("No path or glob specified, skipping backup list entry <entry>");
        defaults.node("backup-list-path-invalid").set("Path invalid, skipping backup list entry <entry>");
        defaults.node("backup-local-complete").set("Local backup(s) created and compressed");
        String backupLocalFailed = "Failed to create local backup\n"
                                    + "Even if local-keep-count is set to zero, the plugin needs to temporarily create a local backup\n"
                                    + "Skipping backup location";
        defaults.node("backup-local-failed").set(backupLocalFailed);
        defaults.node("backup-local-file-complete").set("Local backup for \"<location>\" created");
        defaults.node("backup-local-file-start").set("Creating local backup for \"<location>\"");
        defaults.node("backup-local-prune-complete").set("Local backup pruning complete");
        defaults.node("backup-local-prune-start").set("Pruning local backups");
        defaults.node("backup-local-start").set("Creating local backup(s) and compressing...");
        defaults.node("backup-method-complete").set("Backup to <gold><upload-method></gold> complete");
        String backupMethodErrorOccurred = "Failed to backup to <upload-method>, verify all\n"
                                            + "credentials are correct or diagnose the problem with\n"
                                            + "<gold><click:run_command:'<diagnose-command>'><diagnose-command></click></gold>";
        defaults.node("backup-method-error-occurred").set(backupMethodErrorOccurred);
        String backupMethodLimitReached = "There are <file-count> file(s) for the current\n"
                                            + "backup location in <upload-method> which exceeds the limit of <file-limit>,\n"
                                            + "deleting";
        defaults.node("backup-method-limit-reached").set(backupMethodLimitReached);
        String backupMethodNotAuth = "Skipping backup to <upload-method>, failed to authenticate\n"
                                        + "Please verify all credentials are correct in the <gold>config.yml<gold>";
        defaults.node("backup-method-not-auth").set(backupMethodNotAuth);
        String backupMethodNotAuthAuthenticator = "Skipping backup to <upload-method>, failed to authenticate\n"
                                                    + "Please try re-linking your account, run <gold><click:run_command:'<link-command>'><link-command></click></gold>";
        defaults.node("backup-method-not-auth-authenticator").set(backupMethodNotAuthAuthenticator);
        String backupMethodNotLinked = "Skipping backup to <upload-method>, account not yet linked\n"
                                        + "To link your account, run <gold><click:run_command:'<link-command>'><link-command></click></gold>";
        defaults.node("backup-method-not-linked").set(backupMethodNotLinked);
        defaults.node("backup-method-prune-failed").set("Failed to delete backups exceeding limit");
        String backupMethodSharedDrivePruneFailed = "Failed to delete backups exceeding limit\n"
                                                    + "Try asking the owner of the shared drive to elevate your account's permissions, or set keep-count to 0 to disable deleting backups";
        defaults.node("backup-method-shared-drive-prune-failed").set(backupMethodSharedDrivePruneFailed);
        defaults.node("backup-method-upload-failed").set("Failed to upload");
        defaults.node("backup-method-uploading").set("Uploading file to <upload-method>");
        defaults.node("backup-no-methods").set("No backup method is enabled");
        defaults.node("backup-schedule-day-empty").set("Day of week list empty, skipping schedule entry <entry>");
        defaults.node("backup-schedule-day-invalid").set("Day of week invalid, skipping day of week \"<day>\"");
        defaults.node("backup-schedule-days-invalid").set("Days list invalid, skipping schedule entry <entry>");
        defaults.node("backup-schedule-empty").set("Backup schedule empty, disabling schedule-based backups");
        defaults.node("backup-schedule-time-invalid").set("Time invalid, skipping schedule entry <entry>");
        defaults.node("backup-start").set("Creating backups, the server may lag for a little while...");
        defaults.node("backup-status-compressing").set("Compressing backup set \"<set-name>\", set <set-num> of <set-count>");
        defaults.node("backup-status-not-running").set("No backups are running");
        defaults.node("backup-status-uploading").set("Uploading backup set \"<set-name>\", set <set-num> of <set-count>");
        defaults.node("backup-upload-complete").set("Backup(s) uploaded");
        defaults.node("backup-upload-start").set("Uploading backup(s)...");
        defaults.node("backups-interval-scheduled").set("Scheduling a backup to run every <delay> minutes");
        defaults.node("backups-scheduled").set("Scheduling a backup to run at <time> every <days>");
        defaults.node("brief-backup-list").set("DriveBackupV2 will currently back up <list>");
        defaults.node("brief-backup-list-empty").set("nothing");
        defaults.node("brief-backup-list-external-backups").set("some external backups");
        defaults.node("brief-backup-list-help").set("Want to back up something else? See\n"
                                                    + "<gold><click:open_url:https://bit.ly/3xoHRAs>http://bit.ly/3xoHRAs</click></gold>");
        defaults.node("config-loaded").set("Config loaded!");
        defaults.node("config-reloaded").set("Config reloaded!");
        defaults.node("connection-error").set("Failed to connect to <domain>, check your network connection\n"
                                                + "and server firewall");
        defaults.node("date-format-invalid").set("Date format timezone not valid, using UTC");
        defaults.node("debug-log-created").set("Debug URL: <url>");
        defaults.node("debug-log-creating").set("Generating Debug Log");
        defaults.node("default-google-drive-name").set("My Drive");
        defaults.node("drivebackup-command-header").set("<gold>|====== <dark_red>DriveBackupV2</dark_red> ======|</gold>");
        String driveBackupDocsCommand = "<header>\n"
                                        + "Need help? Check out these helpful resources!\n"
                                        + "Wiki: <gold><click:open_url:https://bit.ly/3dDdmwK>http://bit.ly/3dDdmwK</click></gold>\n"
                                        + "Discord: <gold><click:open_url:https://bit.ly/3f4VuuT>http://bit.ly/3f4VuuT</click></gold>";
        defaults.node("drivebackup-docs-command").set(driveBackupDocsCommand);
        String driveBackupHelpCommand = "<header>\n"
                                        + "<gold><click:run_command:/drivebackup>/drivebackup</click></gold> - Displays this menu\n"
                                        + "<gold><click:run_command:/drivebackup help>/drivebackup help</click></gold> - Displays help resources\n"
                                        + "<gold><click:run_command:/drivebackup commands>/drivebackup commands</click></gold> - Displays this command help page\n"
                                        + "<gold><click:run_command:/drivebackup v>/drivebackup [v|version]</click></gold> - Displays the plugin version\n"
                                        + "<gold><click:run_command:/drivebackup linkaccount googledrive>/drivebackup [link|linkaccount] googledrive</click></gold> - Links your Google Drive account for backups\n"
                                        + "<gold><click:run_command:/drivebackup linkaccount onedrive>/drivebackup [link|linkaccount] onedrive</click></gold> - Links your OneDrive account for backups\n"
                                        + "<gold><click:run_command:/drivebackup linkaccount dropbox>/drivebackup [link|linkaccount] dropbox</click></gold> - Links your Dropbox account for backups\n"
                                        + "<gold><click:run_command:/drivebackup linkaccount googledrive>/drivebackup [unlink|unlinkaccount] googledrive</click></gold> - Unlinks your Google Drive account and disables method\n"
                                        + "<gold><click:run_command:/drivebackup linkaccount onedrive>/drivebackup [unlink|unlinkaccount] onedrive</click></gold> - Unlinks your OneDrive account and disables method\n"
                                        + "<gold><click:run_command:/drivebackup linkaccount dropbox>/drivebackup [unlink|unlinkaccount] dropbox</click></gold> - Unlinks your Dropbox account and disables method\n"
                                        + "<gold><click:run_command:/drivebackup reloadconfig>/drivebackup reloadconfig</click></gold> - Reloads the config.yml\n"
                                        + "<gold><click:run_command:/drivebackup nextbackup>/drivebackup nextbackup</click></gold> - Gets the time/date of the next auto backup\n"
                                        + "<gold><click:run_command:/drivebackup status>/drivebackup status</click></gold> - Gets the status of the running backup\n"
                                        + "<gold><click:run_command:/drivebackup backup>/drivebackup backup</click></gold> - Manually initiates a backup\n"
                                        + "<gold><click:run_command:/drivebackup test ftp>/drivebackup test ftp</click></gold> - Tests the connection to the (S)FTP server\n"
                                        + "<gold><click:run_command:/drivebackup test googledrive>/drivebackup test googledrive</click></gold> - Tests the connection to Google Drive\n"
                                        + "<gold><click:run_command:/drivebackup test onedrive>/drivebackup test onedrive</click></gold> - Tests the connection to OneDrive\n"
                                        + "<gold><click:run_command:/drivebackup test dropbox>/drivebackup test dropbox</click></gold> - Tests the connection to Dropbox\n"
                                        + "<gold><click:run_command:/drivebackup update>/drivebackup update</click></gold> - Updates the plugin if there is a newer version";
        defaults.node("drivebackup-help-command").set(driveBackupHelpCommand);
        String driveBackupVersionCommand = "<header>\n"
                                            + "Plugin version: <gold><plugin-version></gold>\n"
                                            + "Java version: <gold><java-version></gold>\n"
                                            + "Server software: <gold><server-software></gold>\n"
                                            + "Server software version: <gold><server-version></gold>";
        defaults.node("drivebackup-version-command").set(driveBackupVersionCommand);
        defaults.node("drivebackup-version-update").set("<gold>Plugin update available!");
        defaults.node("external-backup-base-dir-invalid").set("Path to base directory key invalid in\n"
                                                                + "external backup entry <entry>, leaving blank");
        defaults.node("external-backup-format-invalid").set("Format invalid, skipping external backup entry <entry>");
        defaults.node("external-backup-host-port-invalid").set("Hostname/port invalid, skipping external backup entry <entry>");
        defaults.node("external-backup-list-blacklist-invalid").set("Blacklist invalid in external backup\n"
                                                                    + "backup list entry <entry-backup>, leaving blank");
        defaults.node("external-backup-list-invalid").set("Backup list invalid, skipping external backup entry <entry>");
        defaults.node("external-backup-list-path-invalid").set("Path invalid, skipping external backup backup list entry <entry-backup>");
        defaults.node("external-backup-passphrase-invalid").set("Passphrase invalid in external backup entry <entry>, leaving blank");
        defaults.node("external-backup-public-key-invalid").set("Path to public key invalid in external backup entry <entry>, leaving blank");
        defaults.node("external-backup-type-invalid").set("Backup type invalid, skipping external backup entry <entry>");
        defaults.node("external-backup-user-pass-invalid").set("Username/password invalid, skipping external backup entry <entry>");
        defaults.node("external-database-list-blacklist-invalid").set("Blacklist invalid in external backup\n"
                                                                        + "database list entry <entry-backup>, leaving blank");
        defaults.node("external-database-list-invalid").set("Database list invalid, skipping external backup entry <entry>");
        defaults.node("external-database-list-name-invalid").set("Name invalid, skipping external backup database list entry <entry-database>");
        defaults.node("external-database-ssl-invalid").set("SSL enabled setting invalid in external backup entry <entry>, not using SSL");
        defaults.node("external-ftp-backup-blacklisted").set("Didn't include <blacklisted-files> file(s) in\n"
                                                                + "the backup from the external (S)FTP server, as they are blacklisted by\n"
                                                                + "\"<glob-pattern>\"");
        defaults.node("external-ftp-backup-complete").set("Files from a (S)FTP server (<socket-addr>) were\n"
                                                            + "successfully included in the backup");
        defaults.node("external-ftp-backup-failed").set("Failed to include files from a (S)FTP server\n"
                                                        + "(<socket-addr>) in the backup, please check the server credentials in the\n"
                                                        + "<gold>config.yml</gold>");
        defaults.node("external-ftp-backup-start").set("Downloading files from a (S)FTP server (<socket-addr>) to include in backup");
        defaults.node("external-mysql-backup-blacklisted").set("Didn't include table \"<blacklist-entry>\"\n"
                                                                + "in the backup, as it is blacklisted");
        defaults.node("external-mysql-backup-complete").set("Databases from a MySQL server (<socket-addr>)\n"
                                                            + "were successfully included in the backup");
        defaults.node("external-mysql-backup-failed").set("Failed to include databases from a MySQL server\n"
                                                            + "(<socket-addr>) in the backup, please check the server credentials in the\n"
                                                            + "<gold>config.yml</gold>");
        defaults.node("external-mysql-backup-start").set("Downloading databases from a MySQL server\n"
                                                            + "(<socket-addr>) to include in backup");
        defaults.node("file-upload-message").set("File uploaded in <length> seconds (<speed>KB/s)");
        defaults.node("ftp-method-passphrase-invalid").set("Passphrase invalid for FTP backup method, leaving blank");
        defaults.node("ftp-method-pubic-key-invalid").set("Path to public key invalid for FTP backup method, leaving blank");
        defaults.node("google-pick-shared-drive").set("You have access one or more Shared Drives, if you'd\n"
                                                        + "like to use one of them either select it or reply with it's number in the\n"
                                                        + "chat.");
        defaults.node("google-shared-drive-option").set("<bold>[<drive-num>]</bold>\n"
                                                        + "<gold><hover:show_text:Select\n"
                                                        + "Drive><click:run_command:'<select-command>'><drive-name></click></hover></gold>");
        defaults.node("invalid-backup-delay").set("Inputted backup delay invalid, using default");
        defaults.node("keep-count-invalid").set("Keep count invalid, using default");
        defaults.node("link-account-code").set("To link your <provider> account, go to\n"
                                                + "<gold><click:open_url:'<link-url>'><link-url></click></gold> and enter\n"
                                                + "<gold><click:copy_to_clipboard:'<link-code>'><link-code></click></gold>");
        defaults.node("link-provider-complete").set("Your <provider> account is linked!");
        defaults.node("link-provider-failed").set("Failed to link your <provider> account, please try again");
        defaults.node("list-delimiter").set(", ");
        defaults.node("list-last-delimiter").set(" and ");
        defaults.node("local-backup-backlisted").set("Didn't include <blacklisted-files-count> file(s) in\n"
                                                        + "the backup, as they are blacklisted by \"<glob-pattern>\"");
        String localBackupDateFormatInvalid = "Unable to parse date format of stored backup \"<file-name>\", this can be due to the date format being updated in the config.yml\n"
                                                + "Backup will be the first deleted";
        defaults.node("local-backup-date-format-invalid").set(localBackupDateFormatInvalid);
        String localBackupFailedPermissions = "Failed to create local backup, plugin does not have permission to write to the required file system location\n"
                                                + "Even if local-keep-count is set to zero, the plugin needs to temporarily create a local backup\n"
                                                + "Skipping backup location";
        defaults.node("local-backup-failed-permissions").set(localBackupFailedPermissions);
        defaults.node("local-backup-failed-to-delete").set("Local backup deletion failed");
        defaults.node("local-backup-failed-to-include").set("Failed to include \"<file-path>\" in the\n"
                                                            + "backup, is it locked? Do you have permission to access it?");
        defaults.node("local-backup-file-deleted").set("Deleted local backup \"<local-backup-name>\"");
        defaults.node("local-backup-file-failed-to-delete").set("Failed to delete local backup \"<local-backup-name>\"");
        defaults.node("local-backup-in-backup-folder").set("Didn't include <files-in-backup-folder-count>\n"
                                                            + "file(s) in the backup, as they are in the folder used for backups");
        defaults.node("local-backup-limit-not-reached").set("Local backup limit not reached, skipping pruning");
        defaults.node("local-backup-limit-reached").set("There are <backup-count> file(s) which exceeds the\n"
                                                        + "local limit of <backup-limit>, deleting oldest");
        defaults.node("local-backup-no-limit").set("Local backup limit is set to 0, skipping pruning");
        defaults.node("local-backup-pruning-complete").set("Local backup pruning complete for \"<location>\"");
        defaults.node("local-backup-pruning-start").set("Pruning local backups for \"<location>\"");
        defaults.node("local-keep-count-invalid").set("Inputted local keep count invalid, using default");
        defaults.node("metrics-error").set("Metrics failed to start");
        defaults.node("metrics-started").set("Metrics started");
        defaults.node("next-backup").set("The next backup is in %TIME minutes");
        defaults.node("next-schedule-backup").set("The next backup is at %DATE");
        defaults.node("next-schedule-backup-format").set("h:mm a EEE, MMM d O");
        defaults.node("no-perm").set("You don't have permission to do this!");
        defaults.node("player-join-backup-enable").set("Enabling automatic backups");
        String playerJoinBackupFailed = "<red>The last backup failed!<red>\n"
                                        + "Check the console for more info";
        defaults.node("player-join-backup-failed").set(playerJoinBackupFailed);
        String playerJoinUpdateAvailable = "An update is available, get it here: <gold><click:open_url:https://bit.ly/2M14uVD>http://bit.ly/2M14uVD</click></gold>\n"
                                           + "or by running <gold><click:run_command:/drivebackup update>/drivebackup update</click></gold>";
        defaults.node("player-join-update-available").set(playerJoinUpdateAvailable);
        defaults.node("plugin-stop").set("Stopping plugin!");
        defaults.node("test-file-creation-failed").set("Test file creation failed, please try again");
        defaults.node("test-method-begin").set("Beginning the test on <upload-method>");
        defaults.node("test-method-failed").set("The <upload-method> test was unsuccessful, please check the\n"
                                                + "<gold>config.yml</gold>");
        defaults.node("test-method-invalid").set("\"<specified-method>\" isn't a valid backup method");
        defaults.node("test-method-not-enabled").set("<upload-method> backups are disabled, you can enable\n"
                                                        + "them in the <gold>config.yml</gold>");
        defaults.node("test-method-not-specified").set("Please specify a backup method to test");
        defaults.node("test-method-successful").set("The <upload-method> test was successful");
        defaults.node("thread-priority-too-high").set("Inputted thread priority more than maximum, using maximum");
        defaults.node("thread-priority-too-low").set("Inputted thread priority less than minimum, using minimum");
        defaults.node("unlink-provider-complete").set("Your <provider> account is unlinked!");
        defaults.node("unlink-provider-failed").set("Failed to unlink your <provider> account, please try again");
        defaults.node("update-checker-failed").set("There was an issue attempting to check for the latest DriveBackupV2 release");
        String updateCheckerNewRelease = "DriveBackup version <latest-version> has been released, You are currently running version <current-version>\n"
                                         + "Update at: <gold><click:open_url:https://bit.ly/2VGtF7L>http://bit.ly/2VGtF7L</click></gold>\" or with <gold><click:run_command:/drivebackup update>/drivebackup update</click></gold>";
        defaults.node("update-checker-new-release").set(updateCheckerNewRelease);
        defaults.node("update-checker-started").set("Checking for updates...");
        String updateCheckerUnsupportedRelease = "You are running an unsupported release!\n"
                                                    + "The recommended release is <latest-version>, and you are running <current-version>\n"
                                                    + "If the plugin has just recently updated, please ignore this message";
        defaults.node("update-checker-unsupported-release").set(updateCheckerUnsupportedRelease);
        defaults.node("updater-fetch-failed").set("Unable to fetch latest version of DriveBackupV2");
        defaults.node("updater-no-updates").set("You are using the latest version of DriveBackupV2!");
        defaults.node("updater-start").set("Attempting to download the latest version of DriveBackupV2");
        defaults.node("updater-successful").set("Successfully updated plugin! Please restart your server in\n"
                                                + "order for changes to take effect");
        defaults.node("updater-update-failed").set("Plugin update failed, see console for more info");
        defaults.node("upload-error-check").set("Checking for upload errors...");
        defaults.node("upload-no-errors").set("No upload errors found");
        defaults.node("zip-compression-too-high").set("Inputted zip compression more than maximum, using maximum");
        defaults.node("zip-compression-too-low").set("Inputted zip compression less than minimum, using minimum");
        
        return defaults;
    }
    
    public void save() throws ConfigurateException {
        ConfigurationUtils.saveConfig(configurationObject);
    }
}
