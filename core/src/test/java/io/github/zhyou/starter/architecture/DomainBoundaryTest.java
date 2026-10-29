package io.github.zhyou.starter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class DomainBoundaryTest {
  @Test
  void domainRemainsIndependentOfFrameworksAndAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..application..",
            "..persistence..",
            "..cli..",
            "..api..",
            "..web..",
            "jakarta..",
            "io.quarkus..")
        .check(new ClassFileImporter().importPackages("io.github.zhyou.starter.domain"));
  }
}
