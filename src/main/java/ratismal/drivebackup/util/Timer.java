package ratismal.drivebackup.util;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class Timer {
    private Date start;
    private Date end;

    public Timer() {

    }

    public void start() {
        start = new Date();
        end = null;
    }

    public boolean end() {
        if (start == null) {
            return false;
        }
        end = new Date();
        return true;
    }

    public String getUploadTimeMessage(File file) {
        DecimalFormat df = new DecimalFormat("#.##");
        double difference = getTime();
        double length = Double.valueOf(df.format(difference / 1000));
        double speed = Double.valueOf(df.format((file.length() / 1024) / length));

        return "File uploaded in " + length + " seconds (" + speed + "KB/s)";
    }

    public double getTime() {
        return end.getTime() - start.getTime();
    }

}
