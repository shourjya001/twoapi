package com.socgen.riskweb.util;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socgen.riskweb.Model.InternalRegistrations;
import com.socgen.riskweb.Model.ResponseInternal;
import com.socgen.riskweb.beans.RiskwebclientProperties;
import com.socgen.riskweb.dao.RiskwebClientDao;

import static java.lang.System.out;
import static java.sql.Types.NULL;

@Component("restClientUtility")
public class RestClientUtility {

    private static final Logger log = Logger.getLogger(RestClientUtility.class.getName());

    @Autowired
    RiskwebClientDao clientDao;

    @Autowired
    RiskwebclientProperties riskwebclientProperties;

    @Autowired
    SendMaestroDataServiceImpl sendMaestroDataService;

    @Autowired
    private ApplicationConfig applicationConfig;

    private String decompressData(byte[] compressedBytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
            GZIPInputStream gis = new GZIPInputStream(bis);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1098];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }

            gis.close();
            bos.close();

            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warning("GZIP decompression failed, trying Inflater: " + e.getMessage());
            System.out.println("GZIP decompression failed, trying Inflater: " + e.getMessage());

            try {
                Inflater inflater = new Inflater(true);
                inflater.setInput(compressedBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedBytes.length);
                byte[] buffer = new byte[9098];

                while (!inflater.finished()) {
                    int count = inflater.inflate(buffer);
                    outputStream.write(buffer, 0, count);
                }

                outputStream.close();
                inflater.end();

                return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            } catch (DataFormatException | IOException ex) {
                log.severe("Error decompressing content with Inflater: " + ex.getMessage());
                System.err.println("Error decompressing content with Inflater: " + ex.getMessage());
                ex.printStackTrace();
                System.out.println("Both decompression methods failed. Returning original data as string.");
                log.severe("Both decompression methods failed. Returning original data as string.");
                return new String(compressedBytes, StandardCharsets.UTF_8);
            }
        }
    }

    public ResponseInternal sendPrimaryroleApi(String endpoint, String apiType) throws IOException {
        String scope = "api.get-third-parties.v1";
        String clientId = riskwebclientProperties.getMaestroClientId();
        String secretId = riskwebclientProperties.getMaestroSecretId();
        String access_token = generateSGconnectToken(scope, clientId, secretId);
        ResponseInternal responseObject = null;

        String maestrodate = "?snapshotDate=2025-02-15";
        String baseUrl = riskwebclientProperties.getMaestrorelationshipApiUrl()
                .replace("/internalRegistration", "")
                .replace("/externalRegistration", "");
        String endpointUrl = baseUrl + "/" + endpoint + maestrodate;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + access_token);
        headers.set("content-Language", "en-US");
        headers.set("Host", "maestro-search-uat.fr.world.socgen");
        headers.set("Accept", "*/*");
        headers.set("content-type", "application/json");
        headers.set("accept", "application/json");
        headers.set("Accept-Encoding", "gzip, deflate");

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        System.out.println("Request headers for " + apiType + ": " + headers);
        log.info("Making API call to Maestro API for " + apiType + " at " + endpointUrl);

        try {
            ResponseEntity<byte[]> result = restTemplate.exchange(
                    endpointUrl,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            int status = result.getStatusCode().value();
            if (status == NULL || status == 401 || status == 402 || status == 403
                    || status == 404 || status == 500 || status == 201 || status == 501) {
                String errorMessage = "API returned status code for " + apiType + ": " + status;
                System.err.println(errorMessage);
                log.error(errorMessage);
                sendMaestroDataService.sendErrorNotification("API Error for " + apiType, errorMessage);
                return null;
            }

            if (status == 200) {
                System.out.println("Successfully received data from Maestro for " + apiType);
                log.info("**Successfully received data from Maestro API for " + apiType + "**");

                byte[] responseBody = result.getBody();
                String decompressedJson = decompressData(responseBody);
                
                if (decompressedJson == null) {
                    log.error("Failed to decompress or read the response data for " + apiType);
                    System.err.println("Failed to decompress or read the response data for " + apiType);
                    return null;
                }

                try {
                    ObjectMapper mapperObj = new ObjectMapper();
                    mapperObj.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
                    mapperObj.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    List<InternalRegistrations> registrationsList = mapperObj.readValue(decompressedJson,
                            new TypeReference<List<InternalRegistrations>>() {});

                    List<InternalRegistrations> processedRegistrations = new ArrayList<>();
                    for (InternalRegistrations registration : registrationsList) {
                        if (registration.getEntityId() != null) {
                            String bdrid = registration.getEntityId();
                            if (bdrid.length() < 10) {
                                bdrid = String.format("%010d", Long.parseLong(bdrid));
                                registration.setEntityId(bdrid);
                            }
                        }
                        processedRegistrations.add(registration);
                    }

                    responseObject = new ResponseInternal();
                    responseObject.setInternalRegistrations(processedRegistrations);
                    
                    processAndPrintEntityData(processedRegistrations);

                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        String jsonResponse = mapper.writeValueAsString(responseObject);
                        System.out.println("Full API Response as JSON for " + apiType + ": " + jsonResponse);
                        log.info("Full API Response as JSON for " + apiType + ": " + jsonResponse);
                    } catch (JsonProcessingException e) {
                        System.err.println("Error converting response to JSON for " + apiType + ": " + e.getMessage());
                        log.error("Error converting response to JSON for " + apiType + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    return responseObject;
                } catch (JsonProcessingException e) {
                    System.err.println("Error parsing JSON for " + apiType + ": " + e.getMessage());
                    log.error("Error parsing JSON for " + apiType + ": " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            } else {
                System.err.println("Unexpected status code for " + apiType + ": " + status);
                log.error("Unexpected status code for " + apiType + ": " + status);
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP error calling API for " + apiType + ": " + e.getMessage());
            log.error("HTTP error calling API for " + apiType + ": " + e.getMessage());
            return null;
        }

        return responseObject;
    }

   
}
