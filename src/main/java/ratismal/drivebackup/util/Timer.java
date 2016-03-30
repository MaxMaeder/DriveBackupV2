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

    /**
     * Starts the timer
     */
    public void start() {
        start = new Date();
        end = null;
    }

    /**
     * Ends the timer
     * @return false if timer was never started
     */
    public boolean end() {
        if (start == null) {
            return false;
        }
        end = new Date();
        return true;
    }

    /**
     * Construct an upload message
     * @param file file that was uploaded
     * @return message
     */
    public String getUploadTimeMessage(File file) {
        DecimalFormat df = new DecimalFormat("#.##");
        double difference = getTime();
        double length = Double.valueOf(df.format(difference / 1000));
        double speed = Double.valueOf(df.format((file.length() / 1024) / length));

        return "File uploaded in " + length + " seconds (" + speed + "KB/s)";
    }

    /**
     * Calculates the time
     * @return Calculated time
     */
    public double getTime() {
        return end.getTime() - start.getTime();
    }

}
