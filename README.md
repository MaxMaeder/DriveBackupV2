# DriveBackup

*This is an updated version of the unmantained Bukkit plugin that's available [here](https://dev.bukkit.org/projects/drivebackup). Due to changes in the Google Drive/OneDrive APIs, that plugin no longer fully works. More details [here]().*

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

## Setup:
First, download the plugin [here](https://github.com/MaxMaeder/DriveBackup/releases) and copy it to the `plugins` folder on your server. Then, reload your server (using `/reload`). Finally, follow the instructions below for the backup method of your choice.

### FTP
Fill in the `config.yml` with the FTP server login information.

### Google Drive
Simply run `/drivebackup linkaccount googledrive` and follow the on-screen instructions.

### OneDrive
Simply run `/drivebackup linkaccount onedrive` and follow the on-screen instructions.

## Usage:
By default, the plugin initiates a backup every hour. You can configure how often and what folders to backup in the `config.yml`.

You can also manually initiate a backup by running `/drivebackup backup`

## Permissions
OPs have all permissions by default

### `drivebackup.reloadConfig`
Allows user to reload the plugin's `config.yml`
Recommended permission holders: server owner, admins

### `drivebackup.linkAccounts`
Allows user to link their Google Drive and/or OneDrive account to the plugin for use as the backup destination
Recommended permission holders: server owner

### `drivebackup.backup`
Allows user to manulally initiate a backup
Recommended permission holders: server owner, admins

## How is this better than the original plugin?
- Fixed issue preventing users from authenticating with Google Drive, making it impossible to upload to it
- Now uploads backups *asyncronously*, previously uploading a large backup would cause players to get kicked from the server
- Made it **much** easier to authenticate with Google Drive/OneDrive
  - Now, instead of running a companion program and copying the outputted files to the server, users now just have to run a single command
- Fixed issue causing backups in OneDrive past the number to keep to not get deleted
- Commands now have tab suggestions
- Now requests very limited access from Google Drive and OneDrive
  - This means if there ever is a glitch in the plugin, it can't delete your sensitive Google Drive and/or OneDrive data
  - Previously, the plugin had almost free reign over users' Google Drive and/or OneDrive data

## Privacy Policy
Since we need to access your Google Drive and/or OneDrive data in order to back up your world, we are required to provide a Privacy Policy.
 
All of the data this plugin uploads and downloads from your Google Drive and/or OneDrive stays on your Minecraft server, so we never have access to it. This plugin physically cannot access any data in your Google Drive and/or OneDrive that is not related to backing up your Minecraft world backups. But don't take our word for it, all of this plugin's source code is available here!
