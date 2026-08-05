package com.biel.lifecamp.system.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * EHR 同步集成测试数据准备与断言 Mapper。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Mapper
public interface EhrSyncTestMapper {
    void deleteRoleAssignments();

    void deleteWechatProfiles();

    void deleteExternalIdentities();

    void deleteLocalCredentials();

    void deleteSyncIssues();

    long countSyncIssues();

    List<SyncIssueProjection> selectSyncIssues();

    void deleteEmployeeStages();

    void deleteSyncRuns();

    void deleteTaskLeases();

    void deleteEmployees();

    void insertLocalBootstrapEmployee();

    void insertLegacyEhrEmployee(@Param("ehrPersonId") String ehrPersonId,
                                 @Param("employeeNo") String employeeNo,
                                 @Param("displayName") String displayName);

    void resetIntegrationState();

    void deleteIntegrationState();

    List<EmployeeProjection> selectEmployees();

    int countEmployeeRoleAssignments();

    Boolean selectInitialSyncCompleted();

    String selectEmploymentStatusByEmployeeNo(String employeeNo);

    /**
     * 测试断言使用的员工投影。
     */
    record EmployeeProjection(Long id, String ehrPersonId, String employeeNo,
                              String displayName, String employmentStatus,
                              String accountStatus, String supervisorEmployeeNo,
                              Long supervisorEmployeeId, String professionalTitle,
                              String jobName, String positionName) {
    }

    /**
     * 测试断言使用的同步问题投影。
     */
    record SyncIssueProjection(
            String employeeNo,
            String failureStage,
            String issueCode) {
    }
}
