# Folia Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Folia support to DriveBackupV2 while maintaining Spigot/Paper compatibility using `folia-lib`.

**Architecture:** Use `folia-lib` as a cross-platform scheduler abstraction. Initialize it in the main class and refactor all scheduler calls to use it. Detect platform on startup and log a message.

**Tech Stack:** Java 8, Maven, folia-lib (0.5.1), Bukkit API.

---

### Task 1: Update Maven Configuration

**Files:**
- Modify: `DriveBackupV2/DriveBackup/pom.xml`

- [ ] **Step 1: Add Repository and Dependency**
Add the devmart repository and FoliaLib dependency.

```xml
<repositories>
    ...
    <repository>
        <id>devmart-other</id>
        <url>https://nexus.devmart.objot.com/repository/maven-other/</url>
    </repository>
</repositories>

<dependencies>
    ...
    <dependency>
        <groupId>com.tcoded</groupId>
        <artifactId>FoliaLib</artifactId>
        <version>0.5.1</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Add Relocation to Shade Plugin**
Ensure FoliaLib is relocated to avoid conflicts.

```xml
<relocation>
    <pattern>com.tcoded.folialib</pattern>
    <shadedPattern>ratismal.drivebackup.lib.folialib</shadedPattern>
</relocation>
```

- [ ] **Step 3: Commit**
```bash
git add DriveBackup/pom.xml
git commit -m "build: add folia-lib dependency and relocation"
```

### Task 2: Initialize FoliaLib and Detect Platform

**Files:**
- Modify: `DriveBackupV2/DriveBackup/src/main/java/ratismal/drivebackup/plugin/DriveBackup.java`

- [ ] **Step 1: Add FoliaLib field and initialization**
```java
import com.tcoded.folialib.FoliaLib;
...
public class DriveBackup extends JavaPlugin {
    private static FoliaLib foliaLib;
    ...
    @Override
    public void onEnable() {
        plugin = this;
        foliaLib = new FoliaLib(this);
        ...
        String platform = "Spigot/Other";
        if (foliaLib.isFolia()) {
            platform = "Folia";
        } else if (foliaLib.isPaper()) {
            platform = "Paper";
        }
        getLogger().info("Detected server: " + platform);
        ...
    }
    
    public static FoliaLib getFoliaLib() {
        return foliaLib;
    }
}
```

- [ ] **Step 2: Commit**
```bash
git add DriveBackup/src/main/java/ratismal/drivebackup/plugin/DriveBackup.java
git commit -m "feat: initialize FoliaLib and detect platform on startup"
```

### Task 3: Refactor Scheduler.java

**Files:**
- Modify: `DriveBackupV2/DriveBackup/src/main/java/ratismal/drivebackup/plugin/Scheduler.java`

- [ ] **Step 1: Replace BukkitScheduler with FoliaLib scheduler**
Replace task IDs with `WrappedTask` if necessary, or just use FoliaLib's simplified methods.
Note: Since `Scheduler` uses static methods, we'll need to use `DriveBackup.getFoliaLib()`.

```java
import com.tcoded.folialib.wrapper.task.WrappedTask;
...
private static List<WrappedTask> backupTasks = new ArrayList<>();
private static WrappedTask scheduleDriftTask = null;
...
// In startBackupThread()
if (scheduleDriftTask != null) {
    scheduleDriftTask.cancel();
}
...
backupTasks.add(DriveBackup.getFoliaLib().getScheduler().runTimerAsync(
    new UploadThread(),
    SchedulerUtil.sToTicks(ChronoUnit.SECONDS.between(now, startingOccurrence)),
    SchedulerUtil.sToTicks(ChronoUnit.SECONDS.between(previousOccurrence, nextOccurrence))
));
```

- [ ] **Step 2: Commit**
```bash
git add DriveBackup/src/main/java/ratismal/drivebackup/plugin/Scheduler.java
git commit -m "refactor: use FoliaLib for backup scheduling"
```

### Task 4: Refactor Utility and Other Classes

**Files:**
- Modify: `DriveBackupV2/DriveBackup/src/main/java/ratismal/drivebackup/uploaders/Authenticator.java`
- Modify: `DriveBackupV2/DriveBackup/src/main/java/ratismal/drivebackup/plugin/updater/UpdateChecker.java`
- Modify: `DriveBackupV2/DriveBackup/src/main/java/ratismal/drivebackup/util/SchedulerUtil.java`
- Modify: `DriveBackupV2/DriveBackup/src/main/java/ratismal/drivebackup/util/ServerUtil.java`

- [ ] **Step 1: Refactor Authenticator.java**
Replace `taskId` (int) with `WrappedTask`.

- [ ] **Step 2: Refactor UpdateChecker.java**
Replace `runTaskTimerAsynchronously` with FoliaLib equivalent.

- [ ] **Step 3: Refactor SchedulerUtil.java**
Update `cancelTasks` to handle `WrappedTask`.

- [ ] **Step 4: Refactor ServerUtil.java**
Update sync method call.

- [ ] **Step 5: Commit**
```bash
git add DriveBackup/src/main/java/ratismal/drivebackup/uploaders/Authenticator.java \
        DriveBackup/src/main/java/ratismal/drivebackup/plugin/updater/UpdateChecker.java \
        DriveBackup/src/main/java/ratismal/drivebackup/util/SchedulerUtil.java \
        DriveBackup/src/main/java/ratismal/drivebackup/util/ServerUtil.java
git commit -m "refactor: update other classes to use FoliaLib"
```

### Task 5: Update Plugin Metadata

**Files:**
- Modify: `DriveBackupV2/DriveBackup/src/main/resources/plugin.yml`

- [ ] **Step 1: Add folia-supported flag**
```yaml
folia-supported: true
```

- [ ] **Step 2: Commit**
```bash
git add DriveBackup/src/main/resources/plugin.yml
git commit -m "feat: add folia-supported: true to plugin.yml"
```

### Task 6: Final Verification and Push

- [ ] **Step 1: Build the project**
Run: `cd DriveBackup && mvn clean package`
Expected: SUCCESS

- [ ] **Step 2: Push to fork**
Run: `git push origin folia`

- [ ] **Step 3: Create Pull Request**
Run: `gh pr create --title "feat: add Folia support" --body "Added Folia support using folia-lib."`
