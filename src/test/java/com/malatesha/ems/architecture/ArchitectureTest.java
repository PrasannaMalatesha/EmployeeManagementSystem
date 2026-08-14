package com.malatesha.ems.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module boundary rules from PRD §6, enforced at build time.
 *
 * <p>Rule 3 is the load-bearing one: it's what makes this a modular monolith
 * rather than a big ball of mud with feature-shaped folders. Everything
 * else is convention; rule 3 is architecture.
 *
 * <p>Rules pass vacuously in M0 because there is no code yet to violate
 * them. That is the point of writing them at empty-scaffold stage: the
 * first violation will be caught immediately.
 */
class ArchitectureTest {

    private static final String BASE = "com.malatesha.ems";

    /** Feature modules from PRD §6. Cross-cutting modules (config, common, security) are excluded. */
    private static final List<String> FEATURE_MODULES = List.of(
            "employee", "department", "leave", "attendance",
            "document", "notification", "audit"
    );

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    // allowEmptyShould(true) is required through M1 because there are no controllers,
    // services, or entities yet. Remove it in M2 once the employee module lands —
    // by then a stale rule that matches nothing genuinely IS a bug worth failing on.

    /** Rule 1: controllers speak DTOs, never entities. */
    @Test
    void controllers_do_not_depend_on_entities() {
        noClasses()
                .that().areAnnotatedWith(Controller.class)
                .or().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .as("Controllers must not import JPA entities — map to DTOs in the service layer")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** Rule 2: controllers go through a service, never a repository. */
    @Test
    void controllers_do_not_depend_on_repositories() {
        noClasses()
                .that().areAnnotatedWith(Controller.class)
                .or().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAnyPackage(BASE + "..repository..")
                .as("Controllers must not touch repositories directly — always via a service")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * Rule 3 (the important one): a module's {@code entity} and {@code repository}
     * packages are internal. Other modules must go through the public service
     * interface or react to a domain event.
     */
    @Test
    void module_internals_are_not_reachable_from_other_modules() {
        for (String module : FEATURE_MODULES) {
            String ownModulePackage = BASE + "." + module + "..";
            noClasses()
                    .that().resideOutsideOfPackage(ownModulePackage)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + "." + module + ".entity..",
                            BASE + "." + module + ".repository..")
                    .as("Module '" + module + "': entity and repository packages are private to the module")
                    .check(classes);
        }
    }

    /** Rule 4a: @Transactional never sits on a controller or repository class. */
    @Test
    void transactional_is_not_on_controllers_or_repositories() {
        noClasses()
                .that().resideInAnyPackage(BASE + "..controller..", BASE + "..repository..")
                .should().beAnnotatedWith(Transactional.class)
                .as("@Transactional belongs on services, never controllers or repositories")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** Rule 4b: @Transactional on a method must be in a service package. */
    @Test
    void transactional_methods_are_only_in_service_packages() {
        methods()
                .that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat().resideInAnyPackage(BASE + "..service..")
                .as("@Transactional methods live in service packages")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** Rule 5: no cyclic dependencies between feature modules. */
    @Test
    void modules_are_free_of_cycles() {
        SlicesRuleDefinition.slices()
                .matching(BASE + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
