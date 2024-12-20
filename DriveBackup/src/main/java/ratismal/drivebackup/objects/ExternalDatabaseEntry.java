package ratismal.drivebackup.objects;

public final class ExternalDatabaseEntry {
    
    public final String name;
    public final String[] blacklist;
    
    public ExternalDatabaseEntry(String name, String[] blacklist) {
        this.name = name;
        this.blacklist = blacklist;
    }
    
}
