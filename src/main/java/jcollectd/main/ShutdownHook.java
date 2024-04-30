package jcollectd.main;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class ShutdownHook extends Thread {

    private final Thread engineThread;

    @Override
    public void run() {
        if (engineThread.isAlive()) {
            log.info("Received KILL signal, shutdown sequence initiated");
            engineThread.interrupt();
        }
    }

}
