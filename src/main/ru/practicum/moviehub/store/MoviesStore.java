package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public Movie add(String title, int year) {
        int id = nextId;
        nextId += 1;

        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        return movie;
    }

    public List<Movie> findAll() {
        List<Movie> result = new ArrayList<>(movies.values());
        result.sort(Comparator.comparingInt(Movie::getId));
        return result;
    }

    public Movie findById(int id) {
        return movies.get(id);
    }

    public boolean deleteById(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> findByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies.values()) {
            if (movie.getYear() == year) {
                result.add(movie);
            }
        }
        result.sort(Comparator.comparingInt(Movie::getId));
        return result;
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }
}