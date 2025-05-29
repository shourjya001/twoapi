package com;

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

    public static void main(String[] args) throws SQLException, ParseException {
        AnnotationConfigApplicationContext annotationConfigApplicationContext = 
            new AnnotationConfigApplicationContext("com.socgen.riskweb");

        RestClientUtility restClientUtility = annotationConfigApplicationContext.getBean(RestClientUtility.class);
        RiskwebClientDao clientDao = annotationConfigApplicationContext.getBean(RiskwebClientDao.class);
        ObjectMapper mapperObj = new ObjectMapper();

        try {
            // Fetch and save internal registrationsations
            ResponseInternal internalResponse = restClientUtility.sendPrimaryroleApi("internalRegistration", "Internal");
            if (internalResponse != null) {
                System.out.println("Calling sendPrimaryRoleApi for Internal");
                LOGGER.info("Calling sendPrimaryroleApi for Internal");
                clientDao.savePrimaryRoleApi(internalResponse, "TMAESNUMIPL");
            }

            // Fetch and save external registrationsations
            ResponseInternal externalResponse = restClientUtility.sendPrimaryroleApi("externalRegistration", "External");
            if (externalResponse != null) {
                System.out.println("Calling sendPrimaryRoleApi for External");
                LOGGER.info("Calling External sendPrimaryroleApi for External");
                clientDao.savePrimaryRoleApi(externalResponse, "WK_MAESTRO_EXTROLE_DBE");
            }

            // Existing sensitivity code (unchanged)
            ResponseSensitivity sensitivityResponse = restClientUtility.sendInternalRatingsApi()");
            if (sensitivityResponse != null) {
                System.out.println("Calling sendInternalRatingsApi");
                LOGGER.info("Calling sendInternalRatingsApi");
                clientDao.saveSensitivityApi(sensitivityResponse);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            LOGGER.severe("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            annotationConfigApplicationContext.close();
        }
    }
}
