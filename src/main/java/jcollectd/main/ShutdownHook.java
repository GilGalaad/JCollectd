package jcollectd.main;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ShutdownHook extends Thread {

    private final Thread workerThread;

    public ShutdownHook(Thread collectThread) {
        this.workerThread = collectThread;
    }

    @Override
    public void run() {
        log.info("Received KILL signal, shutdown sequence initiated...");
        workerThread.interrupt();
    }

}
