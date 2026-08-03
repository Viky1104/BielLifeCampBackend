package com.biel.lifecamp.system;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 系统服务 MVC 分层与包职责架构测试。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
class SystemServiceArchitectureTest {
    private static JavaClasses systemClasses;

    /**
     * 导入系统服务生产代码供架构规则检查。
     */
    @BeforeAll
    static void importProductionClasses() {
        systemClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.biel.lifecamp.system");
    }

    /**
     * 验证重构前的分层包不会重新引入。
     */
    @Test
    void formerLayerPackagesStayRemoved() {
        noClasses().should().resideInAnyPackage(
                "..interfaces..", "..application..", "..domain..", "..infrastructure..")
                .check(systemClasses);
    }

    /**
     * 验证控制器与异常处理器位于模板约定包中。
     */
    @Test
    void webComponentsFollowTheTemplate() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..controller..")
                .check(systemClasses);
        classes().that().areAnnotatedWith(RestControllerAdvice.class)
                .should().resideInAPackage("..common.exception..")
                .check(systemClasses);
    }

    /**
     * 验证服务实现和 MyBatis 数据访问接口遵循包命名模板。
     */
    @Test
    void serviceAndDaoRolesFollowTheTemplate() {
        classes().that().areAnnotatedWith(Service.class)
                .should().resideInAPackage("..service.impl..")
                .andShould().haveSimpleNameEndingWith("ServiceImpl")
                .check(systemClasses);
        classes().that().areAnnotatedWith(Mapper.class)
                .should().resideInAPackage("..dao..")
                .check(systemClasses);
    }

    /**
     * 验证配置类与配置属性类分包管理。
     */
    @Test
    void configurationAndPropertiesStaySeparated() {
        classes().that().areAnnotatedWith(Configuration.class)
                .should().resideInAPackage("..config..")
                .check(systemClasses);
        classes().that().areAnnotatedWith(ConfigurationProperties.class)
                .should().resideInAPackage("..config.properties..")
                .check(systemClasses);
    }

    /**
     * 验证控制层不能越过服务层访问 DAO，DAO 也不能反向依赖上层。
     */
    @Test
    void controllerAndDaoDoNotBypassTheServiceBoundary() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..dao..")
                .check(systemClasses);
        noClasses().that().resideInAPackage("..dao..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..controller..", "..service..", "..manager..")
                .check(systemClasses);
    }
}
