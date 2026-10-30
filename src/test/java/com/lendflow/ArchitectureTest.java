package com.lendflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Layers are matched as com.lendflow.<module>.<layer>, because one module is itself called "application". */
@AnalyzeClasses(packages = "com.lendflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses().that().resideInAPackage("com.lendflow.*.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..",
            "org.apache.kafka..", "io.github.resilience4j..", "io.micrometer..");

    @ArchTest
    static final ArchRule domainKnowsNothingOfUseCasesOrAdapters = noClasses().that()
        .resideInAPackage("com.lendflow.*.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("com.lendflow.*.application..", "com.lendflow.*.adapter..",
            "com.lendflow.config..");

    @ArchTest
    static final ArchRule useCasesDoNotReachIntoAdapters = noClasses().that()
        .resideInAPackage("com.lendflow.*.application..")
        .should().dependOnClassesThat().resideInAnyPackage("com.lendflow.*.adapter..", "com.lendflow.config..");

    @ArchTest
    static final ArchRule onlyAdaptersTalkToKafkaAndHttp = noClasses().that()
        .resideOutsideOfPackages("com.lendflow.*.adapter..", "com.lendflow.config..", "com.lendflow.shared.web..")
        .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..",
            "org.springframework.web..");
}
