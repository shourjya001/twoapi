import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

public class VaultService {

    private static final String VAULT_URL = "https://your-vault-server/v1/";   // 🔹 Vault base URL
    private static final String VAULT_NAMESPACE = "secret/data/";              // 🔹 Vault namespace
    private static final String VAULT_NAME = "myapp/config";                   // 🔹 Path of your secret

    private static final String ENV = System.getenv("env") != null ? System.getenv("env") : "";
    private static final String ROLE_ID = "your-role-id";       // 🔹 Replace with your Vault role_id
    private static final String SECRET_ID = "your-secret-id";   // 🔹 Replace with your Vault secret_id

    private String vaultToken;
    private long expiresIn;
    private JSONObject pgData;
    private HttpURLConnection conn;

    private static Logger logger = LoggerFactory.getLogger(VaultService.class);

    // 🔹 Separate mappings for each file
    Map<String, String> propertiesMappings = new HashMap<>();
    Map<String, String> yamlMappings = new HashMap<>();

    public static void main(String[] args) {
        VaultService vault = new VaultService();

        // 🔹 Fetch secrets from Vault
        vault.getSecretsList();

        // 🔹 Initialize both mappings
        vault.initiatePropertiesMapping();
        vault.initiateYamlMapping();

        // 🔹 Update both files
        vault.updatePropertiesFile();
        vault.updateYamlFile();

        System.out.println("✅ application.properties and application.yml updated successfully");
    }

    // -------------------- FETCH SECRETS FROM VAULT ---------------------
    public JSONObject getSecretsList() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Vault-Token", getAccessToken());

            HttpURLConnection request = buildGetRequest(
                    new URL(VAULT_URL + VAULT_NAMESPACE + VAULT_NAME),
                    headers
            );

            String response = getResponse(request);
            JSONObject jsonObject = new JSONObject(response);

            // Vault returns data.data structure
            JSONObject data = new JSONObject(jsonObject.get("data").toString());
            pgData = new JSONObject(data.get("data").toString());

            System.out.println("pquser===>" + pgData.getString("Pg_user"));
            System.out.println("pgpass===>" + pgData.getString("pg_pass"));
            System.out.println("clientId===>" + pgData.getString("client_id"));
            System.out.println("clientSecret===>" + pgData.getString("client_secret"));

            return pgData;

        } catch (Exception e) {
            e.printStackTrace();
            logger.error("VAULT ERROR: Failed to retrieve secrets");
        }
        return null;
    }

    // -------------------- INIT MAPPINGS ---------------------
    private void initiatePropertiesMapping() {
        try {
            propertiesMappings.put("datasource_username", pgData.getString("Pg_user"));
            propertiesMappings.put("datasource_password", pgData.getString("pg_pass"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void initiateYamlMapping() {
        try {
            yamlMappings.put("client_id", pgData.getString("client_id"));
            yamlMappings.put("client_secret", pgData.getString("client_secret"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // -------------------- UPDATE FILES ---------------------
    private void updatePropertiesFile() {
        File file = new File("src/main/resources/application.properties");
        updateFileWithMappings(file, propertiesMappings);
    }

    private void updateYamlFile() {
        File file = new File("src/main/resources/application.yml");
        updateFileWithMappings(file, yamlMappings);
    }

    // -------------------- COMMON HELPER ---------------------
    private void updateFileWithMappings(File file, Map<String, String> mappings) {
        try {
            List<String> newLines = new ArrayList<>();
            List<String> lines = Files.readAllLines(file.toPath());

            for (String line : lines) {
                String newLine = line;
                for (String key : mappings.keySet()) {
                    if (line.contains(key)) {
                        newLine = newLine.replaceAll(key, mappings.get(key));
                    }
                }
                newLines.add(newLine);
            }

            file.delete();
            Files.write(file.toPath(), newLines, StandardOpenOption.CREATE);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------- VAULT AUTH (AppRole) ---------------------
    private String getAccessToken() throws Exception {
        if (vaultToken == null || getExpirationTimeInSeconds() <= 60) {
            String response = getToken();
            if (response != null && !response.isEmpty()) {
                JSONObject jsonObject = new JSONObject(response);
                JSONObject clientToken = new JSONObject(jsonObject.get("auth").toString());
                vaultToken = clientToken.getString("client_token");
            }
        }
        return vaultToken;
    }

    private String getToken() {
        try {
            Map<String, String> vaultRequestBody = new HashMap<>();
            vaultRequestBody.put("role_id", ROLE_ID);
            vaultRequestBody.put("secret_id", SECRET_ID);

            Map<String, String> headers = new HashMap<>();
            headers.put("X-Vault-Namespace", VAULT_NAMESPACE);
            headers.put("content-type", "application/json");

            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(vaultRequestBody);

            HttpURLConnection request = buildRequest(
                    "POST",
                    new URL(VAULT_URL + "auth/approle/login"),
                    headers,
                    requestBody
            );

            if (request.getResponseCode() == 200 || request.getResponseCode() == 201) {
                return getResponse(request);
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.error("VAULT ERROR: Vault Token Generation failed");
        }
        return "";
    }

    // -------------------- HTTP HELPERS ---------------------
    private HttpURLConnection buildRequest(String requestMethod, URL url, Map<String, String> headers, String content) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {}
            }};

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(requestMethod);

            logger.info(requestMethod + " request to: " + url);

            if (headers != null) {
                for (String header : headers.keySet()) {
                    conn.setRequestProperty(header, headers.get(header));
                }
            }

            if (content != null) {
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(content.getBytes());
                os.flush();
                os.close();
            }

        } catch (Exception e) {
            logger.info("Error:: " + e.getMessage());
        }
        return conn;
    }

    private HttpURLConnection buildGetRequest(URL url, Map<String, String> headers) throws IOException {
        return buildRequest("GET", url, headers, null);
    }

    private String getResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        logger.info("Response Code " + responseCode + " " + conn.getRequestMethod() + " " + conn.getURL());

        StringBuilder response = new StringBuilder();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        }
        return response.toString();
    }

    private long getExpirationTimeInSeconds() {
        if (expiresIn == 0) return -1;
        return expiresIn;
    }
}