public class RiskwebMaestroStartup {
    @Autowired
    static RiskwebClientDao clientDao;
    
    private static final Logger LOGGER = Logger.getLogger(RiskwebMaestroStartup.class);
    
    public static void main(String[] args) throws SQLException, ParseException {
        AnnotationConfigApplicationContext annotationConfigApplicationContext = // your context initialization
        
        RiskwebClientService riskwebClientService = (RiskwebClientService) annotationConfigApplicationContext.getBean("riskwebClientService");
        RestClientUtility restClientUtility = (RestClientUtility) annotationConfigApplicationContext.getBean("restClientUtility");
        
        try {
            // Process internal registrations
            ResponseInternal internalResponse = restClientUtility.sendPrimaryroleApi();
            if (internalResponse != null) {
                System.out.println("Calling the sendPrimaryroleApi method for internal");
                LOGGER.info("Calling the sendPrimaryroleApi method for internal");
                riskwebClientService.savePrimaryroleApi(internalResponse);
            }
            
            // Process external registrations
            ResponseInternal externalResponse = restClientUtility.sendExternalRegistrationApi();
            if (externalResponse != null) {
                System.out.println("Calling the sendExternalRegistrationApi method for external");
                LOGGER.info("Calling the sendExternalRegistrationApi method for external");
                riskwebClientService.savePrimaryroleApi(externalResponse); // Same method, different table based on registrationType
            }
            
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
