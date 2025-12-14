package com.justsyncit.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSecurityTest {

    private static WebServer server;
    private static WebServerContext context;
    private static int port;
    private static HttpClient client;

    @BeforeAll
    static void setup() {
        context = Mockito.mock(WebServerContext.class);
        // We need real UserController behaviour, but WebServer creates it internally.
        // The contexts are mocked but UserController uses internal maps so it should
        // work for auth testing.

        server = new WebServer(0, context); // 0 for random port
        server.start();
        port = server.getBoundPort();
        client = HttpClient.newHttpClient();

        // Wait for server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testUnauthenticatedAccessIsBlocked() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/config"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    void testLoginAndAuthenticatedAccess() throws Exception {
        // 1. Login
        String loginJson = "{\"username\":\"admin\",\"password\":\"admin\"}";
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();

        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResponse.statusCode());

        // Extract token (quick and dirty parsing)
        String body = loginResponse.body();
        String token = body.split("\"token\":\"")[1].split("\"")[0];

        // 2. Access protected resource
        HttpRequest configRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/config"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> configResponse = client.send(configRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, configResponse.statusCode());
    }

    @Test
    void testPathTraversalIsBlocked() throws Exception {
        // 1. Login first
        String loginJson = "{\"username\":\"admin\",\"password\":\"admin\"}";
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();
        String token = client.send(loginRequest, HttpResponse.BodyHandlers.ofString())
                .body().split("\"token\":\"")[1].split("\"")[0];

        // 2. Try to access .ssh
        // FileBrowserController checks path query param
        String sensitivePath = System.getProperty("user.home") + "/.ssh";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/files?path=" + sensitivePath))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Should return 403 Forbidden
        assertEquals(403, response.statusCode());
    }
}
