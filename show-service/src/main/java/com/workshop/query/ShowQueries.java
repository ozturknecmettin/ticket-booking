package com.workshop.query;

public sealed interface ShowQueries permits
        ShowQueries.GetShow,
        ShowQueries.GetAllShows,
        ShowQueries.GetActiveShows {

    record GetShow(String showId) implements ShowQueries {}

    record GetAllShows() implements ShowQueries {}

    record GetActiveShows() implements ShowQueries {}
}
