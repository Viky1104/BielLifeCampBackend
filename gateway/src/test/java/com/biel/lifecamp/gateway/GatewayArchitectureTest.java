package com.biel.lifecamp.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Configuration;

/**
 * 守护架构重构后网关各包的职责边界。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
class GatewayArchitectureTest {
    private static JavaClasses gatewayClasses;

    /**
     * 导入网关生产代码供架构规则检查。
     */
    @BeforeAll
    static void importProductionClasses() {
        gatewayClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.biel.lifecamp.gateway");
    }

    /**
     * 验证过滤器和配置类始终位于约定包中。
     */
    @Test
    void filtersAndConfigurationStayInTheirPackages() {
        classes().that().implement(GlobalFilter.class)
                .should().resideInAPackage("..filter..")
                .check(gatewayClasses);
        classes().that().areAnnotatedWith(Configuration.class)
                .should().resideInAPackage("..config..")
                .check(gatewayClasses);
        classes().that().areAnnotatedWith(ConfigurationProperties.class)
                .should().resideInAPackage("..config..")
                .check(gatewayClasses);
    }

    /**
     * 验证根包只保留应用启动类。
     */
    @Test
    void onlyTheBootstrapClassRemainsInTheRootPackage() {
        classes().that().resideInAPackage("com.biel.lifecamp.gateway")
                .should().haveSimpleName("GatewayApplication")
                .check(gatewayClasses);
    }
}
