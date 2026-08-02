package com.saryom.rideservice.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Initializes the Firebase Admin SDK for real deployments (any profile other
 * than {@code dev}). Credentials come from {@code FIREBASE_SERVICE_ACCOUNT_JSON}
 * (raw JSON) or Application Default Credentials.
 */
@Configuration
@Profile("!dev")
class FirebaseConfig {

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(resolveCredentials())
                .build();
        return FirebaseApp.initializeApp(options);
    }

    private GoogleCredentials resolveCredentials() throws IOException {
        String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            var stream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
            return GoogleCredentials.fromStream(stream);
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
