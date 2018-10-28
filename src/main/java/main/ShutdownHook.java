package main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShutdownHook extends Thread {

    public static final Logger logger = LogManager.getLogger();

    private final Thread workerThread;

    public ShutdownHook(Thread collectThread) {
        this.workerThread = collectThread;
    }

    @Override
    public void run() {
        logger.info("Received KILL signal, shutdown sequence initiated...");
        workerThread.interrupt();
    }

}
