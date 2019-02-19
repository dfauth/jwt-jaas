package com.github.dfauth.jwt_jaas.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
import java.security.PublicKey;
import java.util.function.Function;


public class JWTVerifier {

    public Function<Claims, User> asUser = claims -> {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String str = mapper.writeValueAsString(claims.get("user"));
            return mapper.readValue(str, UserBuilder.class).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    private final PublicKey publicKey;

    public JWTVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public <T> T authenticateToken(String token, Function<Claims, T> f) {
        Jws<Claims> claims = Jwts.parser()
                .setSigningKey(publicKey)
                .requireIssuer("me")
                .parseClaimsJws(token);
        return f.apply(claims.getBody());
    }
}
