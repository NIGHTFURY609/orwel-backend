package com.political.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.political.service.BillTagService;

@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillTagService billTagService;

    public BillController(BillTagService billTagService) {
        this.billTagService = billTagService;
    }

    @PostMapping("/generate-tags")
    public String generateTags() {
        billTagService.processBillTags();
        return "Processing bills for tags...";
    }
}