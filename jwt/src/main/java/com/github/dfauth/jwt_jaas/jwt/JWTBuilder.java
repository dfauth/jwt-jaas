package com.github.dfauth.jwt_jaas.jwt;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.PrivateKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class JWTBuilder {

    private final PrivateKey privateKey;
    private final SignatureAlgorithm algorithm;
    private final String issuer;

    public JWTBuilder(String issuer, PrivateKey privateKey) {
        this(issuer, privateKey, "RS256");
    }

    public JWTBuilder(String issuer, PrivateKey privateKey, String algorithm) {
        this.issuer = issuer;
        this.privateKey = privateKey;
        this.algorithm = SignatureAlgorithm.forName(algorithm);
    }

    public ClaimBuilder forSubject(String subject) {
        return new ClaimBuilder(subject, this);
    }

    public static class ClaimBuilder {

        private final String subject;
        private final JWTBuilder signer;
        private ZonedDateTime nbf = ZonedDateTime.now();
        private ZonedDateTime expiry = ZonedDateTime.now().plusHours(1);
        private Map<String, Object> claims = new HashMap<>();

        private ClaimBuilder(String subject, JWTBuilder signer) {
            this.subject = subject;
            this.signer = signer;
        }

        public ClaimBuilder withClaim(String key, Object value) {
            this.claims.put(key, value);
            return this;
        }

        public ClaimBuilder withExpiry(ZonedDateTime expiry) {
            this.expiry = expiry;
            return this;
        }

        public ClaimBuilder withExpiry(long epochMillis) {
            this.expiry = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
            return this;
        }

        public String build() {
            JwtBuilder builder = Jwts.builder()
                    .setSubject(subject)
                    .setNotBefore(toDate(nbf))
                    .setIssuedAt(toDate(ZonedDateTime.now()))
                    .setExpiration(toDate(expiry))
                    .setIssuer(signer.issuer)
                    .signWith(signer.privateKey, signer.algorithm);
            claims.entrySet().stream().forEach(c -> builder.claim(c.getKey(), c.getValue()));
            return builder.compact();
        }

        private Date toDate(ZonedDateTime zdt) {
            return Date.from(zdt.toInstant());
        }
    }
}
