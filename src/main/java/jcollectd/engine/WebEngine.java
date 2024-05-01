package jcollectd.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jcollectd.common.dto.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static jcollectd.common.CommonUtils.smartElapsed;

@RequiredArgsConstructor
@Log4j2
public class WebEngine implements HttpHandler {

    private final AppConfig config;
    private final CollectEngine engine;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long startTime = System.nanoTime();

        byte[] body = "Internal server error".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-type", List.of("text/plain; charset=utf-8"));
        exchange.sendResponseHeaders(500, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }

        long endTime = System.nanoTime();
        log.debug("{} {} served in {}", exchange.getRequestMethod(), exchange.getRequestURI(), smartElapsed(endTime - startTime));
    }

}
