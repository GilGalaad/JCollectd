package jcollectd.main;

import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.exception.ConfigurationException;
import jcollectd.engine.ConfigurationParser;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Files;
import java.nio.file.Paths;

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
//        Thread sh = new ShutdownHook(Thread.currentThread());
//        Runtime.getRuntime().addShutdownHook(sh);

        // starting engine
//        try {
//            CollectEngine eng = new CollectEngine(config);
//            eng.run();
//        } catch (Exception ex) {
//            log.fatal(ExceptionUtils.getCanonicalFormWithStackTrace(ex));
//            System.exit(1);
//        }
    }

    @SneakyThrows
    private static void printBanner() {
        System.out.println(Files.readString(Paths.get(Launcher.class.getClassLoader().getResource("banner.txt").toURI())));
    }

}
