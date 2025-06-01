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
    public void savePrimaryroleApi(ResponseInternal internalRatingsEventResponse) {
        List<InternalRegistrations> internalRegistrationsList = internalRatingsEventResponse.getInternalRegistrations();
        String registrationType = internalRatingsEventResponse.getRegistrationType();
        
        // Determine which queries to use based on registration type
        String truncateQuery;
        String insertQuery;
        String tableName;
        
        if ("external".equals(registrationType)) {
            truncateQuery = AppQueries.QRY_EXTERNAL_TRUNCATE.value();
            insertQuery = AppQueries.QRY_SAVE_EXTERNAL.value();
            tableName = "TMAESNUMIPL_EXTERNAL";
        } else {
            truncateQuery = AppQueries.QRY_PRIMARYROLE_TRUNCATE.value();
            insertQuery = AppQueries.QRY_SAVE_PRIMARYROLE.value();
            tableName = "TMAESNUMIPL_INTERNAL";
        }

        int totalSize = internalRegistrationsList.size();
        log.info("Total " + registrationType + " records to process: " + totalSize);

        // Truncate the table before inserting new data
        try {
            this.jdbcTemplate.update(truncateQuery, new Object[]{});
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
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> processBatch(batch, insertQuery), executorService);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executorService.shutdown();

            logProgress(totalInserted.get(), startTime);
            log.info("Completed processing " + registrationType + ". Total inserted: " + totalInserted.get());
        }
    }

    private void processBatch(List<InternalRegistrations> batch, String insertQuery) {
        int inserted = executeBatch(batch, insertQuery);
        int newTotal = totalInserted.addAndGet(inserted);
        if (newTotal % LOG_INTERVAL == 0) {
            logProgress(newTotal, System.currentTimeMillis());
        }
    }

    private int executeBatch(List<InternalRegistrations> batch, String insertQuery) {
        log.info("Started inserting records using query: " + insertQuery);

        // Collect all records to insert
        List<Object[]> batchParams = new ArrayList<>();

        for (InternalRegistrations internalReg : batch) {
            String entityId = internalReg.getEntityId();
            List<Registration> registrations = internalReg.getRegistrations();

            if (registrations != null) {
                for (Registration reg : registrations) {
                    String code = reg.getCode();
                    List<SubBookingEntity> subBookingEntities = reg.getSubBookingEntities();

                    // If subBookingEntities is null or empty, insert a record with null subbookingId
                    if (subBookingEntities == null || subBookingEntities.isEmpty()) {
                        batchParams.add(new Object[]{entityId, code, null});
                    } else {
                        // Insert a record for each subbookingId
                        for (SubBookingEntity subBooking : subBookingEntities) {
                            String subbookingId = subBooking.getSubbookingId();
                            batchParams.add(new Object[]{entityId, code, subbookingId});
                        }
                    }
                }
            }
        }

        // Execute batch update
        int[] updateCounts = jdbcTemplate.batchUpdate(
                insertQuery,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Object[] params = batchParams.get(i);
                        ps.setString(1, (String) params[0]); // entityId
                        ps.setString(2, (String) params[1]); // code
                        if (params[2] == null) {
                            ps.setNull(3, java.sql.Types.VARCHAR); // subbookingId
                        } else {
                            ps.setString(3, (String) params[2]); // subbookingId
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

    private void logProgress(int totalInserted, long startTime) {
        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - startTime) / 1000;
        double recordsPerSecond = totalInserted / (double) Math.max(1, elapsedSeconds);
        log.info("Inserted " + totalInserted + " records. Rate: " + String.format("%.2f", recordsPerSecond) + " records/second");
    }
}
