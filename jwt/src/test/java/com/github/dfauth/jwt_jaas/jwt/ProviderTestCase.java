package com.github.dfauth.jwt_jaas.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.security.KeyPair;

import static com.github.dfauth.jwt_jaas.jwt.Role.role;


public class ProviderTestCase {

    private static final Logger logger = LoggerFactory.getLogger(ProviderTestCase.class);

    @Test
    public void testIt() {
        KeyPair testKeyPair = KeyPairFactory.createKeyPair("RSA", 2048);
        JWTBuilder jwtBuilder = new JWTBuilder("me", testKeyPair.getPrivate());
        User user = User.of("fred", role("test:admin"), role("test:user"));
        String token = jwtBuilder.forSubject(user.getUserId()).withClaim("roles", user.getRoles()).build();

        JWTVerifier jwtVerifier = new JWTVerifier(testKeyPair.getPublic());
        jwtVerifier.authenticateToken(token, claims -> {
            logger.info("claims: "+claims);
            return null;
        });
    }

}
