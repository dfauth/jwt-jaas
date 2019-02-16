package com.github.dfauth.jwt_jaas.jwt;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.jwt.JWTOptions;

import java.security.PrivateKey;
import java.util.Base64;
import java.util.stream.IntStream;


public class JWTGenerator {

    private final JWTAuth provider;
    private final PubSecKeyOptions options;

    public JWTGenerator(PrivateKey privateKey) {
        this(privateKey, "RS256");
    }

    public JWTGenerator(PrivateKey privateKey, String algorithm) {
        options =  new PubSecKeyOptions()
                        .setAlgorithm(algorithm)
                        .setSecretKey(asBase64(privateKey.getEncoded()));
        provider = JWTAuth.create(Vertx.vertx(),new JWTAuthOptions().addPubSecKey(options));
    }

    public String generateToken(Object obj, String algo) {
        JsonObject json = JsonObject.mapFrom(obj);
        return provider.generateToken(json, new JWTOptions().setAlgorithm(options.getAlgorithm()));
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
