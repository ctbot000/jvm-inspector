package io.github.ctbot000.jvminspector.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ctbot000.jvminspector.JvmInspector;
import io.github.ctbot000.jvminspector.inspect.Detail;
import io.github.ctbot000.jvminspector.inspect.InspectionOptions;
import io.github.ctbot000.jvminspector.inspect.Inspector;
import io.github.ctbot000.jvminspector.inspect.Inspectors;
import io.github.ctbot000.jvminspector.model.Report;
import io.github.ctbot000.jvminspector.render.JsonRenderer;
import io.github.ctbot000.jvminspector.render.OutputFormat;
import io.github.ctbot000.jvminspector.target.Target;
import io.github.ctbot000.jvminspector.util.Json;
import io.github.ctbot000.jvminspector.util.Redactor;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small HTTP server that presents the report in a browser.
 *
 * <p>It binds to the loopback interface by default and sends no CORS headers, so a page on another
 * origin cannot read the answers; the {@code Host} header is checked as well, which is what stops a
 * DNS rebinding attack from turning a local port into a public one.
 */
public final class WebServer implements AutoCloseable {

    /** The interface the server binds to unless the caller names another one. */
    public static final String DEFAULT_HOST = "127.0.0.1";
    /** The port used when none is given. */
    public static final int DEFAULT_PORT = 7777;

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final Target target;
    private final InspectionOptions defaults;
    private final HttpServer server;
    private final ExecutorService workers;

    private WebServer(Target target, InspectionOptions defaults, HttpServer server,
                      ExecutorService workers) {
        this.target = target;
        this.defaults = defaults;
        this.server = server;
        this.workers = workers;
    }

    /** Starts the server. A port of {@code 0} picks a free one, which is what the tests use. */
    public static WebServer start(Target target, InspectionOptions defaults, String host, int port)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), port), 32);
        ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "jvm-inspector-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(workers);
        WebServer web = new WebServer(target, defaults, server, workers);
        server.createContext("/", web::route);
        server.start();
        return web;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://" + server.getAddress().getHostString() + ":" + port() + "/";
    }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }

    private void route(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain; charset=utf-8", "only GET is supported");
                return;
            }
            if (!hostIsAllowed(exchange)) {
                send(exchange, 403, "text/plain; charset=utf-8",
                        "refused: this server answers only to a loopback host name");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            switch (path) {
                case "/", "/index.html" -> page(exchange);
                case "/api/target" -> json(exchange, describeTarget());
                case "/api/sections" -> json(exchange, describeSections());
                case "/api/live" -> json(exchange, LiveMetrics.snapshot(target));
                case "/api/report" -> json(exchange, report(query, Set.of()));
                case "/api/section" -> section(exchange, query);
                case "/api/download" -> download(exchange, query);
                default -> send(exchange, 404, "text/plain; charset=utf-8", "no such path: " + path);
            }
        } catch (RuntimeException failure) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":" + Json.quote(String.valueOf(failure)) + "}");
        }
    }

    /**
     * Rejects a request whose {@code Host} header is not a loopback name. A browser tricked into
     * resolving an attacker's domain to 127.0.0.1 still sends that domain here, so this is the
     * check that keeps the port private.
     */
    private boolean hostIsAllowed(HttpExchange exchange) {
        if (!server.getAddress().getAddress().isLoopbackAddress()) {
            return true;
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return true;
        }
        String name = host.contains("]") ? host.substring(0, host.indexOf(']') + 1)
                : host.split(":")[0];
        return "localhost".equalsIgnoreCase(name) || "127.0.0.1".equals(name)
                || "[::1]".equals(name) || "::1".equals(name);
    }

    private void page(HttpExchange exchange) throws IOException {
        try (InputStream resource = WebServer.class.getResourceAsStream("index.html")) {
            if (resource == null) {
                send(exchange, 500, "text/plain; charset=utf-8", "the packaged user interface is missing");
                return;
            }
            send(exchange, 200, "text/html; charset=utf-8", new String(resource.readAllBytes(),
                    StandardCharsets.UTF_8));
        }
    }

    private void section(HttpExchange exchange, Map<String, String> query) throws IOException {
        String id = query.get("id");
        if (id == null || !Inspectors.byId().containsKey(id)) {
            send(exchange, 404, "application/json; charset=utf-8",
                    "{\"error\":\"unknown section\"}");
            return;
        }
        json(exchange, report(query, Set.of(id)));
    }

    private void download(HttpExchange exchange, Map<String, String> query) throws IOException {
        OutputFormat format = OutputFormat.parse(query.getOrDefault("format", "text"));
        Report report = JvmInspector.inspect(target, options(query),
                new JvmInspector.Selection(Set.of(), Set.of()));
        String extension = switch (format) {
            case TEXT -> "txt";
            case JSON -> "json";
            case MARKDOWN -> "md";
        };
        String name = "jvm-report-" + CLOCK.format(java.time.Instant.now().atZone(ZoneId.systemDefault()))
                .replace(':', '-').replace(' ', '_') + "." + extension;
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + name + "\"");
        send(exchange, 200, format == OutputFormat.JSON ? "application/json; charset=utf-8"
                : "text/plain; charset=utf-8", format.renderer().renderToString(report));
    }

    private String report(Map<String, String> query, Set<String> only) {
        Report report = JvmInspector.inspect(target, options(query),
                new JvmInspector.Selection(only, Set.of()));
        return new JsonRenderer().renderToString(report);
    }

    /** Query parameters override the options the command line started the server with. */
    private InspectionOptions options(Map<String, String> query) {
        Detail detail = query.containsKey("detail") ? Detail.parse(query.get("detail")) : defaults.detail();
        boolean expensive = query.containsKey("expensive")
                ? Boolean.parseBoolean(query.get("expensive")) : defaults.expensive();
        int stackDepth = defaults.stackDepth();
        if (query.containsKey("stackDepth")) {
            try {
                stackDepth = Math.max(0, Integer.parseInt(query.get("stackDepth")));
            } catch (NumberFormatException ignored) {
                // Keep the configured depth rather than failing a whole request over one parameter.
            }
        }
        Redactor redactor = query.containsKey("redact")
                ? new Redactor(Redactor.parseMode(query.get("redact"))) : defaults.redactor();
        return new InspectionOptions(detail, expensive, stackDepth, redactor);
    }

    private String describeTarget() {
        return "{\"description\":" + Json.quote(target.description())
                + ",\"inProcess\":" + target.inProcess()
                + ",\"tool\":" + Json.quote(JvmInspector.NAME)
                + ",\"version\":" + Json.quote(JvmInspector.version())
                + ",\"diagnosticCommands\":" + target.diagnostics().commands().size()
                + ",\"defaults\":{\"detail\":"
                + Json.quote(defaults.detail().name().toLowerCase(Locale.ROOT))
                + ",\"expensive\":" + defaults.expensive()
                + ",\"stackDepth\":" + defaults.stackDepth()
                + ",\"redact\":" + Json.quote(defaults.redactor().mode().name().toLowerCase(Locale.ROOT))
                + "}}";
    }

    private String describeSections() {
        StringJoiner sections = new StringJoiner(",", "[", "]");
        for (Inspector inspector : Inspectors.all()) {
            sections.add("{\"id\":" + Json.quote(inspector.id())
                    + ",\"title\":" + Json.quote(inspector.title())
                    + ",\"description\":" + Json.quote(String.valueOf(inspector.description()))
                    + ",\"available\":" + (!inspector.requiresInProcess() || target.inProcess()) + "}");
        }
        return sections.toString();
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        send(exchange, 200, "application/json; charset=utf-8", body);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            query.put(decode(name), decode(value));
        }
        return query;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

}
