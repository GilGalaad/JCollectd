package main;

import engine.CollectEngine;
import engine.config.CollectConfiguration;
import engine.exception.ConfigurationException;
import engine.exception.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JCollectd {

    public static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.fatal("Please provide path to configuration file as parameter, aborting");
            System.exit(1);
        }

        // parsing configuration
        CollectConfiguration conf = null;
        try {
            conf = ConfigurationParser.parse(args[0]);
        } catch (ConfigurationException ex) {
            logger.fatal(ex.getMessage());
            System.exit(1);
        }

        // adding shutdown hook for clean shutdown when killed
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);

        // starting engine
        CollectEngine eng = new CollectEngine(conf);
        try {
            eng.run();
        } catch (ExecutionException | RuntimeException ex) {
            logger.fatal(ex);
            System.exit(1);
        }
    }

}
