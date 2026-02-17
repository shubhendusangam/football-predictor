package com.app.footballprediction.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO for news API responses.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsResponse {

    private String status;
    private Integer totalResults;
    private List<Article> articles;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Article {
        private Source source;
        private String author;
        private String title;
        private String description;
        private String url;
        private String urlToImage;
        private String publishedAt;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String id;
        private String name;
    }
}

