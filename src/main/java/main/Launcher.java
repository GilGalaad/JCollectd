package main;

import common.exception.ConfigurationException;
import common.exception.ExecutionException;
import engine.CollectEngine;
import engine.ConfigurationParser;
import engine.config.CollectConfiguration;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class Launcher {

    public static void main(String[] args) {
        // parsing configuration
        CollectConfiguration conf = null;
        try {
            conf = ConfigurationParser.parse(args);
        } catch (ConfigurationException ex) {
            log.fatal(ex);
            System.exit(1);
        }

        // adding shutdown hook for clean shutdown when killed
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);

        // starting engine
        CollectEngine eng = new CollectEngine(conf);
        try {
            eng.run();
        } catch (ExecutionException ex) {
            log.fatal(ex);
            System.exit(1);
        }
    }

}
