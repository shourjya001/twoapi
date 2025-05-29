package com.socgen.riskweb;

import com.socgen.riskweb.Model.ResponseInternal;
import com.socgen.riskweb.Model.ResponseSensitivity;
import com.socgen.riskweb.dao.RiskwebClientDao;
import com.socgen.riskweb.util.RestClientUtility;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.logging.Logger;

public class RiskwebMaestroStartup {

    private static final Logger LOGGER = Logger.getLogger(RiskwebMaestroStartup.class.getName());

    // Removed static @Autowired to avoid injection issues
    public static void main(String[] args) throws SQLException, ParseException {
        AnnotationConfigApplicationContext annotationConfigApplicationContext = 
            new AnnotationConfigApplicationContext("com.socgen.riskweb");

        RestClientUtility restClientUtility = annotationConfigApplicationContext.getBean(RestClientUtility.class);
        RiskwebClientDao clientDao = annotationConfigApplicationContext.getBean(RiskwebClientDao.class);
        ObjectMapper mapperObj = new ObjectMapper();

        try {
            // Fetch and save internal registrations
            ResponseInternal internalResponse = restClientUtility.sendPrimaryroleApi("internalRegistration", "Internal");
            if (internalResponse != null) {
                System.out.println("Calling sendPrimaryroleApi for Internal");
                LOGGER.info("Calling sendPrimaryroleApi for Internal");
                clientDao.savePrimaryroleApi(internalResponse, "TMAESNUMIPL");
            }

            // Fetch and save external registrations
            ResponseInternal externalResponse = restClientUtility.sendPrimaryroleApi("externalRegistration", "External");
            if (externalResponse != null) {
                System.out.println("Calling sendPrimaryroleApi for External");
                LOGGER.info("Calling sendPrimaryroleApi for External");
                clientDao.savePrimaryroleApi(externalResponse, "WK_MAESTRO_EXTROLE_DBE");
            }

            // Existing sensitivity API call (unchanged)
            ResponseSensitivity sensitivityResponse = restClientUtility.sendInternalRatingsApi();
            if (sensitivityResponse != null) {
                System.out.println("Calling sendInternalRatingsApi");
                LOGGER.info("Calling sendInternalRatingsApi");
                clientDao.saveSensitivityApi(sensitivityResponse);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            LOGGER.severe("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            annotationConfigApplicationContext.close();
        }
    }
}
