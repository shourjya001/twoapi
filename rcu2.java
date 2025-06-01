@Component("restClientUtility")
public class RestClientUtility {

    private static final Logger log = Logger.getLogger(RestClientUtility.class.getName());

    @Autowired
    DbeClientDao clientDao;

    @Autowired
    ObeclientProperties dbeclientProperties;

    @Autowired
    SendMaestroDataServiceImpl sendMaestroDataService;

    @Autowired
    private ApplicationConfig applicationConfig;

    // Your existing decompressData method remains the same
    private String decompressData(byte[] compressedBytes) {
        // ... existing implementation unchanged
    }

    // Your existing method for internal registrations
    public ResponseInternal sendPrimaryroleApi() throws IOException, JsonException {
        return sendRegistrationApi("internal");
    }

    // New method for external registrations
    public ResponseInternal sendExternalRegistrationApi() throws IOException, JsonException {
        return sendRegistrationApi("external");
    }

    // Common method that handles both internal and external
    private ResponseInternal sendRegistrationApi(String registrationType) throws IOException, JsonException {
        String scope = "api.get-third-parties.v1";
        String ClientId = dbeclientProperties.getMaestroClientId();
        String SecretId = dbeclientProperties.getMaestroSecretId();
        String access_token = generateSGconnectToken(scope, ClientId, SecretId);
        ResponseInternal responseObject = null;

        // static date
        String maestrodate = "?snapshotDate=2025-02-15";
        LocalDate today = LocalDate.now();
        String formattedDate = today.toString();
        // String maestrodate = "?snapshotDate="+formattedDate;

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

        System.out.println("requestObject---->" + headers);
        log.info("Making API call to Maestro API for " + registrationType + " Registration");

        // Determine the API URL based on registration type
        String apiUrl;
        if ("external".equals(registrationType)) {
            apiUrl = this.dbeclientProperties.getMaestroExternalRegistrationApiUrl() + maestrodate;
        } else {
            apiUrl = this.dbeclientProperties.getMaestrorelationshipApiUrl() + maestrodate;
        }

        ResponseEntity<byte[]> result = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                byte[].class
        );

        int status = result.getStatusCode().value();
        if (status == NULL || status == 401 || status == 402 || status == 403
                || status == 404 || status == 500 || status == 201 || status == 501) {
            String errorMessage = "API returned status code: " + status;
            System.err.println(errorMessage);
            log.error(errorMessage);
            sendMaestroDataService.sendErrorNotification("API Error", errorMessage);
            return null;
        }

        if (status == 200) {
            System.out.println("Successfully Data received from Maestro for " + registrationType);
            log.info("**Successfully Data received from Maestro API for " + registrationType + " Registration*");

            byte[] responseBody = result.getBody();
            String decompressedJson = decompressData(responseBody);
            
            if (decompressedJson == null) {
                log.error("Failed to decompress or read the response data");
                System.err.println("Failed to decompress or read the response data");
                return null;
            }

            try {
                ObjectMapper mapperObj = new ObjectMapper();
                mapperObj.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
                mapperObj.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                // Parse the JSON data into a list of InternalRegistrations
                List<InternalRegistrations> registrationsList = mapperObj.readValue(decompressedJson,
                        new TypeReference<List<InternalRegistrations>>() {});

                // Process the parsed data
                List<InternalRegistrations> processedRegistrations = new ArrayList<>();
                
                for (InternalRegistrations registration : registrationsList) {
                    // Pad BDRID if needed
                    if (registration.getEntityId() != null) {
                        String bdrid = registration.getEntityId();
                        if (bdrid.length() < 10) {
                            bdrid = String.format("%010d", Long.parseLong(bdrid));
                            registration.setEntityId(bdrid);
                        }
                    }
                    processedRegistrations.add(registration);
                }

                // Create the response object
                responseObject = new ResponseInternal();
                responseObject.setInternalRegistrations(processedRegistrations);
                // Set the registration type for DAO to use
                responseObject.setRegistrationType(registrationType);
                
                // Process and print the data in the required format
                processAndPrintEntityData(processedRegistrations);

                // print to check
                if (responseObject != null) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        String jsonResponse = mapper.writeValueAsString(responseObject);
                        System.out.println("Full API Response as JSON: " + jsonResponse);
                        // Optionally log it as well
                        log.info("Full API Response as JSON: " + jsonResponse);
                    } catch (JsonProcessingException e) {
                        System.err.println("Error converting response to JSON: " + e.getMessage());
                        log.error("Error converting response to JSON: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                return responseObject;
            } catch (JsonProcessingException e) {
                System.err.println("Error parsing JSON: " + e.getMessage());
                log.error("Error parsing JSON: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        } else {
            System.err.println("Unexpected status code: " + status);
            log.error("Unexpected status code: " + status);
        }

        return responseObject;
    }
}
