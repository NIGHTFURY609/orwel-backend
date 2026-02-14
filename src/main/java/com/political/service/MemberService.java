package com.political.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.political.entity.MemberEntity;

@Service
public class MemberService {
    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);
    private final WebClient webClient;

    @Value("${congress.api.url}") private String congressUrl;
    @Value("${CONGRESS_API_KEY}") private String apiKey;
    @Value("${SUPABASE_URL}") private String supabaseUrl;
    @Value("${SUPABASE_KEY}") private String supabaseKey;

    public MemberService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void fetchAndLinkMembers() {
    String congressUri = congressUrl + "/member?format=json&limit=5&api_key=" + apiKey;
    
    JsonNode root = webClient.get().uri(congressUri).retrieve().bodyToMono(JsonNode.class).block();
    if (root == null || !root.has("members")) return;

    for (JsonNode memberNode : root.get("members")) {
        try {
            // CONSTRUCT A MAP INSTEAD OF SENDING THE ENTITY OBJECT
            // This guarantees mem_id is NOT in the JSON
            Map<String, Object> memberData = new java.util.HashMap<>();
            
            String rawName = memberNode.get("name").asText();
            String[] parts = rawName.split("\\s+", 2);
            memberData.put("l_name", parts[0].replace(",", "").trim());
            memberData.put("f_name", parts.length > 1 ? parts[1].trim() : "");
            memberData.put("territory_primary", "USA");
            memberData.put("territory_secondary", memberNode.get("state").asText());
            memberData.put("political_party", memberNode.has("partyName") ? memberNode.get("partyName").asText() : "Independent");

            // Save Member and GET BACK the generated mem_id
            JsonNode savedMember = webClient.post()
                    .uri(supabaseUrl + "/rest/v1/members")
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation") 
                    .bodyValue(memberData) // Send the MAP
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (savedMember != null && savedMember.isArray() && !savedMember.isEmpty()) {
                int newMemId = savedMember.get(0).get("mem_id").asInt();
                
                String chamber = "Unknown";
                if (memberNode.has("terms") && memberNode.get("terms").has("item")) {
                    chamber = memberNode.get("terms").get("item").get(0).get("chamber").asText();
                }

                int govId = mapChamberToGovId(chamber);
                linkMemberToGov(newMemId, govId);
            }
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            logger.error("Supabase Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("Failed to process member: {}", e.getMessage());
        }
    }
}

    private int mapChamberToGovId(String chamber) {
        if (chamber.contains("House")) return 2;  // US House
        if (chamber.contains("Senate")) return 1; // US Senate
        return 0; // Or handle as error
    }

    private void linkMemberToGov(int memId, int govId) {
        if (govId == 0) return;
        
        Map<String, Integer> link = Map.of("gov_id", govId, "mem_id", memId);
        
        webClient.post()
                .uri(supabaseUrl + "/rest/v1/gov_mem")
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Content-Type", "application/json")
                .bodyValue(link)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(res -> logger.info("Linked mem_id {} to gov_id {}", memId, govId));
    }

    private MemberEntity mapToEntity(JsonNode node) {
        MemberEntity entity = new MemberEntity();
        String rawName = node.get("name").asText();
        String[] parts = rawName.split("\\s+", 2);
        entity.setLName(parts[0].replace(",", "").trim());
        entity.setFName(parts.length > 1 ? parts[1].trim() : "");
        entity.setTerritoryPrimary("USA");
        entity.setTerritorySecondary(node.get("state").asText());
        entity.setPoliticalParty(node.has("partyName") ? node.get("partyName").asText() : "Independent");
        return entity;
    }
}