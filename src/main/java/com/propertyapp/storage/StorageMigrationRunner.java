package com.propertyapp.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * One-off job: start the app with {@code STORAGE_MIGRATE=dry-run} (then {@code apply}) and it runs
 * {@link LegacyImageMigration} and exits (0 when no problems, 2 otherwise). Runbook: RUNBOOK_S13.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.storage.migrate:off}' == 'dry-run' or '${app.storage.migrate:off}' == 'apply'")
public class StorageMigrationRunner implements ApplicationRunner {

    private final LegacyImageMigration migration;
    private final ConfigurableApplicationContext context;
    private final org.springframework.core.env.Environment env;

    @Override
    public void run(ApplicationArguments args) {
        boolean apply = "apply".equals(env.getProperty("app.storage.migrate"));
        LegacyImageMigration.Report report = migration.run(apply);
        int code = report.problems().isEmpty() ? 0 : 2;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
