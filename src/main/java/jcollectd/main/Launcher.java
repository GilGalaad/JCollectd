package jcollectd.main;

import jcollectd.common.exception.ConfigurationException;
import jcollectd.common.exception.ExceptionUtils;
import jcollectd.engine.CollectEngine;
import jcollectd.engine.ConfigurationParser;
import jcollectd.engine.config.CollectConfiguration;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class Launcher {

    public static void main(String[] args) {
        // parsing configuration
        CollectConfiguration conf = null;
        try {
            conf = ConfigurationParser.parse(args);
        } catch (ConfigurationException ex) {
            log.fatal(ExceptionUtils.getCanonicalFormWithStackTrace(ex));
            System.exit(1);
        }

        // adding shutdown hook for clean shutdown when killed
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);

        // starting engine
        try {
            CollectEngine eng = new CollectEngine(conf);
            eng.run();
        } catch (Exception ex) {
            log.fatal(ExceptionUtils.getCanonicalFormWithStackTrace(ex));
            System.exit(1);
        }
    }

}
