package com.GASB.o365_func.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JwtDecoder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String decodeJwtPayload(String jwt) {
        // JWT는 "header.payload.signature" 형태이므로, "."으로 분리
        String[] jwtParts = jwt.split("\\.");

        if (jwtParts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        // 페이로드는 두 번째 부분
        String payload = jwtParts[1];

        // Base64Url 디코딩
        byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    public static Map<String, Object> parseJwtPayload(String jwt) {
        try {
            String decodedPayload = decodeJwtPayload(jwt);
            // JSON 문자열을 Map으로 변환
            return objectMapper.readValue(decodedPayload, Map.class);
        } catch (NullPointerException e) {
            log.error("Error parsing JWT payload: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT payload");
        } catch (IllegalArgumentException e) {
            log.error("Error parsing JWT payload: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT payload");
        } catch (Exception e) {
            log.error("Error parsing JWT payload: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT payload");
        }
    }

    public static Date getExpDate(String jwt) {
        Map<String, Object> payload = parseJwtPayload(jwt);

        // exp 필드는 보통 long 타입이므로 long으로 캐스팅
        long exp = ((Number) payload.get("exp")).longValue();
        log.info("exp: {}", exp);

        // 만료일 반환 (초 단위에서 밀리초 단위로 변환)
        return new Date(exp * 1000);
    }
}

