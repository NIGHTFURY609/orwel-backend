package com.political.dto;

import java.util.List;

public record CongressApiResponse(List<CongressMemberDto> members) {}