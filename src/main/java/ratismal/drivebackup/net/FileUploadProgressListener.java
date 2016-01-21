package ratismal.drivebackup.net;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;

import java.io.IOException;
import java.text.NumberFormat;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class FileUploadProgressListener implements MediaHttpUploaderProgressListener {

    @Override
    public void progressChanged(MediaHttpUploader uploader) throws IOException {
        switch (uploader.getUploadState()) {
            case INITIATION_STARTED:
                System.out.println("Upload Initiation has started.");
                break;
            case INITIATION_COMPLETE:
                System.out.println("Upload Initiation is Complete.");
                break;
            case MEDIA_IN_PROGRESS:
                System.out.println("Upload is In Progress: "
                        + NumberFormat.getPercentInstance().format(uploader.getProgress()));
                break;
            case MEDIA_COMPLETE:
                System.out.println("Upload is Complete!");
                break;
        }
    }
}