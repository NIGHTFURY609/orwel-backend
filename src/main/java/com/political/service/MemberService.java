package com.political.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.political.dto.CongressApiResponse;
import com.political.entity.MemberEntity;

@Service
public class MemberService {

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

    private final WebClient webClient;
    
    @Value("${congress.api.url}")
    private String congressUrl;

    @Value("${CONGRESS_API_KEY}") // Fixed typo: removed extra 'c'
    private String apiKey;

    @Value("${SUPABASE_URL}")
    private String supabaseUrl;

    @Value("${SUPABASE_KEY}") // Fixed typo: removed extra 's'
    private String supabaseKey;

    public MemberService(WebClient.Builder webClientBuilder) {
        // Build a clean WebClient without a fixed base URL
        this.webClient = webClientBuilder.build();
    }

    public void fetchAndSaveCurrentMembers() {
        // Use the Congress Base URL + the endpoint
        String congressUri = congressUrl + "/member?format=json&limit=50&api_key=" + apiKey;

        try {
            logger.info("Fetching from: {}", congressUri);

            CongressApiResponse response = webClient.get()
                    .uri(congressUri)
                    .retrieve()
                    .bodyToMono(CongressApiResponse.class)
                    .block();

            if (response == null || response.members() == null || response.members().isEmpty()) {
                logger.warn("No members received from Congress API");
                return;
            }

            logger.info("Retrieved {} members from Congress API", response.members().size());

            // Map to Entity (Using your record logic)
            List<MemberEntity> entitiesToSave = response.members().stream().map(dto -> {
    MemberEntity entity = new MemberEntity();
    
    // Name Logic
    String rawName = dto.name();
    if (rawName != null && !rawName.trim().isEmpty()) {
        String[] parts = rawName.trim().split("\\s+", 2);
        entity.setLName(parts[0].replace(",", "").trim());
        entity.setFName(parts.length > 1 ? parts[1].trim() : "");
    } else {
        entity.setLName("Unknown");
        entity.setFName("Unknown");
    }

    entity.setTerritoryPrimary("USA");
    
    // Safety check for State
    entity.setTerritorySecondary(dto.state() != null ? dto.state() : "Unknown");
    
    // IMPORTANT: Fix for the null value in political_party
    String party = dto.partyName();
    entity.setPoliticalParty((party != null && !party.isEmpty()) ? party : "Independent");

    return entity;
}).collect(Collectors.toList());

            // POST to Supabase REST API
            logger.info("Sending data to Supabase...");
            
            String supabaseRestUri = supabaseUrl + "/rest/v1/members";

            webClient.post()
                    .uri(supabaseRestUri)
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation") // Supabase specific: returns the object
                    .bodyValue(entitiesToSave)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            logger.info("Successfully saved members to Supabase.");

        }
        
        catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
    logger.error("API Error! Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
} catch (Exception e) {
    logger.error("General Error: ", e);

    }
} }