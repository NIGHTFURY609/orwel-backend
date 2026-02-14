package com.political.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.political.service.MemberService;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/sync")
    public String syncMembers() {
        // Updated the method call to match the new service method name
        memberService.fetchAndLinkMembers();
        return "Successfully fetched members from API and saved to the database!";
    }
}