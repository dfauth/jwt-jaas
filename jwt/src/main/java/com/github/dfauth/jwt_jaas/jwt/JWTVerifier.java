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

    public <T> TokenAuthentication<T> authenticateToken(String token, Function<Claims, T> f) {
        try {
            Jws<Claims> claims = Jwts.parser()
                    .setSigningKey(publicKey)
                    .requireIssuer("me")
                    .parseClaimsJws(token);
            return TokenAuthentication.Success.with(f.apply(claims.getBody()));
        } catch (RuntimeException e) {
            return TokenAuthentication.Failure.with(e);
        }
    }

    public static class TokenAuthentication<T> {

        public static class Success<T> extends TokenAuthentication<T> {

            private final T payload;

            public Success(T payload) {
                this.payload = payload;
            }

            public static <T> TokenAuthentication<T> with(T payload) {
                return new Success(payload);
            }

            public T getPayload() {
                return payload;
            }
        }

        public static class Failure<T> extends TokenAuthentication<T> {

            private final RuntimeException e;

            public Failure(RuntimeException e) {
                this.e = e;
            }

            public static <T> TokenAuthentication<T> with(RuntimeException e) {
                return new Failure(e);
            }
        }

    }
}
