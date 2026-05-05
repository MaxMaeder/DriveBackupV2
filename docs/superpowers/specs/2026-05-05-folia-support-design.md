# Design Spec - Folia Support for DriveBackupV2

**Date**: 2026-05-05
**Topic**: Add Folia support to DriveBackupV2 Minecraft plugin.

## Goal
Add support for the Folia server software (region-based multithreading) while maintaining compatibility with Spigot and Paper.

## Architecture

### 1. Multi-Platform Abstraction
- Use the `folia-lib` library to abstract scheduling differences between Folia and non-Folia platforms.
- Add `dev.folia-lib:folia-lib` to `pom.xml`.
- Relocate `dev.folia` to `ratismal.drivebackup.lib.folia`.

### 2. Platform Detection
- In `DriveBackup.java` (onEnable), initialize `FoliaLib`.
- Detect the current platform and log a message:
    - "Detected server: Folia"
    - "Detected server: Paper" (if applicable)
    - "Detected server: Spigot/Other"

### 3. Scheduler Refactoring
The following classes will be modified to use `FoliaLib` instead of `BukkitScheduler`:
- `ratismal.drivebackup.plugin.Scheduler`: Main scheduling logic for backups.
- `ratismal.drivebackup.uploaders.Authenticator`: Token refresh scheduling.
- `ratismal.drivebackup.plugin.updater.UpdateChecker`: Update check scheduling.
- `ratismal.drivebackup.util.SchedulerUtil`: Task cancellation logic.
- `ratismal.drivebackup.util.ServerUtil`: Sync method calls.

### 4. Task Management
- Since Folia uses different task objects, `SchedulerUtil` will be updated to handle `WrappedTask` from `folia-lib`.
- Existing task ID (`int`) usage will be replaced or wrapped to support both types.

### 5. Plugin Metadata
- Update `src/main/resources/plugin.yml` to include `folia-supported: true`.

## Implementation Details

### Dependencies
```xml
<dependency>
    <groupId>com.tcoded</groupId>
    <artifactId>FoliaLib</artifactId>
    <version>0.5.1</version>
</dependency>
```

### Repository
```xml
<repository>
    <id>devmart-other</id>
    <url>https://nexus.devmart.objot.com/repository/maven-other/</url>
</repository>
```

## Success Criteria
- Plugin builds successfully.
- Plugin starts on both Spigot and Folia.
- Platform detection message is printed correctly.
- Backups are scheduled and executed correctly on both platforms.
