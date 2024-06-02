package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ChunkedFileInputStream extends InputStream {
    private FileInputStream input;
    private final int chunksize;
    private int current_chunk;
    private int position;

    public ChunkedFileInputStream(int chunksize, FileInputStream input) {
        this.chunksize = chunksize;
        this.input = input;
        current_chunk = 0;
        position = 0;
    }

    @Override
    public int read() throws IOException {
        if (position >= chunksize) {
            return -1;
        }
        int data = input.read();
        if (0 <= data) {
            position++;
        }
        return data;
    }

    @Override
    public int read(byte @NotNull [] bytes, int off, int len) throws IOException {
        len = Integer.min(available(), len);
        int bytes2 = input.read(bytes, off, len);
        position += bytes2;
        return bytes2;
    }

    @Override
    public int available() throws IOException {
        return Integer.min(input.available(), Integer.max(0, chunksize - position));
    }

    public boolean hasNext() {
        try {
            return 0 < input.available();
        } catch (IOException ex) {
            return false;
        }
    }

    public boolean next() {
        if (!hasNext()) {
            return false;
        }
        current_chunk++;
        position = 0;
        return true;
    }

    @Contract (pure = true)
    public int getCurrentChunk() {
        return current_chunk;
    }

    // Needs to be long, or you'll get trouble with files larger than 2G.
    @Contract (pure = true)
    public long getCurrentOffset() {
        return (long)current_chunk * chunksize;
    }

    @Contract (mutates = "this")
    @Override
    protected void finalize() {
        input = null;
    }
}
