package com.biel.lifecamp.system.common.id;

import java.time.Instant;

/**
 * 根据时间、节点和序列生成趋势递增的长整型标识。
 *
 * @author Biel Life Camp Team
 * @since 2026-07-22
 */
public final class LongIdGenerator {
    private static final long EPOCH = 1735689600000L;
    private final long nodeId;
    private long lastMillis = -1;
    private long sequence;

    public LongIdGenerator(int nodeId) {
        if (nodeId < 0 || nodeId > 1023) {
            throw new IllegalArgumentException("nodeId must be between 0 and 1023");
        }
        this.nodeId = nodeId;
    }

    /**
     * 生成下一个长整型标识。
     *
     * @return 当前节点内唯一且趋势递增的标识
     * @throws IllegalStateException 系统时钟回拨时抛出
     */
    public synchronized long next() {
        long now = Instant.now().toEpochMilli();
        if (now < lastMillis) {
            throw new IllegalStateException("Clock moved backwards");
        }
        if (now == lastMillis) {
            sequence = (sequence + 1) & 4095;
            if (sequence == 0) {
                // 同一毫秒序列耗尽时等待下一毫秒，避免重复标识。
                do {
                    now = Instant.now().toEpochMilli();
                } while (now <= lastMillis);
            }
        } else {
            sequence = 0;
        }
        lastMillis = now;
        return ((now - EPOCH) << 22) | (nodeId << 12) | sequence;
    }
}
