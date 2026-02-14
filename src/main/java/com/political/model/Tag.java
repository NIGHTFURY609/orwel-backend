package com.political.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Tag(
    @JsonProperty("tag_name") String tagName
) {}