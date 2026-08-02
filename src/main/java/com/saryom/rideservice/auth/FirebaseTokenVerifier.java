package com.saryom.rideservice.auth;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Production {@link TokenVerifier} backed by the Firebase Admin SDK. */
@Component
@Profile("!dev")
public class FirebaseTokenVerifier implements TokenVerifier {

    private final FirebaseAuth firebaseAuth;

    FirebaseTokenVerifier(FirebaseApp firebaseApp) {
        this.firebaseAuth = FirebaseAuth.getInstance(firebaseApp);
    }

    @Override
    public VerifiedUser verify(String idToken) throws InvalidTokenException {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidTokenException("Missing bearer token");
        }
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken);
            return new VerifiedUser(token.getUid(), token.getEmail(), token.getName());
        } catch (FirebaseAuthException e) {
            throw new InvalidTokenException("Firebase rejected the ID token", e);
        }
    }
}
