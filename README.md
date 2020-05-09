# DriveBackup

*This is an updated version of the unmantained Bukkit plugin that's available [here](https://dev.bukkit.org/projects/drivebackup). Due to changes with the Google Drive API, that plugin no longer works.*

## What is this?
Have you ever lost your world before?

Maybe your hard drive died. Maybe you used a web hosting service that terminated without warning. Maybe you just accidentally deleted it.

DriveBackup is a plugin that aims to provide an extra layer of security to your data.

## What does it do?
- Creates and uploads backups to Google Drive, OneDrive, or a remote FTP server
  - When choosing between Google Drive and OneDrive, keep in mind that OneDrive has a 10GB upload limit per file and uploads substantially slower than Google Drive.
- Deletes backups locally and remotely if they exceed a specified amount
- Can create a backup out of any folder in the root directory
  - If you already have a utility creating certain backups (eg. world) you can choose not to create that kind of backup and instead upload the existing ones

## Usage:
First, download the plugin [here](https://github.com/MaxMaeder/DriveBackup/releases) and copy it to the `plugins` folder on your server. Then, reload your server (using `/reload`). Finally, follow the instructions below for the backup method of your choice.

### FTP
Simply need to fill in the `config.yml` with the FTP server login information.

### Google Drive & OneDrive
*You need to have the [Java SE Runtime Environment](oracle.com/java/technologies/javase-jre8-downloads.html) installed before proceeding. If you play Minecraft on this computer, you already have it installed*

Download and extract the companion program available [here](https://github.com/MaxMaeder/DriveBackup/releases). Then, run the `.bat` file and follow the on-screen instuctions. Once you've authenticated, copy the `StoredCredential` or `OneDriveCredential.json` into the `plugins/DriveBackup` folder on your server.
 
## Privacy Policy
Since we need to access your Google Drive data in order to back up your world, we are required to provide a Privacy Policy.
 
All of the data this plugin uploads and downloads from your Google Drive stays on your Minecraft server, so we never have access to it. This plugin will not access any data in your Google Drive that is not related to backing up your Minecraft world backups. But don't take our word for it, all of this plugin's source code is available here!
