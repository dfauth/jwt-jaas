package com.github.dfauth.jwt_jaas.jwt;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;

import static com.github.dfauth.jwt_jaas.jwt.JWTGenerator.asBase64;


public class JWTVerifier {

    private static final Logger logger = LoggerFactory.getLogger(JWTVerifier.class);

    private final JWTAuth provider;
    private final PubSecKeyOptions options;

    public JWTVerifier(PublicKey publicKey) {
        this(publicKey, "RS256");
    }

    public JWTVerifier(PublicKey publicKey, String algorithm) {
        options =  new PubSecKeyOptions()
                        .setAlgorithm(algorithm)
                        .setPublicKey(asBase64(publicKey.getEncoded())
                );
        provider = JWTAuth.create(Vertx.vertx(),new JWTAuthOptions().addPubSecKey(options));
    }

    public void authenticateToken(String token) {
        provider.authenticate(new JsonObject().put("jwt", token), new Handler<AsyncResult<User>>() {
            @Override
            public void handle(AsyncResult<User> event) {
                event.map(u -> {
                    logger.info("u is: "+u);
                    return null;
                });
            }
        });
//            r.map(a -> {
//                logger.info("a is: "+a);
//            });
//        });
    }
}
