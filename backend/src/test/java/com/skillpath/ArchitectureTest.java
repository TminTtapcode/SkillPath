package com.skillpath;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.skillpath");

    @Test
    void controllersDoNotDependOnPersistence() {
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnFrameworks() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void knowledgePersistenceRemainsPrivate() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..knowledge.infrastructure.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..knowledge.infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void assessmentPersistenceRemainsPrivate() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..assessment.infrastructure.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..assessment.infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void progressPersistenceRemainsPrivate() {
        noClasses().that().resideOutsideOfPackage("..progress.infrastructure.persistence..")
                .should().dependOnClassesThat().resideInAPackage("..progress.infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void reviewPersistenceRemainsPrivate() {
        noClasses().that().resideOutsideOfPackage("..review.infrastructure.persistence..")
                .should().dependOnClassesThat().resideInAPackage("..review.infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void learningPersistenceRemainsPrivate() {
        noClasses().that().resideOutsideOfPackage("..learning.infrastructure.persistence..")
                .should().dependOnClassesThat().resideInAPackage("..learning.infrastructure.persistence..")
                .check(classes);
    }

    @Test
    void plannerUsesOnlyOtherModulesApplicationContracts() {
        noClasses().that().resideInAPackage("..planner..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..goal.infrastructure..", "..knowledge.infrastructure..",
                        "..progress.infrastructure..", "..review.infrastructure..",
                        "..learning.infrastructure..", "..goal.api..", "..knowledge.api..",
                        "..progress.api..", "..review.api..", "..learning.api..")
                .check(classes);
        noClasses().that().resideInAPackage("..planner..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.skillpath.learning.application.LearningStore")
                .check(classes);
        noClasses().that().resideOutsideOfPackage("..planner.infrastructure.persistence..")
                .should().dependOnClassesThat()
                .resideInAPackage("..planner.infrastructure.persistence..")
                .check(classes);
    }
}
