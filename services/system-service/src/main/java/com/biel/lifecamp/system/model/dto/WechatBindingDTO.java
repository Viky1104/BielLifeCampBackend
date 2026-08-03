package com.biel.lifecamp.system.model.dto;

import java.time.Instant;

/**
 * 持久化微信绑定关系和员工默认角色所需的参数。
 *
 * @param identityId 外部身份标识
 * @param roleAssignmentId 默认角色分配标识
 * @param employeeId 员工标识
 * @param appId 微信小程序标识
 * @param subjectHash 经保护的 OpenID 摘要
 * @param subjectCiphertext 经认证加密的 OpenID 密文
 * @param unionHash 可用时记录的 UnionID 摘要
 * @param unionCiphertext 可用时记录的 UnionID 密文
 * @param mobileHash 经保护的手机号摘要
 * @param scopeValue 本人数据范围值
 * @param now 操作时间
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public record WechatBindingDTO(Long identityId, Long roleAssignmentId, Long employeeId,
                               String appId, String subjectHash, byte[] subjectCiphertext,
                               String unionHash, byte[] unionCiphertext, String mobileHash,
                               String scopeValue, Instant now) {
}
