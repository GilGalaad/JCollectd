package jcollectd.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jcollectd.common.ExceptionUtils;
import jcollectd.common.dto.config.AppConfig;
import jcollectd.common.dto.rest.Runtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jcollectd.common.CommonUtils.OBJECT_MAPPER;
import static jcollectd.common.CommonUtils.smartElapsed;

@RequiredArgsConstructor
@Log4j2
public class WebEngine implements HttpHandler {

    private final AppConfig config;
    private final CollectEngine engine;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long startTime = System.nanoTime();
        log.debug("{} {}", exchange.getRequestMethod(), exchange.getRequestURI());

        try {
            Response response = switch (exchange.getRequestURI().toString()) {
                case "/api/runtime" -> handleApiRequest();
                default -> serveStaticResource(exchange.getRequestURI().toString());
            };
            exchange.getResponseHeaders().put("Access-Control-Allow-Origin", List.of("*"));
            exchange.getResponseHeaders().put("Content-type", List.of(response.contentType));
            exchange.sendResponseHeaders(200, response.body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.body);
            }
        } catch (Exception ex) {
            log.error("Error during {} {}: {}", exchange.getRequestMethod(), exchange.getRequestURI(), ExceptionUtils.getCanonicalFormWithStackTrace(ex));
            byte[] body = "Internal server error".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().put("Access-Control-Allow-Origin", List.of("*"));
            exchange.getResponseHeaders().put("Content-type", List.of("text/plain; charset=utf-8"));
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        long endTime = System.nanoTime();
        log.debug("{} {} served in {}", exchange.getRequestMethod(), exchange.getRequestURI(), smartElapsed(endTime - startTime));
    }

    private Response serveStaticResource(String path) throws IOException {
        // emulate nginx try_files, serve target file if exists, otherwise index.html
        // since the webapp could be mounted in a specific context root, extract last fragment from the path and assume relative to web directory
        String fragment = "/" + Arrays.asList(path.split("/", -1)).getLast();
        if (fragment.equals("/")) {
            return handleIndexRequest();
        }
        try (var is = this.getClass().getResourceAsStream("/web" + fragment)) {
            if (is == null) {
                return handleIndexRequest();
            }

            String contentType = switch (path) {
                case String s when s.toLowerCase().endsWith(".css") -> "text/css; charset=utf-8";
                case String s when s.toLowerCase().endsWith(".js") -> "text/javascript; charset=utf-8";
                default -> "application/octet-stream";
            };
            return new Response(is.readAllBytes(), contentType);
        }
    }

    private Response handleIndexRequest() throws IOException {
        try (var is = this.getClass().getResourceAsStream("/web/index.html")) {
            return new Response(is.readAllBytes(), "text/html; charset=utf-8");
        }
    }

    private Response handleApiRequest() throws SQLException, JsonProcessingException {
        long startTime = System.nanoTime();
        Instant after = Instant.now().minus(config.getRetention());
        List<List<Object[]>> datasets = new ArrayList<>(config.getProbes().size());
        try (var service = new SqliteService()) {
            service.initializeDatabase();
            for (var probe : config.getProbes()) {
                var rs = switch (probe.getType()) {
                    case LOAD -> service.getLoadSamples(after);
                    case CPU -> service.getCpuSamples(after);
                    case MEM -> service.getMemSamples(after);
                    case NET -> service.getNetSamples(after, probe.getDevice());
                    case DISK, ZFS -> service.getDiskSamples(after, probe.getDevice());
                    case GPU -> service.getGpuSamples(after);
                };
                datasets.add(rs);
            }
        }

        Runtime runtime = new Runtime(config.getHostname(), config.getInterval().getSeconds(), config.getProbes(),
                engine.getCurResult() != null ? engine.getCurResult().getCollectTms() : null,
                engine.getCollectElapsed() != null ? smartElapsed(engine.getCollectElapsed()) : null,
                engine.getPersistElapsed() != null ? smartElapsed(engine.getPersistElapsed()) : null,
                smartElapsed(System.nanoTime() - startTime),
                datasets);

        byte[] body = OBJECT_MAPPER.writeValueAsBytes(runtime);
        return new Response(body, "application/json");
    }

    private record Response(byte[] body, String contentType) {
    }

}
