package com.GASB.o365_func.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/auth")
public class MsOAuthTest {

    @GetMapping("/ms")
    public String handleMsOAuthRedirect(@RequestParam("code") String code, @RequestParam("state") String state) {
        log.info("Received OAuth authorization code: {}", code);
        log.info("Received state: {}", state);

        String accessToken = exchangeCodeForToken(code);

        return "OAuth authorization successful. Authorization code: " + code;
    }

    private String exchangeCodeForToken(String code) {
        // Exchange the authorization code for an access token
        return "access";
    }
}
