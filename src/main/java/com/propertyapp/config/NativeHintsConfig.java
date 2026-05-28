package com.propertyapp.config;

import com.propertyapp.enums.DiscoveryCategory;
import com.propertyapp.enums.GroupStatus;
import com.propertyapp.enums.MembershipRole;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.stream.Stream;

/**
 * Registers reflection/proxy/resource hints for third-party classes that are not
 * auto-detected by Spring Boot's AOT engine.  This class has zero effect in JVM
 * mode — hints are consumed only during the native-image compile step.
 *
 * Covered:
 *  - JTS geometry types  → Hibernate Spatial maps the PostGIS geography columns
 *  - JJWT impl classes   → Jackson deserialises JWT claims at parse time
 *  - App enums           → Jackson serialisation in JSON responses
 */
@Configuration
@ImportRuntimeHints(NativeHintsConfig.AppRuntimeHints.class)
public class NativeHintsConfig {

    static class AppRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            registerJtsGeometry(hints);
            registerJjwt(hints, classLoader);
            registerAppEnums(hints);
        }

        // ── JTS geometry (org.locationtech.jts) ──────────────────────────────
        // Hibernate Spatial uses these for geography(Point,4326) column mapping.
        private void registerJtsGeometry(RuntimeHints hints) {
            Stream.of(
                    Point.class,
                    Geometry.class,
                    GeometryCollection.class,
                    GeometryFactory.class,
                    LineString.class,
                    LinearRing.class,
                    MultiLineString.class,
                    MultiPoint.class,
                    MultiPolygon.class,
                    Polygon.class,
                    Coordinate.class,
                    CoordinateXY.class,
                    PrecisionModel.class,
                    WKBReader.class,
                    WKBWriter.class,
                    WKTReader.class,
                    WKTWriter.class
            ).forEach(type -> hints.reflection().registerType(type, MemberCategory.values()));
        }

        // ── JJWT implementation classes ───────────────────────────────────────
        // jjwt-impl uses reflection to instantiate claim maps and header classes
        // during JWT parsing; jjwt-jackson uses Jackson for (de)serialisation.
        private void registerJjwt(RuntimeHints hints, ClassLoader classLoader) {
            Stream.of(
                    "io.jsonwebtoken.impl.DefaultClaims",
                    "io.jsonwebtoken.impl.DefaultHeader",
                    "io.jsonwebtoken.impl.DefaultJwsHeader",
                    "io.jsonwebtoken.impl.DefaultJwtParser",
                    "io.jsonwebtoken.impl.DefaultJwtBuilder",
                    "io.jsonwebtoken.impl.compression.DeflateCompressionAlgorithm",
                    "io.jsonwebtoken.impl.compression.GzipCompressionAlgorithm",
                    "io.jsonwebtoken.impl.lang.Services",
                    "io.jsonwebtoken.jackson.io.JacksonDeserializer",
                    "io.jsonwebtoken.jackson.io.JacksonSerializer"
            ).forEach(name -> {
                try {
                    hints.reflection().registerType(
                            Class.forName(name, false, classLoader),
                            MemberCategory.values()
                    );
                } catch (ClassNotFoundException ignored) {
                    // class not present in this jjwt version — safe to skip
                }
            });
        }

        // ── App enums ─────────────────────────────────────────────────────────
        // Spring Boot AOT handles @Entity enums, but stand-alone enums used in
        // JSON request/response bodies need explicit registration.
        private void registerAppEnums(RuntimeHints hints) {
            Stream.of(
                    DiscoveryCategory.class,
                    GroupStatus.class,
                    MembershipRole.class
            ).forEach(type -> hints.reflection().registerType(type, MemberCategory.values()));
        }
    }
}
