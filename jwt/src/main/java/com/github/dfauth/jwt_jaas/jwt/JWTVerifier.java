package com.github.dfauth.jwt_jaas.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class JWTVerifier {

    private static final Logger logger = LoggerFactory.getLogger(JWTVerifier.class);
    private static final Function<Map<String, Object>,RoleBuilder> RBM = t -> new RoleBuilder().withSystemId((String) (t.get("systemId"))).withRoleName((String) (t.get("rolename")));


    public Function<Claims, User> asUser = claims -> {
        Set<RoleBuilder> roles = ((List<Map<String, Object>>) Optional.ofNullable(claims.get("roles", List.class)).orElse(Collections.emptyList())).stream().map(RBM).collect(Collectors.toSet());
        String userId = claims.getSubject();
        Date expiry = claims.getExpiration();
        return new UserBuilder().withUserId(userId).withExpiry(expiry.toInstant()).withRoles(roles).build();
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
            logger.error(e.getMessage(), e);
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

            public RuntimeException getCause() {
                return this.e;
            }

            public static <T> TokenAuthentication<T> with(RuntimeException e) {
                return new Failure(e);
            }
        }

    }

    private class MapStream extends ArrayList<Map<String, Object>> {
        public Stream<Map<String, Object>> stream() {
            return Stream.empty();
        }
    }
}
