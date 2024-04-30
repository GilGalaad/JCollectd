package jcollectd.main;

import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.exception.CollectException;
import jcollectd.common.exception.ConfigurationException;
import jcollectd.engine.CollectEngine;
import jcollectd.engine.ConfigurationParser;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;

@Log4j2
public class Launcher {

    public static void main(String[] args) {
        // printing banner
        printBanner();

        // parsing configuration
        AppConfig config = null;
        try {
            config = ConfigurationParser.parse(args);
        } catch (ConfigurationException ex) {
            log.error(ExceptionUtils.getCanonicalForm(ex));
            System.exit(1);
        }

        // adding shutdown hook for clean shutdown when killed
        Thread sh = new ShutdownHook(Thread.currentThread());
        Runtime.getRuntime().addShutdownHook(sh);

        // starting engine
        try {
            CollectEngine engine = new CollectEngine(config);
            engine.run();
        } catch (CollectException ex) {
            System.exit(1);
        } catch (Exception ex) {
            log.error(ExceptionUtils.getCanonicalFormWithStackTrace(ex));
            System.exit(1);
        }
    }

    @SneakyThrows
    private static void printBanner() {
        System.out.println(new String(Launcher.class.getResourceAsStream("/banner.txt").readAllBytes(), StandardCharsets.UTF_8));
    }

}
