package com.github.dfauth.jwt_jaas.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class TestCase {

    private static final Logger logger = LoggerFactory.getLogger(TestCase.class);

    @Test
    public void testCreateToken() {

    }

    @Test
    public void testGenerateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.genKeyPair();
            byte[] publicKey = keyPair.getPublic().getEncoded();
            StringBuffer pubKeyString = new StringBuffer();
            for (int i = 0; i < publicKey.length; ++i) {
                pubKeyString.append(Integer.toHexString(0x0100 + (publicKey[i] & 0x00FF)).substring(1));
            }
            logger.info("publicKey: "+pubKeyString);
            byte[] privateKey = keyPair.getPrivate().getEncoded();
            StringBuffer priKeyString = new StringBuffer();
            for (int i = 0; i < privateKey.length; ++i) {
                priKeyString.append(Integer.toHexString(0x0100 + (privateKey[i] & 0x00FF)).substring(1));
            }
            logger.info("privateKey: "+priKeyString);
            Assert.assertNotEquals(pubKeyString, priKeyString);
        } catch (NoSuchAlgorithmException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGenerateKeyPairViaFactory() {
        KeyPair keyPair = KeyPairFactory.createKeyPair("RSA", 2048);
        String priKeyString = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String pubKeyString = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        Assert.assertNotEquals(pubKeyString, priKeyString);
        logger.info("publicKey: "+pubKeyString);
        logger.info("privateKey: "+priKeyString);
    }
}
