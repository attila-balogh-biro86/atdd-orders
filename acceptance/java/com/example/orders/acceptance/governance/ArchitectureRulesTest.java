package com.example.orders.acceptance.governance;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Governance rules that protect the executable specification from being "gamed".
 * Lives under acceptance/ so implementation PRs (and agents) cannot weaken it.
 */
class ArchitectureRulesTest {

    private static JavaClasses all;

    @BeforeAll
    static void importClasses() {
        all = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages("com.example.orders");
    }

    @Test
    void specificationTalksToTheSystemOnlyThroughDrivers() {
        noClasses().that().resideInAPackage("..acceptance..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.example.orders.domain..",
                        "com.example.orders.application..",
                        "com.example.orders.web..")
                .because("acceptance tests must verify behaviour at the boundary, "
                        + "not by calling (or mirroring) production internals")
                .check(all);
    }

    @Test
    void stepDefinitionsAreThinGlue() {
        classes().that().resideInAPackage("..acceptance.steps..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("..acceptance.steps..", "..acceptance.dsl..", "io.cucumber..", "java..")
                .because("logic hidden in glue code is invisible to spec reviewers")
                .check(all);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("com.example.orders.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..")
                .check(all);
    }

    @Test
    void productionCodeNeverDependsOnTestCode() {
        noClasses().that().resideInAnyPackage("com.example.orders.domain..",
                        "com.example.orders.application..", "com.example.orders.web..")
                .should().dependOnClassesThat().resideInAPackage("..acceptance..")
                .check(all);
    }
}
