package jcollectd.main;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class ShutdownHook extends Thread {

    private final Thread workerThread;

    @Override
    public void run() {
        log.info("Received KILL signal, shutdown sequence initiated");
        workerThread.interrupt();
    }

}
