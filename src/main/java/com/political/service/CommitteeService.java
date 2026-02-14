package com.political.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class CommitteeService {
    private static final Logger logger = LoggerFactory.getLogger(CommitteeService.class);
    private final WebClient webClient;

    @Value("${congress.api.url}") private String congressUrl;
    @Value("${CONGRESS_API_KEY}") private String apiKey;
    @Value("${SUPABASE_URL}") private String supabaseUrl;
    @Value("${SUPABASE_KEY}") private String supabaseKey;

    public CommitteeService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void fetchAndSaveCommittees() {
        try {
            // Using limit=20 to get a good batch of committees
            String uri = congressUrl + "/committee?format=json&limit=40&api_key=" + apiKey;

            JsonNode root = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(10))
                .block();

            if (root == null || !root.has("committees")) {
                logger.warn("No committees found or API returned empty response.");
                return;
            }

            for (JsonNode committeeNode : root.get("committees")) {
                // 1. Extract exactly the fields you requested
                String name = committeeNode.has("name") ? committeeNode.get("name").asText() : null;
                String officialCode = committeeNode.has("systemCode") ? committeeNode.get("systemCode").asText() : null;
                String apiUrl = committeeNode.has("url") ? committeeNode.get("url").asText() : null;

                // Name is NOT NULL in your SQL, so we only save if it exists
                if (name != null && !name.trim().isEmpty()) {
                    saveCommitteeToSupabase(name, officialCode, apiUrl);
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching committees from Congress API: {}", e.getMessage());
        }
    }

    private void saveCommitteeToSupabase(String name, String officialCode, String apiUrl) {
        // 2. Safely map to your SQL columns using HashMap
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        
        if (officialCode != null && !officialCode.isEmpty()) {
            body.put("official_code", officialCode);
        }
        if (apiUrl != null && !apiUrl.isEmpty()) {
            body.put("api_url", apiUrl);
        }

        try {
            webClient.post()
                .uri(supabaseUrl + "/rest/v1/committee") // Assumes your table is named 'committee'
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            logger.info("Successfully saved Committee: {}", name);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            logger.error("Failed to save Committee '{}'. Supabase Error: {}", name, e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("General error saving committee: {}", e.getMessage());
        }
    }
}