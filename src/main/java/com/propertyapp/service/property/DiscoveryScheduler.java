package com.propertyapp.service.property;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryScheduler {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void refreshDiscoveryCache() {

        log.info("Starting Discovery Cache Refresh...");

        jdbcTemplate.execute("DELETE FROM discovery_cache");

        // A️ POPULAR (7-day weighted)
        jdbcTemplate.execute("""
                INSERT INTO discovery_cache
                                                               (user_id, category, city, property_id, score, rank, updated_at)
                                                           SELECT *
                                                           FROM (
                                                               SELECT\s
                                                                   0::BIGINT as user_id,
                                                                   'POPULAR' as category,
                                                                   p.city,
                                                                   t.property_id,
                                                                   t.score,
                                                                   ROW_NUMBER() OVER (
                                                                       PARTITION BY p.city
                                                                       ORDER BY t.score DESC
                                                                   ) as rank,
                                                                   NOW() as updated_at
                                                               FROM (
                                                                   SELECT\s
                                                                       property_id,
                                                                       SUM(
                                                                           CASE interaction_type
                                                                               WHEN 'VIEW' THEN 1
                                                                               WHEN 'SHORTLIST' THEN 5
                                                                               WHEN 'INQUIRY' THEN 10
                                                                               ELSE 0
                                                                           END
                                                                       ) as score
                                                                   FROM user_property_interactions
                                                                   WHERE created_at > NOW() - INTERVAL '7 days'
                                                                   GROUP BY property_id
                                                               ) t
                                                               JOIN properties p ON p.id = t.property_id
                                                               WHERE p.status = 'ACTIVE'
                                                                 AND p.deleted_at IS NULL
                                                           ) ranked
                                                           WHERE ranked.rank <= 100;
            """);

        // B️⃣ USER RECOMMENDATION (Safe simplified collaborative filtering)
        jdbcTemplate.execute("""
                INSERT INTO discovery_cache
                                              (user_id, category, city, property_id, score, rank, updated_at)
                                              SELECT *
                                              FROM (
                                                  SELECT\s
                                                      ui.user_id,
                                                      'RECOMMENDED' as category,
                                                      p.city,
                                                      ui.property_id,
                                                      COUNT(*) as score,
                                                      ROW_NUMBER() OVER (
                                                          PARTITION BY ui.user_id, p.city
                                                          ORDER BY COUNT(*) DESC
                                                      ) as rank,
                                                      NOW() as updated_at
                                                  FROM user_property_interactions ui
                                                  JOIN properties p ON p.id = ui.property_id
                                                  WHERE ui.user_id IS NOT NULL
                                                    AND ui.created_at > NOW() - INTERVAL '7 days'
                                                    AND p.status = 'ACTIVE'
                                                    AND p.deleted_at IS NULL
                                                  GROUP BY ui.user_id, p.city, ui.property_id
                                              ) ranked
                                              WHERE ranked.rank <= 100;
                """);

        log.info("Discovery Cache Refresh Completed.");
    }
}
