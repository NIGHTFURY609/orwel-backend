package com.political.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "members")
public class MemberEntity {

    // Primary key (exists in DB but never exposed in JSON)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mem_id")
    @JsonIgnore
    private Long memId;

    @Column(name = "f_name", nullable = false)
    private String fName;

    @Column(name = "l_name", nullable = false)
    private String lName;

    @Column(name = "territory_primary", nullable = false)
    private String territoryPrimary;

    @Column(name = "territory_secondary")
    private String territorySecondary;

    @Column(name = "political_party", nullable = false)
    private String politicalParty;

    public MemberEntity() {}

    // Getters
    public Long getMemId() { return memId; }

    @JsonProperty("f_name")
    public String getFName() { return fName; }

    @JsonProperty("l_name")
    public String getLName() { return lName; }

    @JsonProperty("territory_primary")
    public String getTerritoryPrimary() { return territoryPrimary; }

    @JsonProperty("territory_secondary")
    public String getTerritorySecondary() { return territorySecondary; }

    @JsonProperty("political_party")
    public String getPoliticalParty() { return politicalParty; }

    // Setters
    public void setFName(String fName) { this.fName = fName; }
    public void setLName(String lName) { this.lName = lName; }
    public void setTerritoryPrimary(String territoryPrimary) { this.territoryPrimary = territoryPrimary; }
    public void setTerritorySecondary(String territorySecondary) { this.territorySecondary = territorySecondary; }
    public void setPoliticalParty(String politicalParty) { this.politicalParty = politicalParty; }
}
