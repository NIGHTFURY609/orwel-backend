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

    // Changed to POST since we are writing data to the DB
    @PostMapping("/sync")
    public String syncMembers() {
        memberService.fetchAndSaveCurrentMembers();
        return "Successfully fetched members from API and saved to the database!";
    }
}