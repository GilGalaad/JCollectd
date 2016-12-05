package engine;

import static java.util.logging.Level.INFO;
import static main.JCollectd.logger;

public class ShutdownHook extends Thread {

    Thread collectThread;

    public ShutdownHook(Thread collectThread) {
        this.collectThread = collectThread;
    }

    @Override
    public void run() {
        logger.log(INFO, "Received KILL signal, shutdown sequence initiated...");
        collectThread.interrupt();
        try {
            collectThread.join();
        } catch (InterruptedException ex) {
        }
    }
}
