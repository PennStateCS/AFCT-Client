package gui.components;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;

public class FileDownloadProgressBar extends JProgressBar {
    private MonitorInputStreamForProgressBar monitoredInputStream;

    public FileDownloadProgressBar() {
        super();
    }

    /**
     * Constructs an object to monitor the progress of an input stream.
     *
     * @param parentComponent The component triggering the operation
     *                        being monitored.
     * @param in The input stream to be monitored.
     */
    public InputStream getMonitoredInputStream(Component parentComponent, InputStream in) {
        monitoredInputStream = new MonitorInputStreamForProgressBar(parentComponent, this, in);
        return monitoredInputStream;
    }

    private class MonitorForProgressBar {

    }
}
