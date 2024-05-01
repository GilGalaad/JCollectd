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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

//        if (exchange.getRequestMethod().equals("OPTIONS")) {
//            log.info("OPTIONS!!!");
//            exchange.getResponseHeaders().put("Access-Control-Allow-Origin", List.of("*"));
//            exchange.sendResponseHeaders(200, -1);
//            return;
//        }

        try {
            Response response = switch (exchange.getRequestURI().toString()) {
                case "/api/runtime" -> handleApiRequest();
                default -> handleApiRequest();
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

    private Response handleApiRequest() throws SQLException, JsonProcessingException {
        long startTime = System.nanoTime();
        Instant after = Instant.now().minus(Duration.ofHours(12));
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

        Runtime runtime = new Runtime(config.getHostname(), config.getProbes(),
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
