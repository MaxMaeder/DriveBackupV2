package ratismal.drivebackup.constants;

public enum Initiator {
    PLAYER,
    CONSOLE,
    AUTOMATIC,
    API,
    OTHER;
    
    public boolean isAuto() {
        return AUTOMATIC == this;
    }
}
