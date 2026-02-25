package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if (path.equals("/movies") || path.equals("/movies/")) {
            handleMoviesRoot(ex, method);
            return;
        }

        if (path.startsWith("/movies/")) {
            handleMovieById(ex, method, path);
            return;
        }

        sendJson(ex, 404, gson.toJson(new ErrorResponse("Эндпоинт не найден")));
    }

    private void handleMoviesRoot(HttpExchange ex, String method) throws IOException {
        if (method.equalsIgnoreCase("GET")) {
            handleGetMovies(ex);
            return;
        }
        if (method.equalsIgnoreCase("POST")) {
            handlePostMovie(ex);
            return;
        }
        sendJson(ex, 405, gson.toJson(new ErrorResponse("Метод не поддерживается")));
    }

    private void handleGetMovies(HttpExchange ex) throws IOException {
        Map<String, String> query = QueryString.parse(ex.getRequestURI().getRawQuery());

        if (query.containsKey("year")) {
            String yearStr = query.get("year");
            int year;
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'")));
                return;
            }

            sendJson(ex, 200, gson.toJson(store.findByYear(year)));
            return;
        }

        sendJson(ex, 200, gson.toJson(store.findAll()));
    }

    private void handlePostMovie(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendJson(ex, 415, gson.toJson(new ErrorResponse("Неподдерживаемый Content-Type")));
            return;
        }

        String body = readBody(ex.getRequestBody());
        Movie incoming;
        try {
            incoming = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный JSON")));
            return;
        }

        List<String> details = validateMovie(incoming);
        if (!details.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Ошибка валидации", details)));
            return;
        }

        Movie created = store.add(incoming.getTitle(), incoming.getYear());
        sendJson(ex, 201, gson.toJson(created));
    }

    private void handleMovieById(HttpExchange ex, String method, String path) throws IOException {
        String idPart = path.substring("/movies/".length());
        if (idPart.contains("/")) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Эндпоинт не найден")));
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        if (method.equalsIgnoreCase("GET")) {
            Movie movie = store.findById(id);
            if (movie == null) {
                sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
                return;
            }
            sendJson(ex, 200, gson.toJson(movie));
            return;
        }

        if (method.equalsIgnoreCase("DELETE")) {
            boolean removed = store.deleteById(id);
            if (!removed) {
                sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
                return;
            }
            sendNoContent(ex);
            return;
        }

        sendJson(ex, 405, gson.toJson(new ErrorResponse("Метод не поддерживается")));
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie == null) {
            errors.add("тело запроса пустое");
            return errors;
        }

        String title = movie.getTitle();
        if (title == null || title.isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (title.length() > 100) {
            errors.add("название не должно быть длиннее 100 символов");
        }

        int year = movie.getYear();
        int maxYear = Year.now().getValue() + 1;
        if (year < 1888 || year > maxYear) {
            errors.add("год должен быть между 1888 и " + maxYear);
        }

        return errors;
    }

    private String readBody(InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }
}
