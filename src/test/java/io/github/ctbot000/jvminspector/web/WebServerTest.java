package io.github.ctbot000.jvminspector.web;

import io.github.ctbot000.jvminspector.inspect.InspectionOptions;
import io.github.ctbot000.jvminspector.render.TestJson;
import io.github.ctbot000.jvminspector.target.LocalTarget;
import io.github.ctbot000.jvminspector.target.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerTest {

    private static Target target;
    private static WebServer server;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws IOException {
        target = new LocalTarget();
        server = WebServer.start(target, InspectionOptions.defaults(), WebServer.DEFAULT_HOST, 0);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    static void stopServer() {
        server.close();
        target.close();
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(60))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a request with a chosen Host header. The JDK's HTTP client refuses to set that header,
     * and it is exactly the header the rebinding guard reads, so this one test speaks HTTP directly.
     */
    private static int statusWithHost(String host) throws IOException {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", server.port())) {
            socket.getOutputStream().write(("GET /api/live HTTP/1.1\r\nHost: " + host
                    + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    @Test
    void theRootPathServesTheUserInterface() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("<title>jvm-inspector</title>"));
        assertTrue(response.body().contains("/api/live"), "the page must know where to poll");
        assertFalse(response.body().contains("http://cdn"), "the page must not fetch anything external");
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
    }

    @Test
    void theTargetEndpointDescribesWhatIsBeingInspected() throws Exception {
        Map<?, ?> body = object(get("/api/target"));
        assertEquals("jvm-inspector", body.get("tool"));
        assertEquals(Boolean.TRUE, body.get("inProcess"));
        assertTrue(String.valueOf(body.get("description")).contains("this JVM"));
        assertInstanceOf(Map.class, body.get("defaults"));
    }

    @Test
    void theSectionListMatchesTheInspectors() throws Exception {
        HttpResponse<String> response = get("/api/sections");
        assertEquals(200, response.statusCode());
        List<?> sections = assertInstanceOf(List.class, TestJson.parse(response.body()));
        assertEquals(io.github.ctbot000.jvminspector.inspect.Inspectors.all().size(), sections.size());
        Map<?, ?> first = assertInstanceOf(Map.class, sections.get(0));
        assertEquals("overview", first.get("id"));
        assertEquals(Boolean.TRUE, first.get("available"));
    }

    @Test
    void theLiveEndpointCarriesTheMovingNumbers() throws Exception {
        Map<?, ?> body = object(get("/api/live"));
        Map<?, ?> heap = assertInstanceOf(Map.class, body.get("heap"));
        assertTrue((Double) heap.get("used") > 0);
        Map<?, ?> threads = assertInstanceOf(Map.class, body.get("threads"));
        assertTrue((Double) threads.get("live") > 0);
        assertInstanceOf(Map.class, body.get("gc"));
        assertInstanceOf(List.class, body.get("pools"));
        assertTrue((Double) body.get("timestamp") > 0);
    }

    @Test
    void oneSectionCanBeFetchedOnItsOwn() throws Exception {
        Map<?, ?> body = object(get("/api/section?id=overview&stackDepth=0"));
        List<?> sections = assertInstanceOf(List.class, body.get("sections"));
        assertEquals(2, sections.size(), "the requested section plus the run summary");
        assertEquals("Overview", ((Map<?, ?>) sections.get(0)).get("title"));
    }

    @Test
    void queryParametersOverrideTheServerDefaults() throws Exception {
        Map<?, ?> body = object(get("/api/section?id=modules&detail=full"));
        String rendered = String.valueOf(body);
        assertTrue(rendered.contains("Module detail"),
                "detail=full should add the per-module breakdown");
    }

    @Test
    void reportsCanBeDownloadedInEveryFormat() throws Exception {
        HttpResponse<String> markdown = get("/api/download?format=markdown");
        assertEquals(200, markdown.statusCode());
        assertTrue(markdown.headers().firstValue("Content-Disposition").orElse("").contains(".md"));
        assertTrue(markdown.body().startsWith("# jvm-inspector"));

        HttpResponse<String> json = get("/api/download?format=json");
        assertTrue(json.headers().firstValue("Content-Disposition").orElse("").contains(".json"));
        assertInstanceOf(Map.class, TestJson.parse(json.body()));

        HttpResponse<String> text = get("/api/download?format=text");
        assertTrue(text.headers().firstValue("Content-Disposition").orElse("").contains(".txt"));
        assertTrue(text.body().contains("1. OVERVIEW"));
    }

    @Test
    void unknownPathsAndSectionsAreNotFound() throws Exception {
        assertEquals(404, get("/nope").statusCode());
        assertEquals(404, get("/api/section?id=nope").statusCode());
        assertEquals(404, get("/api/section").statusCode());
    }

    @Test
    void onlyGetIsAccepted() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/live"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void aLoopbackServerRefusesAForeignHostHeader() throws IOException {
        assertEquals(403, statusWithHost("attacker.example.com"));
        assertEquals(200, statusWithHost("localhost:" + server.port()));
        assertEquals(200, statusWithHost("127.0.0.1:" + server.port()));
    }

    @Test
    void queryStringsAreParsedAndDecoded() {
        assertEquals(Map.of(), WebServer.parseQuery(null));
        assertEquals(Map.of(), WebServer.parseQuery("  "));
        assertEquals(Map.of("a", "1", "b", "two words"), WebServer.parseQuery("a=1&b=two%20words"));
        assertEquals(Map.of("flag", ""), WebServer.parseQuery("flag"));
    }

    private static Map<?, ?> object(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), () -> "body: " + response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        return assertInstanceOf(Map.class, TestJson.parse(response.body()));
    }
}
