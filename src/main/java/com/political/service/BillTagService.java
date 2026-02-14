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
        String uri = congressUrl + "/bill?format=json&limit=20&api_key=" + apiKey;
        
        JsonNode root = webClient.get()
            .uri(uri)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(10))
            .block();

        if (root == null || !root.has("bills")) return;

        for (JsonNode bill : root.get("bills")) {
            // 1. Existing core fields
            String title = bill.has("title") ? bill.get("title").asText() : "No Title";
            String billType = bill.has("type") ? bill.get("type").asText() : "";
            String billNumber = bill.has("number") ? bill.get("number").asText() : "";
            
            // 2. NEW FIELDS: Pulling congress and text/summary from the API
            Integer congress = bill.has("congress") ? bill.get("congress").asInt() : null;
            
            // Note: Congress API sometimes calls text 'text' or buries it in 'latestAction'
            String summaryText = null;
            if (bill.has("text")) {
                summaryText = bill.get("text").asText();
            } else if (bill.has("latestAction") && bill.get("latestAction").has("text")) {
                summaryText = bill.get("latestAction").get("text").asText();
            }

            // Get tags from Groq using the title
            List<String> tags = fetchTagsFromGroq(title);
            
            for (String tagName : tags) {
                // Insert Tag and get the ID back
                Integer tagId = saveTagAndGetId(tagName);
                
                // Insert into Legislation table with the new fields included
                if (tagId != null) {
                    saveToLegislation(billType, billNumber, tagId, title, summaryText, congress);
                }
            }
        }
    } catch (Exception e) {
        logger.error("Error processing bills and tags: {}", e.getMessage());
    }
}
private Integer saveTagAndGetId(String tagName) {
    try {
        Map<String, String> body = Map.of("tag_name", tagName);

        // Fetch as a String first so we can safely inspect the raw response
        String rawResponse = webClient.post()
            .uri(supabaseUrl + "/rest/v1/tag")
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation") 
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        if (rawResponse != null && !rawResponse.isEmpty()) {
            logger.info("Raw Supabase Response for tag '{}': {}", tagName, rawResponse);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode responseNode = mapper.readTree(rawResponse);

            if (responseNode.isArray() && !responseNode.isEmpty()) {
                JsonNode savedRow = responseNode.get(0);
                
                // Safely check for the ID column, regardless of what you named it in SQL
                if (savedRow.has("tag_id") && !savedRow.get("tag_id").isNull()) {
                    return savedRow.get("tag_id").asInt();
                } else if (savedRow.has("id") && !savedRow.get("id").isNull()) {
                    return savedRow.get("id").asInt();
                } else {
                    logger.error("Could not find 'tag_id' or 'id' in the returned JSON: {}", savedRow.toString());
                }
            }
        }
    } catch (Exception e) {
        logger.error("Failed to save tag '{}': {}", tagName, e.getMessage());
    }
    return null;
}

private void saveToLegislation(String type, String number, Integer tagId, String title, String summary, Integer congress) {
    
    // IMPORTANT: Using HashMap instead of Map.of() to prevent "NullPointerException" crashes 
    // if the Congress API forgets to send a summary or congress number for a specific bill.
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("bill_type", type);
    body.put("bill_number", number);
    body.put("tag_id", tagId);
    body.put("title", title);
    
    if (summary != null && !summary.isEmpty()) {
        body.put("summary", summary);
    }
    if (congress != null) {
        body.put("congress", congress);
    }

    try {
        String response = webClient.post()
            .uri(supabaseUrl + "/rest/v1/legislation")
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block(); 

        logger.info("Linked Bill {} #{} (Congress {}) to Tag ID {}", type, number, congress, tagId);
    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
        logger.error("Failed to link Bill {} #{}. Reason: {}", type, number, e.getResponseBodyAsString());
    } catch (Exception e) {
        logger.error("General error linking legislation: {}", e.getMessage());
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

public void updateLegislationDetails() {
    try {
        // 1. Fetch all existing bills from your Supabase legislation table
        // We only need the identifiers to construct the Congress API URL
        JsonNode savedBills = webClient.get()
            .uri(supabaseUrl + "/rest/v1/legislation?select=congress,bill_type,bill_number")
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (savedBills == null || !savedBills.isArray() || savedBills.isEmpty()) {
            logger.info("No bills found in the database to update.");
            return;
        }

        // 2. Loop through each bill and fetch details from Congress API
        for (JsonNode savedBill : savedBills) {
            String congress = savedBill.has("congress") && !savedBill.get("congress").isNull() 
                              ? savedBill.get("congress").asText() : null;
            String type = savedBill.has("bill_type") ? savedBill.get("bill_type").asText() : null;
            String number = savedBill.has("bill_number") ? savedBill.get("bill_number").asText() : null;

            // Skip if we are missing the required IDs to hit the Congress API
            if (congress == null || type == null || number == null) continue;

            String detailUri = String.format("%s/bill/%s/%s/%s?format=json&api_key=%s", 
                                             congressUrl, congress, type, number, apiKey);

            try {
                JsonNode detailRoot = webClient.get()
                    .uri(detailUri)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

                if (detailRoot != null && detailRoot.has("bill")) {
                    JsonNode billData = detailRoot.get("bill");
                    
                    // Extract the new fields safely
                    String policyArea = billData.has("policyArea") && billData.get("policyArea").has("name")
                                        ? billData.get("policyArea").get("name").asText() : "Unknown";
                                        
                    String dateIntroduced = billData.has("introducedDate") 
                                            ? billData.get("introducedDate").asText() : null;
                    
                    // Combine Action Date and Text for a useful current status
                    String currentStatus = null;
                    if (billData.has("latestAction")) {
                        JsonNode action = billData.get("latestAction");
                        String actionDate = action.has("actionDate") ? action.get("actionDate").asText() : "";
                        String actionText = action.has("text") ? action.get("text").asText() : "";
                        currentStatus = actionDate + " - " + actionText;
                    }

                    // 3. Update the existing row in Supabase
                    patchLegislationDetails(congress, type, number, policyArea, dateIntroduced, currentStatus);
                }
            } catch (Exception e) {
                logger.error("Failed to fetch details for Bill {} {}: {}", type, number, e.getMessage());
            }
        }
    } catch (Exception e) {
        logger.error("Error running detailed update process: {}", e.getMessage());
    }
}

private void patchLegislationDetails(String congress, String type, String number, 
                                     String policyArea, String dateIntroduced, String currentStatus) {
    Map<String, Object> body = new java.util.HashMap<>();
    if (policyArea != null) body.put("policy_area", policyArea);
    if (dateIntroduced != null) body.put("date_introduced", dateIntroduced);
    if (currentStatus != null) body.put("current_status", currentStatus);

    if (body.isEmpty()) return; // Nothing to update

    // Supabase allows us to update specific rows using query parameters (eq = equals)
    String updateUri = String.format("%s/rest/v1/legislation?congress=eq.%s&bill_type=eq.%s&bill_number=eq.%s",
                                     supabaseUrl, congress, type, number);

    try {
        webClient.patch() // IMPORTANT: Using PATCH to update an existing row, not POST
            .uri(updateUri)
            .header("apikey", supabaseKey)
            .header("Authorization", "Bearer " + supabaseKey)
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        logger.info("Successfully updated details for Bill {} #{}", type, number);
    } catch (Exception e) {
        logger.error("Failed to patch database for Bill {} #{}: {}", type, number, e.getMessage());
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