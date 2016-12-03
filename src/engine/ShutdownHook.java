package engine;

import static main.JCollectd.logger;

public class ShutdownHook extends Thread {

    Thread collectThread;

    public ShutdownHook(Thread collectThread) {
        this.collectThread = collectThread;
    }

    @Override
    public void run() {
        logger.info("Shutdown sequence initiated...");
        collectThread.interrupt();
        try {
            collectThread.join();
        } catch (InterruptedException ex) {
        }
        logger.info("Shutting down!");
    }
}
