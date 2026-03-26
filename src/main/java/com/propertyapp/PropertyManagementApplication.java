package com.propertyapp;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableCaching
@EnableAsync
@EnableRetry
@EnableTransactionManagement
@EnableAspectJAutoProxy
@OpenAPIDefinition(
		info = @Info(
				title = "Property Management System API",
				version = "1.0.0",
				description = "Production-ready REST API with Authentication, User & Property Management",
				contact = @Contact(
						name = "API Support",
						email = "support@propertyapp.com"
				),
				license = @License(
						name = "MIT License",
						url = "https://opensource.org/licenses/MIT"
				)
		)
)
@SecurityScheme(
		name = "Bearer Authentication",
		type = SecuritySchemeType.HTTP,
		bearerFormat = "JWT",
		scheme = "bearer"
)
public class PropertyManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(PropertyManagementApplication.class, args);

		String baseUrl = System.getenv("APP_BASE_URL");
		if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8080";

		System.out.println("\n" +
				"========================================================\n" +
				"   Property Management System - READY\n" +
				"   Swagger UI : " + baseUrl + "/swagger-ui.html\n" +
				"   API Docs   : " + baseUrl + "/api-docs\n" +
				"   Health     : " + baseUrl + "/actuator/health\n" +
				"========================================================\n"
		);
	}
}