package com.springboot.finalprojcet.domain.itunes.service;

import java.util.Map;

public interface ItunesService {
    Map<String, Object> searchMusic(String term, int limit, String country, String entity);

    Map<String, Object> getRecommendations(String country, int limit, String genre);

    Map<String, Object> getAlbumDetails(String id, String country);
}
