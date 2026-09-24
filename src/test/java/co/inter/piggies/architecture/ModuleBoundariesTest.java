package co.inter.piggies.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundariesTest {

    private static final String ROOT = "co.inter.piggies";
    private static final String COORDINATOR = ROOT + ".coordinator..";
    private static final String RESERVE = ROOT + ".reserve..";
    private static final String MERCHANT = ROOT + ".merchant..";
    private static final String COORDINATOR_LOCAL_ADAPTERS = ROOT + ".coordinator.infra.local..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void reserveDoesNotDependOnOtherModules() {
        noClasses().that().resideInAPackage(RESERVE)
                .should().dependOnClassesThat().resideInAnyPackage(COORDINATOR, MERCHANT)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void merchantDoesNotDependOnOtherModules() {
        noClasses().that().resideInAPackage(MERCHANT)
                .should().dependOnClassesThat().resideInAnyPackage(COORDINATOR, RESERVE)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void onlyCoordinatorLocalAdaptersReachOtherModules() {
        noClasses().that().resideInAPackage(COORDINATOR)
                .and().resideOutsideOfPackage(COORDINATOR_LOCAL_ADAPTERS)
                .should().dependOnClassesThat().resideInAnyPackage(RESERVE, MERCHANT)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void localAdaptersUseOnlyFacades() {
        noClasses().that().resideInAPackage(COORDINATOR_LOCAL_ADAPTERS)
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".reserve.domain..", ROOT + ".reserve.infra..", ROOT + ".reserve.web..",
                        ROOT + ".merchant.domain..", ROOT + ".merchant.infra..", ROOT + ".merchant.web..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void coordinatorCoreDependsOnlyOnItsPorts() {
        noClasses().that().resideInAnyPackage(ROOT + ".coordinator.domain..", ROOT + ".coordinator.orchestration..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".coordinator.infra..", ROOT + ".coordinator.web..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
