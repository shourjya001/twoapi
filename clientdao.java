package com.socgen.riskweb.dao;

import com.socgen.riskweb.Model.InternalRegistrations;
import com.socgen.riskweb.Model.Registration;
import com.socgen.riskweb.Model.ResponseInternal;
import com.socgen.riskweb.Model.SubBookingEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Component
public class RiskwebClientDaoImpl implements RiskwebClientDao {

    private static final int BATCH_SIZE = 1000;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final int LOG_INTERVAL = 50000;
    private static final Logger log = Logger.getLogger(RiskwebClientDaoImpl.class.getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AtomicInteger totalInserted = new AtomicInteger(0);

    @Transactional
    public void savePrimaryroleApi(ResponseInternal internalRatingsEventResponse, String tableName) {
        List<InternalRegistrations> internalRegistrationsList = internalRatingsEventResponse.getInternalRegistrations();

        int totalSize = internalRegistrationsList.size();
        log.info("Total records to process for table " + tableName + ": " + totalSize);

        try {
            this.jdbcTemplate.update(AppQueries.QRY_PRIMARYROLE_TRUNCATE.value(tableName), new Object[]{});
            log.info("Truncated table " + tableName);
        } catch (Exception e) {
            log.severe("Failed to truncate table " + tableName + ": " + e.getMessage());
            throw e;
        }

        if (totalSize > 0) {
            long startTime = System.currentTimeMillis();
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < totalSize; i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, totalSize);
                List<InternalRegistrations> batch = internalRegistrationsList.subList(i, end);
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> processBatch(batch, tableName), executorService);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executorService.shutdown();

            logProgress(totalSize, startTime);
            System.out.println("Completed processing for table " + tableName + ". Total inserted: " + totalInserted.get());
            log.info("Completed processing for table " + tableName + ". Total inserted: " + totalInserted.get());
        }
    }

    private void logProgress(int totalSize, long startTimeMillis) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        long elapsedSeconds = elapsedTime / 1000);
        double recordsPerSecond = totalSize / (elapsedSeconds > 0 ? elapsedSeconds : 1);
        log.info("Inserted: " + totalSize + " records in " + elapsedTime + " ms. Rate: " + String.format("%.2f", recordsPerSecond) + " records/s");
    }

    private void processBatch(List<InternalRegistrations> registrations, String tableName) {
        int inserted = executeBatch(registrations, tableName);
        int newTotal = totalInserted.addAndGet(inserted);
        if (newTotal % LOG_INTERVAL == 0) {
            logProgress(newTotal, System.currentTimeMillis());
        }
    }

    private int executeBatch(List<InternalRegistrations> registrations, String tableName) {
        log.info("Started batch insertion for table " + tableName);

        List<Object[]> batchParams = new ArrayList<>();

        for (InternalRegistrations internalReg : registrations) {
            String entityId = internalReg.getEntityId();
            List<Registration> registrationList = internalReg.getRegistrations();

            if (registrationList != null) {
                for (Registration reg : registrationList) {
                    String code = reg.getCode();
                    List<SubBooking> subBookingList = registrationList.getSubBookingList();

                    if (subBookingList == null || subBookingList.isEmpty()) {
                        batchParams.add(new Object[]{entityId, code, null});
                    } else {
                        for (SubBooking subBooking : subBookingList) {
                            String subBookingId = subBooking.getSubbookingId();
                            batchParams.add(new Object[]{entityId, code, subBookingId});
                        }
                    }
                }
            }
        }

        int[] updateCounts = jdbcTemplate.batchUpdate(
                AppQueries.QRY_SAVE_PRIMARYROLE.value(tableName),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Object[] params = batchParams.get(i);
                        ps.setString(1, params[0], String.class); // entityId
                        ps.setString(2, params[1], String.class); // code
                        if (params[2] == null) {
                            ps.setNull(3, java.sql.Types.VARCHAR); // subBookingId
                        } else {
                            ps.setString(3, params[2], String.class);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return batchParams.size();
                    }
                }
            );

        return Arrays.stream(updateCounts).sum();
    }
}
