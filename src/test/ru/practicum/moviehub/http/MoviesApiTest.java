package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";

    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;
    private static final Gson gson = new Gson();

    private static void assertJsonContentType(HttpResponse<?> resp) {
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    @BeforeAll
    static void beforeAll() {
        // Создаем чистое хранилище фильмов
        store = new MoviesStore();

        // Создаем и запускаем сервер на порту 8080
        // Сервер получает ссылку на хранилище чтобы читать и менять данные
        server = new MoviesServer(store, 8080);
        server.start();

        // Создаем HTTP клиент один раз
        // Таймаут соединения ставим 2 секунды как в задании
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        // Перед каждым тестом чистим хранилище
        // Так каждый тест начинается с одинакового состояния
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        // После всех тестов останавливаем сервер
        // Это освобождает порт 8080
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        // Этот тест проверяет первый сценарий из спецификации
        // Когда фильмов нет GET /movies должен вернуть 200 и пустой массив

        // Собираем HTTP запрос на GET /movies
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        // Отправляем запрос и получаем ответ как строку в UTF 8
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем код ответа 200
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        // Проверяем заголовок Content Type
        assertJsonContentType(resp);

        // Проверяем что тело ответа это пустой JSON массив
        assertEquals("[]", resp.body().trim(), "Ожидается пустой массив");
    }

    @Test
    void getMovies_whenHasMovies_returnsList() throws Exception {
        // Этот тест проверяет что GET /movies возвращает список добавленных фильмов

        // Добавляем фильмы прямо в хранилище
        // Это проще чем делать POST на этом шаге
        store.add("Matrix", 1999);
        store.add("Interstellar", 2014);

        // Делаем запрос на получение списка
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем что запрос успешный
        assertEquals(200, resp.statusCode());

        // Проверяем заголовок Content Type
        assertJsonContentType(resp);

        // Превращаем JSON массив из ответа в список объектов Movie
        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        // Проверяем что сервер вернул два фильма
        assertEquals(2, movies.size());

        // Проверяем что в списке именно те названия которые мы добавили
        assertEquals("Matrix", movies.get(0).getTitle());
        assertEquals("Interstellar", movies.get(1).getTitle());
    }

    @Test
    void postMovies_whenValid_createsMovie() throws Exception {
        // Этот тест проверяет успешное добавление фильма
        // POST /movies должен вернуть 201 и JSON созданного фильма

        // Тело запроса в формате JSON
        String json = "{\"title\":\"Interstellar\",\"year\":2014}";

        // Собираем POST запрос
        // Content Type ставим application/json чтобы сервер принял данные
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем что сервер создал ресурс и вернул 201
        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");

        // Проверяем заголовок Content Type
        assertJsonContentType(resp);

        // Читаем созданный фильм из ответа
        Movie movie = gson.fromJson(resp.body(), Movie.class);

        // Проверяем что id присвоен и больше нуля
        assertTrue(movie.getId() > 0, "Ожидается, что id будет больше 0");

        // Проверяем что данные сохранились без изменений
        assertEquals("Interstellar", movie.getTitle());
        assertEquals(2014, movie.getYear());

        // Дополнительно проверяем что фильм реально появился в списке
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        List<Movie> movies = gson.fromJson(getResp.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(1, movies.size(), "Ожидается один фильм в списке");
    }

    @Test
    void postMovies_whenEmptyTitle_returns422() throws Exception {
        // Этот тест проверяет ошибку валидации
        // title пустой поэтому ожидаем 422 и объект ошибки

        String json = "{\"title\":\"\",\"year\":2014}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "Ожидается 422 при ошибке валидации");

        assertJsonContentType(resp);

        // Разбираем объект ошибки
        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);

        // Проверяем что сервер говорит что это ошибка валидации
        assertEquals("Ошибка валидации", error.getError());

        // Проверяем что детали ошибки не пустые
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void postMovies_whenTooLongTitle_returns422() throws Exception {
        // Этот тест проверяет ограничение длины title
        // Если title длиннее 100 символов ожидаем 422

        // Делаем строку из 101 символа
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            sb.append('a');
        }

        String json = "{\"title\":\"" + sb + "\",\"year\":2014}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "Ожидается 422 при слишком длинном title");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void postMovies_whenYearTooSmall_returns422() throws Exception {
        // Этот тест проверяет что год не может быть меньше 1888
        // Для 1887 ожидаем 422

        String json = "{\"title\":\"Ok\",\"year\":1887}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "Ожидается 422 при неверном year");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void postMovies_whenYearTooLarge_returns422() throws Exception {
        // Этот тест проверяет верхнюю границу года
        // По ТЗ год не должен быть больше текущий год плюс 1
        // Мы ставим текущий год плюс 2 и ожидаем 422

        int year = Year.now().getValue() + 2;
        String json = "{\"title\":\"Ok\",\"year\":" + year + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "Ожидается 422 при неверном year");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertNotNull(error.getDetails());
        assertFalse(error.getDetails().isEmpty());
    }

    @Test
    void postMovies_whenWrongContentType_returns415() throws Exception {
        // Этот тест проверяет что сервер смотрит на Content Type
        // Если Content Type не application/json ожидаем 415

        String json = "{\"title\":\"Ok\",\"year\":2014}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "Ожидается 415 при неверном Content-Type");

        assertJsonContentType(resp);
    }

    @Test
    void postMovies_whenInvalidJson_returns400() throws Exception {
        // Этот тест проверяет обработку сломанного JSON
        // Если JSON нельзя распарсить сервер должен вернуть 400

        String json = "{title:bad";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Ожидается 400 при некорректном JSON");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertNotNull(error.getError());
        assertFalse(error.getError().isBlank());
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        // Этот тест проверяет получение фильма по id
        // Если фильм есть сервер возвращает 200 и объект фильма

        Movie created = store.add("Matrix", 1999);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + created.getId()))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "Ожидается 200 при существующем id");

        assertJsonContentType(resp);

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(created.getId(), movie.getId());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        // Этот тест проверяет 404 когда фильма с таким id нет

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "Ожидается 404 если фильм не найден");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", error.getError());
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        // Этот тест проверяет 400 когда id в пути не число

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Ожидается 400 если id не число");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", error.getError());
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        // Этот тест проверяет успешное удаление
        // При успешном удалении ожидаем 204 и пустое тело

        Movie created = store.add("Matrix", 1999);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + created.getId()))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode(), "DELETE /movies/{id} должен вернуть 204");

        // Даже для 204 мы проверяем заголовок Content Type как сказано в правилах
        assertJsonContentType(resp);
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        // Этот тест проверяет 404 при удалении несуществующего фильма

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "Ожидается 404 если фильм не найден");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", error.getError());
    }

    @Test
    void deleteMovie_whenIdNotNumber_returns400() throws Exception {
        // Этот тест проверяет 400 при удалении когда id не число

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Ожидается 400 если id не число");

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный ID", error.getError());
    }

    @Test
    void getMovies_whenYearFilterWorks_returnsFilteredList() throws Exception {
        // Этот тест проверяет фильтрацию по году через query параметр year
        // Сервер должен вернуть только фильмы указанного года

        int year = Year.now().getValue();
        store.add("A", year);
        store.add("B", year);
        store.add("C", year - 1);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + year))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        assertJsonContentType(resp);

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size(), "Ожидается 2 фильма за указанный год");
    }

    @Test
    void getMovies_whenYearFilterNoMatches_returnsEmptyArray() throws Exception {
        // Этот тест проверяет что фильтр может вернуть пустой массив
        // Если за этот год фильмов нет сервер возвращает []

        int year = Year.now().getValue();
        store.add("A", year - 1);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + year))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        assertJsonContentType(resp);

        assertEquals("[]", resp.body().trim());
    }

    @Test
    void getMovies_whenYearParamNotNumber_returns400() throws Exception {
        // Этот тест проверяет ошибку если year не число
        // По ТЗ в этом случае должен быть 400

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса — 'year'", error.getError());
    }

    @Test
    void unsupportedMethod_onMovies_returns405() throws Exception {
        // Этот тест проверяет 405 для неподдерживаемого метода на /movies
        // Мы отправляем PUT и ожидаем 405

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode());

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertNotNull(error.getError());
    }

    @Test
    void unsupportedMethod_onMovieById_returns405() throws Exception {
        // Этот тест проверяет 405 для неподдерживаемого метода на /movies/{id}
        // Мы отправляем POST без тела и ожидаем 405

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode());

        assertJsonContentType(resp);

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertNotNull(error.getError());
    }
}