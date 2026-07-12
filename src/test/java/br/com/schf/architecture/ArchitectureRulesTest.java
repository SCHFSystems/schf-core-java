package br.com.schf.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.filter.OncePerRequestFilter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "br.com.schf", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domainPackagesMustNotDependOnWeb = noClasses()
        .that()
        .resideInAnyPackage(
            "..audit..",
            "..organization..",
            "..user..",
            "..finance..",
            "..supplier..",
            "..account..",
            "..payable..",
            "..payment..",
            "..category..",
            "..migration..",
            "..shared..",
            "..security..")
        .and()
        .areNotAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        .and()
        .areNotAnnotatedWith(org.springframework.stereotype.Service.class)
        .and()
        .areNotAssignableTo(OncePerRequestFilter.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..");

    @ArchTest
    static final ArchRule configClassesCanExtendWebFilters = noClasses()
        .that()
        .resideInAnyPackage("..config..")
        .and()
        .areNotAssignableTo(OncePerRequestFilter.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..");
}
