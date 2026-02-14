package com.political.service;

import java.time.Duration; // Added this import to fix your error
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.political.model.Tag;

import reactor.core.publisher.Mono;

@Service
public class BillTagService {
    private static final Logger logger = LoggerFactory.getLogger(BillTagService.class);
    private final WebClient webClient;

    @Value("${congress.api.url}") private String congressUrl;
    @Value("${CONGRESS_API_KEY}") private String apiKey;
    @Value("${GROQ_API_KEY}") private String groqKey;
    @Value("${SUPABASE_URL}") private String supabaseUrl;
    @Value("${SUPABASE_KEY}") private String supabaseKey;

    public BillTagService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void processBillTags() {
        try {
            String uri = congressUrl + "/bill?format=json&limit=5&api_key=" + apiKey;
            
            // Fixed the duplicate code and added error handling
            JsonNode root = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(10)) 
                .onErrorResume(e -> {
                    logger.error("Congress API unreachable: {}", e.getMessage());
                    return Mono.empty(); 
                })
                .block();

            if (root == null || !root.has("bills")) {
                logger.warn("No bills found or API returned empty response.");
                return;
            }

            // Added the loop to process the bills found
            for (JsonNode bill : root.get("bills")) {
                String title = bill.get("title").asText();
                List<String> tags = fetchTagsFromGroq(title);
                if (!tags.isEmpty()) {
                    saveTagsToSupabase(tags);
                }
            }

        } catch (Exception e) {
            logger.error("SYSTEM ALERT: Error in bill processing: {}", e.getMessage());
        }
    }

    private List<String> fetchTagsFromGroq(String billText) {
    // We'll use a widely compatible model ID
    String modelName = "groq/compound-mini"; 
    
    String prompt = "Return 3 one-word tags for this bill title. Separate by commas only. Title: " + billText;

    // Simplified request body
    Map<String, Object> requestBody = Map.of(
        "model", modelName,
        "messages", List.of(
            Map.of("role", "user", "content", prompt)
        )
    );

    try {
        JsonNode response = webClient.post()
            .uri("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer " + groqKey)
            .header("Content-Type", "application/json") // Explicitly set content type
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (response != null && response.has("choices")) {
            String content = response.get("choices").get(0).get("message").get("content").asText();
            List<String> cleanedTags = new ArrayList<>();
            // Standardizing the split and cleanup
            for (String s : content.split(",")) {
                String clean = s.trim().toLowerCase().replaceAll("[^a-zA-Z]", "");
                if (!clean.isEmpty()) {
                    cleanedTags.add(clean);
                }
            }
            return cleanedTags;
        }
        return List.of();
    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
        // This will print the EXACT reason Groq is mad (e.g. invalid model)
        logger.error("Groq API Error Details: {}", e.getResponseBodyAsString());
        return List.of();
    } catch (Exception e) {
        logger.error("General Error calling Groq: {}", e.getMessage());
        return List.of();
    }
}

    private void saveTagsToSupabase(List<String> tags) {
        List<Tag> tagObjects = tags.stream()
            .filter(t -> !t.isEmpty())
            .map(Tag::new)
            .toList();

        webClient.post()
            .uri(supabaseUrl + "/rest/v1/tag")
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .header("Content-Type", "application/json")
            .bodyValue(tagObjects)
            .retrieve()
            .bodyToMono(String.class)
            .subscribe(res -> logger.info("Tags inserted: {}", tags));
    }
}