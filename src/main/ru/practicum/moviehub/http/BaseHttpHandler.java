package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {

    protected static final String CT_JSON = "application/json; charset=UTF-8";

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, response.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(response);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
    }

}