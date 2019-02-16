package com.github.dfauth.jwt_jaas.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;


public class KeyPairFactory {

    private static final Logger logger = LoggerFactory.getLogger(KeyPairFactory.class);

    public static KeyPair createKeyPair(String algorithm, int size) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
            keyGen.initialize(size);
            KeyPair keyPair = keyGen.genKeyPair();
            KeyFactory factory = KeyFactory.getInstance(algorithm);
            RSAPublicKeySpec publicKeySpec = factory.getKeySpec(keyPair.getPublic(),
                    RSAPublicKeySpec.class);
            RSAPrivateKeySpec privateKeySpec = factory.getKeySpec(keyPair.getPrivate(),
                    RSAPrivateKeySpec.class);
            PrivateKey privateKey = factory.generatePrivate(privateKeySpec);
            PublicKey publicKey = factory.generatePublic(publicKeySpec);
            return new KeyPair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
