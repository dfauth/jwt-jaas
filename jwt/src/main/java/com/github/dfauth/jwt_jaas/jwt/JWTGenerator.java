package com.github.dfauth.jwt_jaas.jwt;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class JWTGenerator {


    private final PrivateKey privateKey;
    private final SignatureAlgorithm algorithm;

    public JWTGenerator(PrivateKey privateKey) {
        this(privateKey, "RS256");
    }

    public JWTGenerator(PrivateKey privateKey, String algorithm) {
        this.privateKey = privateKey;
        this.algorithm = SignatureAlgorithm.forName(algorithm);
    }

    public String generateToken(String subject, String key, Object value) {
        return generateToken(subject, new AbstractMap.SimpleEntry(key, value));
    }

    public String generateToken(String subject, Map.Entry<String, Object>... claims) {
        LocalDateTime now = LocalDateTime.now();
        JwtBuilder builder = Jwts.builder()
                .setSubject(subject)
                .setNotBefore(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .setIssuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(now.plusHours(1).atZone(ZoneId.systemDefault()).toInstant()))
                .setIssuer("me")
                .signWith(privateKey, algorithm);
        Stream.of(claims).forEach(c -> builder.claim(c.getKey(), c.getValue()));
        return builder.compact();
    }


    public static String asBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String asHex(byte[] bytes) {
        return IntStream.range(0, bytes.length)
                .map(i -> bytes[i])
                .map(b -> 0x0100 + (b & 0x00FF))
                .mapToObj(b -> Integer.toHexString(b).substring(1))
                .reduce("", (acc, s) -> acc+s);
    }

}
