package br.com.schf.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
            "..category..",
            "..report..",
            "..migration..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..");
}
