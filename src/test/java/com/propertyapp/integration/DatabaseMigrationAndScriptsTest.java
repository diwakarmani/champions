package com.propertyapp.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Migrations and owner-run scripts against a real database, no application context (ADR-0015). */
@Testcontainers
class DatabaseMigrationAndScriptsTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(AbstractPostgisIntegrationTest.POSTGIS);

    private PGSimpleDataSource dataSource;
    private JdbcTemplate jdbc;
    private int nextDisplayOrder;

    @BeforeEach
    void freshDatabase() throws Exception {
        String name = "t_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(source(POSTGRES.getDatabaseName())).execute("CREATE DATABASE " + name);
        dataSource = source(name);
        jdbc = new JdbcTemplate(dataSource);
    }

    private PGSimpleDataSource source(String database) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[] {POSTGRES.getHost()});
        ds.setPortNumbers(new int[] {POSTGRES.getMappedPort(5432)});
        ds.setDatabaseName(database);
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("1").load();
    }

    private void runFile(String path) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(path));
        }
    }

    /** Creates the pre-Flyway schema the way production has it: V1 applied, no Flyway history. */
    private void existingDatabaseWithoutFlywayHistory() throws Exception {
        runFile("src/main/resources/db/migration/V1__baseline.sql");
    }

    @Test
    void anEmptyDatabaseGetsTheWholeSchemaFromMigrations() {
        var result = flyway().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(flyway().info().all().length);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name IN ('users','properties','discovery_cache',"
                + "'user_property_interactions','listing_reassignments')", Long.class)).isEqualTo(5L);
    }

    @Test
    void anExistingDatabaseIsBaselinedWithoutRunningV1AndKeepsItsData() throws Exception {
        existingDatabaseWithoutFlywayHistory();
        jdbc.update("INSERT INTO roles (id, name) VALUES (1, 'REALTOR')");

        var result = flyway().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(flyway().info().applied().length - 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE type = 'BASELINE' "
                + "AND version = '1'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE script = 'V1__baseline.sql'",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Long.class)).isEqualTo(1L);
        assertThat(flyway().validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void v2AcceptsDatabasesWhereInquirerWasRequiredAndNotificationTypesWereChecked() throws Exception {
        existingDatabaseWithoutFlywayHistory();
        jdbc.execute("ALTER TABLE inquiries ALTER COLUMN inquirer_id SET NOT NULL");
        assertThat(isNullable("inquiries", "inquirer_id")).isFalse();

        flyway().migrate();

        assertThat(isNullable("inquiries", "inquirer_id")).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname = 'notifications_type_check'",
                Long.class)).isZero();
    }

    @Test
    void theAgentViewOnlyContainsActiveUndeletedRealtors() {
        flyway().migrate();
        long realtorRole = role("REALTOR");
        long buyerRole = role("BUYER");
        long active = user("active@example.com", true, realtorRole);
        user("inactive@example.com", false, realtorRole);
        long deleted = user("deleted@example.com", true, realtorRole);
        jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", deleted);
        user("buyer@example.com", true, buyerRole);
        long suspended = user("suspended@example.com", true, realtorRole);
        jdbc.update("UPDATE users SET agent_suspended_at = now() WHERE id = ?", suspended);

        assertThat(jdbc.queryForList("SELECT user_id FROM active_listing_agents", Long.class)).containsExactly(active);

        jdbc.update("UPDATE users SET agent_suspended_at = NULL WHERE id = ?", suspended);
        assertThat(jdbc.queryForList("SELECT user_id FROM active_listing_agents", Long.class))
                .containsExactlyInAnyOrder(active, suspended);
    }

    @Test
    void theDatabaseItselfAllowsOnlyOneOpenApplicationPerUser() {
        flyway().migrate();
        long applicant = user("applicant@example.com", true, role("BUYER"));
        long admin = user("admin@example.com", true, role("SUPER_ADMIN"));
        long other = user("other@example.com", true, role("REALTOR"));

        long first = application(applicant);
        assertThatThrownBy(() -> application(applicant)).isInstanceOf(DataIntegrityViolationException.class);
        application(other); // someone else is unaffected

        // A decided application no longer blocks a new one, and must say who decided.
        assertThatThrownBy(() -> jdbc.update("UPDATE agent_applications SET status = 'APPROVED' WHERE id = ?", first))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE agent_applications SET status = 'REJECTED', reviewed_by = ?, "
                + "reviewed_at = now() WHERE id = ?", admin, first))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE agent_applications SET status = 'REJECTED', reviewed_by = ?, reviewed_at = now(), "
                + "rejection_reason = 'No' WHERE id = ?", admin, first);
        application(applicant);
    }

    @Test
    void v5HidesTestAndIndiaMarketAmenitiesAndRenamesTheRestWithoutDeletingAny() throws Exception {
        existingDatabaseWithoutFlywayHistory();
        String insert = "INSERT INTO property_amenities (id, version, name, category, is_active, display_order) "
                + "VALUES (?, 0, ?, ?, true, ?)";
        jdbc.update(insert, 1, "Lift", "ACCESS", 1);
        jdbc.update(insert, 2, "Piped Gas", "MODERN", 2);
        jdbc.update(insert, 3, "LiveVerifyOmit-1", "Test", 3);
        jdbc.update(insert, 4, "Swimming Pool", "RECREATION", 4);
        jdbc.update(insert, 5, "Gymnasium", "RECREATION", 5);
        // The US name already exists: the old row is left alone rather than breaking the unique name.
        jdbc.update(insert, 6, "Fitness Center", "RECREATION", 6);

        flyway().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_amenities", Long.class)).isEqualTo(6L);
        assertThat(jdbc.queryForList("SELECT name FROM property_amenities WHERE is_active ORDER BY id", String.class))
                .containsExactly("Elevator", "Swimming Pool", "Gymnasium", "Fitness Center");
        assertThat(jdbc.queryForList("SELECT name FROM property_amenities WHERE NOT is_active ORDER BY id", String.class))
                .containsExactly("Piped Gas", "LiveVerifyOmit-1");
    }

    @Test
    void migrationKeepsExistingUsersAsTheyWere() throws Exception {
        flyway().migrate();
        long agent = user("agent@example.com", true, role("REALTOR"));

        assertThat(jdbc.queryForObject("SELECT agent_suspended_at FROM users WHERE id = ?",
                java.sql.Timestamp.class, agent)).isNull();
        assertThat(jdbc.queryForObject("SELECT license_public FROM users WHERE id = ?", Boolean.class, agent)).isFalse();
    }

    @Test
    void retireSellersScriptAddsBuyerKeepsSellerAndIsSafeToRunTwice() throws Exception {
        flyway().migrate();
        long seller = role("SELLER");
        long buyer = role("BUYER");
        long realtor = role("REALTOR");
        long former = user("former@example.com", true, seller);
        long already = user("both@example.com", true, seller);
        grant(already, buyer);
        long agent = user("agent@example.com", true, realtor);

        runFile("scripts/s2_retire_sellers.sql");
        runFile("scripts/s2_retire_sellers.sql");

        assertThat(rolesOf(former)).containsExactlyInAnyOrder("SELLER", "BUYER");
        assertThat(rolesOf(already)).containsExactlyInAnyOrder("SELLER", "BUYER");
        assertThat(rolesOf(agent)).containsExactly("REALTOR");
    }

    @Test
    void usTypesScriptMovesListingsSwitchesOldNamesOffAndIsSafeToRunTwice() throws Exception {
        flyway().migrate();
        long owner = user("agent@example.com", true, role("REALTOR"));
        long newHouse = type("Single Family Home");
        long apartment = type("Apartment");
        long villa = type("Villa");
        long bhk = subType("2 BHK", apartment);
        long luxuryVilla = subType("Luxury Villa", villa);
        long loft = subType("Loft", apartment);
        long movedListing = property(owner, villa, luxuryVilla);
        long bhkListing = property(owner, apartment, bhk);
        long untouched = property(owner, apartment, loft);

        runFile("scripts/s2_us_property_types.sql");
        runFile("scripts/s2_us_property_types.sql");

        assertThat(jdbc.queryForObject("SELECT property_type_id FROM properties WHERE id = ?", Long.class, movedListing))
                .isEqualTo(newHouse);
        assertThat(jdbc.queryForObject("SELECT property_sub_type_id FROM properties WHERE id = ?", Long.class, movedListing))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT property_sub_type_id FROM properties WHERE id = ?", Long.class, bhkListing))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT property_sub_type_id FROM properties WHERE id = ?", Long.class, untouched))
                .isEqualTo(loft);
        assertThat(jdbc.queryForObject("SELECT is_active FROM property_types WHERE id = ?", Boolean.class, villa)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_active FROM property_types WHERE id = ?", Boolean.class, apartment)).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_active FROM property_sub_types WHERE id = ?", Boolean.class, bhk)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_active FROM property_sub_types WHERE id = ?", Boolean.class, loft)).isTrue();
    }

    private long application(long userId) {
        return jdbc.queryForObject("""
                INSERT INTO agent_applications (version, user_id, status, license_number, license_state,
                                                brokerage_name, created_at)
                VALUES (0, ?, 'PENDING', 'X-1', 'TX', 'Test', now()) RETURNING id""", Long.class, userId);
    }

    private boolean isNullable(String table, String column) {
        return "YES".equals(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = ? AND column_name = ?", String.class, table, column));
    }

    private long role(String name) {
        return jdbc.queryForObject("INSERT INTO roles (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private void grant(long userId, long roleId) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private long user(String email, boolean active, long roleId) {
        long id = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, first_name, last_name, email_verified, mobile_verified,
                                   is_active, is_locked, created_at, updated_at)
                VALUES (?, 'x', 'T', 'U', true, false, ?, false, now(), now()) RETURNING id
                """, Long.class, email, active);
        grant(id, roleId);
        return id;
    }

    private long type(String name) {
        return jdbc.queryForObject("""
                INSERT INTO property_types (name, is_active, display_order)
                VALUES (?, true, 1) RETURNING id""", Long.class, name);
    }

    private long subType(String name, long typeId) {
        return jdbc.queryForObject("""
                INSERT INTO property_sub_types (name, property_type_id, is_active, display_order)
                VALUES (?, ?, true, ?) RETURNING id""", Long.class, name, typeId, ++nextDisplayOrder);
    }

    private long property(long owner, long typeId, long subTypeId) {
        return jdbc.queryForObject("""
                INSERT INTO properties (owner_id, property_type_id, property_sub_type_id, title, listing_type, price,
                                        address_line1, city, state, country, status, created_at, updated_at)
                VALUES (?, ?, ?, 'T', 'SALE', 1, 'A', 'C', 'S', 'US', 'ACTIVE', now(), now()) RETURNING id
                """, Long.class, owner, typeId, subTypeId);
    }

    private java.util.List<String> rolesOf(long userId) {
        return jdbc.queryForList("SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                + "WHERE ur.user_id = ?", String.class, userId);
    }
}
