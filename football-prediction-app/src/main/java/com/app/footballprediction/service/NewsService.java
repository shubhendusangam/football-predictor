package com.app.footballprediction.service;

import com.app.footballprediction.dto.external.NewsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Service for fetching football news from FREE RSS feeds.
 * No API key required!
 *
 * Sources:
 * - BBC Sport Football
 * - Sky Sports Football
 * - ESPN Football
 * - Guardian Football
 */
@Service
@Slf4j
public class NewsService {

    // Free RSS feed URLs for football news
    private static final Map<String, String> RSS_FEEDS = Map.of(
            "bbc", "https://feeds.bbci.co.uk/sport/football/rss.xml",
            "skysports", "https://www.skysports.com/rss/12040",  // Sky Sports Football
            "espn", "https://www.espn.com/espn/rss/soccer/news",
            "guardian", "https://www.theguardian.com/football/rss"
    );

    // Simple cache to minimize requests
    private final Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes

    /**
     * Get Premier League news headlines from multiple sources
     */
    @Cacheable(value = "news", key = "'premier_league'")
    public NewsResponse getPremierLeagueNews() {
        return fetchNewsFromRss("bbc");
    }

    /**
     * Get general football news
     */
    @Cacheable(value = "news", key = "'football'")
    public NewsResponse getFootballNews() {
        // Aggregate from multiple sources
        List<NewsResponse.Article> allArticles = new ArrayList<>();

        for (String source : List.of("bbc", "guardian")) {
            try {
                NewsResponse response = fetchNewsFromRss(source);
                if (response.getArticles() != null) {
                    allArticles.addAll(response.getArticles());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch from {}: {}", source, e.getMessage());
            }
        }

        // Sort by date (newest first) and limit
        allArticles.sort((a, b) -> {
            if (a.getPublishedAt() == null) return 1;
            if (b.getPublishedAt() == null) return -1;
            return b.getPublishedAt().compareTo(a.getPublishedAt());
        });

        NewsResponse response = new NewsResponse();
        response.setStatus("ok");
        response.setTotalResults(allArticles.size());
        response.setArticles(allArticles.subList(0, Math.min(15, allArticles.size())));

        return response;
    }

    /**
     * Get news for a specific team (searches in cached articles)
     */
    @Cacheable(value = "news", key = "'team_' + #teamName")
    public NewsResponse getTeamNews(String teamName) {
        NewsResponse allNews = getFootballNews();

        if (allNews.getArticles() == null) {
            return allNews;
        }

        // Filter articles mentioning the team
        String searchTerm = teamName.toLowerCase();
        List<NewsResponse.Article> filtered = allNews.getArticles().stream()
                .filter(a ->
                    (a.getTitle() != null && a.getTitle().toLowerCase().contains(searchTerm)) ||
                    (a.getDescription() != null && a.getDescription().toLowerCase().contains(searchTerm)))
                .toList();

        NewsResponse response = new NewsResponse();
        response.setStatus("ok");
        response.setTotalResults(filtered.size());
        response.setArticles(filtered);

        return response;
    }

    /**
     * Fetch news from an RSS feed
     */
    private NewsResponse fetchNewsFromRss(String sourceKey) {
        String cacheKey = "rss_" + sourceKey;

        // Check cache
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("RSS cache hit for: {}", sourceKey);
            return cached.data;
        }

        String feedUrl = RSS_FEEDS.get(sourceKey);
        if (feedUrl == null) {
            return createEmptyResponse();
        }

        try {
            log.info("Fetching RSS feed from: {}", sourceKey);

            List<NewsResponse.Article> articles = parseRssFeed(feedUrl, sourceKey);

            NewsResponse response = new NewsResponse();
            response.setStatus("ok");
            response.setTotalResults(articles.size());
            response.setArticles(articles);

            // Cache result
            cache.put(cacheKey, new CacheEntry(response, System.currentTimeMillis() + CACHE_TTL_MS));

            return response;

        } catch (Exception e) {
            log.error("Failed to fetch RSS feed {}: {}", sourceKey, e.getMessage());
            return createEmptyResponse();
        }
    }

    /**
     * Parse RSS XML feed into articles
     */
    private List<NewsResponse.Article> parseRssFeed(String feedUrl, String sourceName) throws Exception {
        List<NewsResponse.Article> articles = new ArrayList<>();

        // Fetch RSS XML
        URL url = URI.create(feedUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FootballPredictor/1.0)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try (InputStream inputStream = conn.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);

            // Parse items
            NodeList items = doc.getElementsByTagName("item");
            int maxItems = Math.min(items.getLength(), 10);

            for (int i = 0; i < maxItems; i++) {
                Element item = (Element) items.item(i);

                NewsResponse.Article article = new NewsResponse.Article();

                // Source
                NewsResponse.Source source = new NewsResponse.Source();
                source.setName(getSourceDisplayName(sourceName));
                article.setSource(source);

                // Title
                article.setTitle(getElementText(item, "title"));

                // Description
                String desc = getElementText(item, "description");
                if (desc != null) {
                    // Strip HTML tags
                    desc = desc.replaceAll("<[^>]*>", "").trim();
                    if (desc.length() > 200) {
                        desc = desc.substring(0, 200) + "...";
                    }
                }
                article.setDescription(desc);

                // URL
                article.setUrl(getElementText(item, "link"));

                // Published date
                String pubDate = getElementText(item, "pubDate");
                if (pubDate != null) {
                    article.setPublishedAt(pubDate);
                }

                // Try to get image from media:thumbnail or enclosure
                String imageUrl = getImageFromItem(item);
                article.setUrlToImage(imageUrl);

                articles.add(article);
            }
        } finally {
            conn.disconnect();
        }

        return articles;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private String getImageFromItem(Element item) {
        // Try media:thumbnail
        NodeList mediaThumbnails = item.getElementsByTagName("media:thumbnail");
        if (mediaThumbnails.getLength() > 0) {
            Element thumb = (Element) mediaThumbnails.item(0);
            return thumb.getAttribute("url");
        }

        // Try enclosure
        NodeList enclosures = item.getElementsByTagName("enclosure");
        if (enclosures.getLength() > 0) {
            Element enc = (Element) enclosures.item(0);
            String type = enc.getAttribute("type");
            if (type != null && type.startsWith("image/")) {
                return enc.getAttribute("url");
            }
        }

        // Try media:content
        NodeList mediaContent = item.getElementsByTagName("media:content");
        if (mediaContent.getLength() > 0) {
            Element media = (Element) mediaContent.item(0);
            return media.getAttribute("url");
        }

        return null;
    }

    private String getSourceDisplayName(String sourceKey) {
        return switch (sourceKey) {
            case "bbc" -> "BBC Sport";
            case "skysports" -> "Sky Sports";
            case "espn" -> "ESPN";
            case "guardian" -> "The Guardian";
            default -> sourceKey.toUpperCase();
        };
    }

    private NewsResponse createEmptyResponse() {
        NewsResponse response = new NewsResponse();
        response.setStatus("ok");
        response.setTotalResults(0);
        response.setArticles(List.of());
        return response;
    }

    /**
     * Get aggregated news from multiple categories
     */
    public Map<String, Object> getAggregatedNews() {
        Map<String, Object> result = new HashMap<>();

        // Premier League news
        NewsResponse plNews = getPremierLeagueNews();
        result.put("premierLeague", plNews.getArticles());

        // General football news
        NewsResponse footballNews = getFootballNews();
        result.put("general", footballNews.getArticles());

        result.put("totalArticles",
                (plNews.getArticles() != null ? plNews.getArticles().size() : 0) +
                (footballNews.getArticles() != null ? footballNews.getArticles().size() : 0));

        return result;
    }

    /**
     * Clear news cache
     */
    @CacheEvict(value = "news", allEntries = true)
    public void clearCache() {
        cache.clear();
        log.info("News cache cleared (both internal and Spring cache)");
    }

    private record CacheEntry(NewsResponse data, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}

