import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Prints a throwaway Firebase service-account file for the Docker build's
 * training start (see train.sh). The key is generated fresh on every build,
 * exists only for the few seconds training takes, and never signs anything:
 * training stops before the service handles a single request.
 */
public class TrainingCredentials {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        // JSON spells a line break as backslash-n, so the PEM's breaks are written that way.
        String key = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                .replace("\n", "\\n");
        System.out.println("{\"type\":\"service_account\",\"project_id\":\"aot-training\","
                + "\"private_key_id\":\"training\",\"private_key\":\"-----BEGIN PRIVATE KEY-----\\n"
                + key + "\\n-----END PRIVATE KEY-----\\n\","
                + "\"client_email\":\"training@aot-training.iam.gserviceaccount.com\","
                + "\"client_id\":\"0\",\"token_uri\":\"https://oauth2.googleapis.com/token\"}");
    }
}
