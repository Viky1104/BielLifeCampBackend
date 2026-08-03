package com.biel.lifecamp.system.dao;

import com.biel.lifecamp.system.model.dto.EhrEmployeePersistItemDTO;
import com.biel.lifecamp.system.model.dto.EhrEmployeeUpsertDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncPromotionResultDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncRunCreateDTO;
import com.biel.lifecamp.system.model.dto.EhrSyncRunDTO;
import com.biel.lifecamp.system.model.dto.EmployeeIdentityDTO;
import com.biel.lifecamp.system.model.dto.EmployeeReferenceDTO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * EHR 人员同步运行和员工投影数据访问。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-29
 */
@Mapper
public interface EhrSyncMapper {
    /**
     * 创建一次同步运行记录。
     *
     * @param run 待创建的同步运行
     * @return 受影响行数
     */
    int insertRun(EhrSyncRunCreateDTO run);

    /**
     * 按主键查询同步运行。
     *
     * @param runId 同步运行标识
     * @return 同步运行，不存在时返回 {@code null}
     */
    EhrSyncRunDTO selectRun(long runId);

    /**
     * 按业务幂等键查询同步运行。
     *
     * @param idempotencyKey 触发方提供的幂等键
     * @return 已有同步运行，不存在时返回 {@code null}
     */
    EhrSyncRunDTO selectRunByIdempotencyKey(String idempotencyKey);

    /**
     * 查询最近的同步运行。
     *
     * @param limit 最大返回条数
     * @return 按创建时间倒序排列的运行列表
     */
    List<EhrSyncRunDTO> selectRecentRuns(int limit);

    /**
     * 将同步运行标记为执行中。
     *
     * @param runId 同步运行标识
     * @param startedAt 开始时间
     * @return 受影响行数
     */
    int updateRunRunning(@Param("runId") long runId, @Param("startedAt") Instant startedAt);

    /**
     * 将同步运行标记为成功并保存统计结果。
     *
     * @param runId 同步运行标识
     * @param result 人员生效统计
     * @param completedAt 完成时间
     * @return 受影响行数
     */
    int updateRunSucceeded(@Param("runId") long runId,
                           @Param("result") EhrSyncPromotionResultDTO result,
                           @Param("completedAt") Instant completedAt);

    /**
     * 将同步运行标记为失败。
     *
     * @param runId 同步运行标识
     * @param failureCode 稳定失败码
     * @param failureDigest 不包含人员敏感信息的失败摘要
     * @param completedAt 完成时间
     * @return 受影响行数
     */
    int updateRunFailed(@Param("runId") long runId, @Param("failureCode") String failureCode,
                        @Param("failureDigest") String failureDigest,
                        @Param("completedAt") Instant completedAt);

    /**
     * 查询已经存在的 EHR 人员标识，用于计算新增和更新数量。
     *
     * @param ehrPersonIds 本批次 EHR 人员标识
     * @return 已存在的 EHR 人员标识
     */
    List<String> selectExistingEhrPersonIds(@Param("ehrPersonIds") List<String> ehrPersonIds);

    /**
     * 查询工号当前归属，用于阻止其他 EHR 人员误更新已有员工。
     *
     * @param employeeNumbers 本批次工号
     * @return 已存在的人员标识与工号归属
     */
    List<EmployeeIdentityDTO> selectExistingEmployeeIdentities(
            @Param("employeeNumbers") List<String> employeeNumbers);

    /**
     * 写入本次同步的人员暂存事实。
     *
     * @param runId 同步运行标识
     * @param employee 已校验的人员数据
     * @param payloadDigest 人员来源关键字段摘要
     * @return 受影响行数
     */
    int insertStage(@Param("runId") long runId,
                    @Param("employee") EhrEmployeeUpsertDTO employee,
                    @Param("payloadDigest") String payloadDigest);

    /**
     * 批量写入本次同步的人员暂存事实。
     *
     * @param runId 同步运行标识
     * @param items 固定大小的人员持久化参数
     * @return 受影响行数
     */
    int insertStageBatch(@Param("runId") long runId,
                         @Param("items") List<EhrEmployeePersistItemDTO> items);

    /**
     * 按 EHR 人员标识新增或更新员工投影。
     *
     * @param employee 已校验的人员数据
     * @param mobileHash 规范化手机号的不可逆摘要
     * @param syncedAt 本次同步时间
     * @return 受影响行数
     */
    int upsertEmployee(@Param("employee") EhrEmployeeUpsertDTO employee,
                       @Param("mobileHash") String mobileHash,
                       @Param("syncedAt") Instant syncedAt);

    /**
     * 按 EHR 人员标识批量新增或更新员工投影。
     *
     * @param items 固定大小的人员持久化参数
     * @param syncedAt 本次同步时间
     * @return 受影响行数
     */
    int upsertEmployeeBatch(
            @Param("items") List<EhrEmployeePersistItemDTO> items,
            @Param("syncedAt") Instant syncedAt);

    /**
     * 查询本批次人员的本地主键及直属上级工号。
     *
     * @param runId 同步运行标识
     * @return 用于解析上下级关系的人员引用
     */
    List<EmployeeReferenceDTO> selectEmployeeReferences(long runId);

    /**
     * 更新员工直属上级关系。
     *
     * @param employeeId 员工本地主键
     * @param supervisorEmployeeId 直属上级本地主键，未匹配时为空
     * @param syncedAt 本次同步时间
     * @return 受影响行数
     */
    int updateSupervisor(@Param("employeeId") long employeeId,
                         @Param("supervisorEmployeeId") Long supervisorEmployeeId,
                         @Param("syncedAt") Instant syncedAt);

    /**
     * 为员工幂等初始化普通角色。
     *
     * @param assignmentId 角色分配主键
     * @param employeeId 员工本地主键
     * @param scopeValue 员工本人数据范围值
     * @param createdAt 创建时间
     * @return 新增行数，已有普通角色时返回 0
     */
    int insertEmployeeRoleAssignment(@Param("assignmentId") long assignmentId,
                                     @Param("employeeId") long employeeId,
                                     @Param("scopeValue") String scopeValue,
                                     @Param("createdAt") Instant createdAt);

    /**
     * 将未出现在本次完整快照中的在职员工标记为离职。
     *
     * @param runId 同步运行标识
     * @param syncedAt 本次同步时间
     * @return 被标记离职的员工数量
     */
    int markMissingEmployeesResigned(@Param("runId") long runId,
                                     @Param("syncedAt") Instant syncedAt);

    /**
     * 标记 EHR 集成已至少成功完成一次全量同步。
     *
     * @param completedAt 完成时间
     * @return 受影响行数
     */
    int completeIntegrationState(Instant completedAt);
}
