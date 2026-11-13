package com.propertyapp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureRulesTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.propertyapp");
    private static final JavaClasses TESTS = new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .importPackages("com.propertyapp");

    /** Existing debt. This list may only shrink: new controllers go through a service. */
    private static final Set<String> CONTROLLERS_USING_REPOSITORIES_TODAY = Set.of(
            "AdminAnalyticsController", "FavoriteController", "InquiryController", "RealtorController");

    @Test
    void springTestsAlwaysRunOnTheTestProfile_soTheyCannotReachARealDatabase() {
        classes().that().areAnnotatedWith(SpringBootTest.class)
                .or().areAssignableTo(com.propertyapp.integration.AbstractPostgisIntegrationTest.class)
                .should().beMetaAnnotatedWith(ActiveProfiles.class)
                .orShould().beAnnotatedWith(ActiveProfiles.class)
                .orShould().beAssignableTo(com.propertyapp.integration.AbstractPostgisIntegrationTest.class)
                .check(TESTS);
    }

    @Test
    void noNewControllerTalksToARepositoryDirectly() {
        Set<String> actual = new TreeSet<>();
        PRODUCTION.stream()
                .filter(c -> c.isAnnotatedWith(RestController.class))
                .filter(c -> c.getDirectDependenciesFromSelf().stream()
                        .anyMatch(d -> d.getTargetClass().getPackageName().startsWith("com.propertyapp.repository")))
                .forEach(c -> actual.add(c.getSimpleName()));
        assertThat(actual).isSubsetOf(CONTROLLERS_USING_REPOSITORIES_TODAY);
    }

    @Test
    void servicesDoNotDependOnControllers() {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .check(PRODUCTION);
    }

    @Test
    void entitiesDoNotDependOnServicesOrControllers() {
        noClasses().that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
                .check(PRODUCTION);
    }
}
