package com.political.dto;

public record CongressMemberDto(
    String bioguideId, 
    String name, 
    String partyName, 
    String state, 
    String updateDate
) {}