package com.github.dfauth.jwt_jaas.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import java.security.PublicKey;
import java.util.function.Function;


public class JWTVerifier {

    public Function<Claims, User> asUser = claims -> claims.get("user", UserBuilder.class).build();

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
