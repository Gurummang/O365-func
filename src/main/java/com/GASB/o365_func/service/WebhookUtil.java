package com.GASB.o365_func.service;

import com.GASB.o365_func.repository.WorkSpaceConfigRepo;
import com.nimbusds.jwt.JWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookUtil {

    @Value("{jwt.secret}")
    private String JWT_SECRET;

    private final WorkSpaceConfigRepo workSpaceConfigRepo;


    // 토큰을 가져오는 부분
    private String getToken(int id) {
        try {
            return workSpaceConfigRepo.findTokenById(id).orElse(null);
        } catch (Exception e) {
            log.error("Error while fetching token from db", e);
            return null;
        }
    }

    // 구독을 생성하는 부분
    private void createSubscription(int id) {
        String token = getToken(id);
        if (token == null) {
            log.error("Token is null");
            return;
        }
        if (!tokenValidation(token)) {
            log.error("Token is invalid");
            return;
        }
        // 구독 생성 로직
    }

    // 구독을 삭제하는 부분

    // 리프레시 토큰을 이용해 토큰을 갱신하는 부분

    // 델타 api를 이용해 변경된 파일을 가져오는 부분

    //토큰 검증하는 부분
    private boolean tokenValidation(String token) {
        // o365는 토큰이 jwt형식이다, jwt를 검증하는 로직을 작성해야한다.
        Claims claims = extractAllClaims(token);
        Date exp = claims.getExpiration();

        if (exp.before(new Date())) {
            log.error("Token is expired");
            // 갱신 코드 추가
            return false;
        }
        return true;
    }

    // 토큰을 파싱하는 부분
    public Claims extractAllClaims(String token) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
