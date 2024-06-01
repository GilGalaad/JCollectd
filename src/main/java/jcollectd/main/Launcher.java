package jcollectd.main;

import com.sun.net.httpserver.HttpServer;
import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.exception.CollectException;
import jcollectd.common.exception.ConfigurationException;
import jcollectd.engine.CollectEngine;
import jcollectd.engine.ConfigurationParser;
import jcollectd.engine.WebEngine;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

@Log4j2
public class Launcher {

    public static void main(String[] args) {
        try {
            // printing banner
            printBanner();

            // parsing configuration
            AppConfig config = ConfigurationParser.parse(args);

            // creating collect engine
            CollectEngine engine = new CollectEngine(config);

            // creating http server
            HttpServer server = HttpServer.create(new InetSocketAddress(config.getPort()), 0, "/", new WebEngine(config, engine));
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // adding shutdown hook for clean shutdown when killed
            Thread sh = new ShutdownHook(Thread.currentThread());
            Runtime.getRuntime().addShutdownHook(sh);

            // starting threads
            server.start();
            engine.run();
        } catch (ConfigurationException | CollectException ex) {
            // exception already handled and logged
            System.exit(1);
        } catch (Exception ex) {
            // unhandled exception
            log.error(ExceptionUtils.getCanonicalFormWithStackTrace(ex));
            System.exit(1);
        }
    }

    @SneakyThrows
    private static void printBanner() {
        try (var is = Launcher.class.getResourceAsStream("/banner.txt")) {
            System.out.println(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

}
