package com.wintercogs.beyonddimensions.util;

import net.minecraft.entity.player.EntityPlayer;

/**
 * 经验值计算工具（1.7.10 适配版）。
 * <p>
 * 1.7.10 中 {@code EntityPlayer.experience} 字段对应 1.20.1 的 {@code experienceProgress}，
 * 表示当前等级内的经验进度（0.0 ~ 1.0）。
 */
public final class XpUtil {

    private XpUtil() {}

    /**
     * 计算从 level 升到 level+1 所需的经验值。
     * 对齐 1.20.1 源项目的 xpCostToNextLevel(L) 语义。
     *
     * @param level 当前等级
     * @return 升到下一级所需经验值
     */
    public static int getExperienceForLevel(int level) {
        if (level < 0) return 0;
        if (level <= 15) {
            return level * 2 + 7;
        }
        if (level <= 30) {
            return level * 5 - 38;
        }
        return level * 9 - 158;
    }

    /**
     * 计算从 0 级到指定等级所需的总经验值（累计）。
     *
     * @param level 目标等级
     * @return 累计经验值
     */
    public static long getTotalExperienceForLevel(int level) {
        if (level <= 0) return 0L;
        // 闭式公式（对齐源项目 totalXpAtIntegerLevel），O(1)，避免 int 累加在高等级溢出为负
        if (level <= 16) {
            return (long) level * level + 6L * level;
        } else if (level <= 31) {
            return (5L * level * level - 81L * level + 720L) / 2L;
        } else {
            return (9L * level * level - 325L * level + 4440L) / 2L;
        }
    }

    /**
     * 根据总经验值计算当前等级。
     *
     * @param totalXp 总经验值
     * @return 等级
     */
    public static int getLevelForExperience(long totalXp) {
        int level = 0;
        long remaining = totalXp;
        while (true) {
            int xpForNextLevel = getExperienceForLevel(level);
            if (xpForNextLevel <= 0) break;
            if (remaining < xpForNextLevel) break;
            remaining -= xpForNextLevel;
            level++;
        }
        return level;
    }

    /**
     * 获取玩家的总经验值（等级 + 当前进度）。
     */
    public static long getPlayerTotalXp(EntityPlayer player) {
        long levelTotal = getTotalExperienceForLevel(player.experienceLevel);
        int xpForNext = getExperienceForLevel(player.experienceLevel);
        int progressXp = (int) (player.experience * xpForNext);
        return (long) levelTotal + progressXp;
    }

    /**
     * 设置玩家的总经验值。
     */
    public static void setPlayerTotalXp(EntityPlayer player, long totalXp) {
        int level = getLevelForExperience(totalXp);
        long usedForLevel = getTotalExperienceForLevel(level);
        long remainingXp = totalXp - usedForLevel;
        int xpForNext = getExperienceForLevel(level);

        player.experienceLevel = level;
        player.experience = (float) remainingXp / (float) xpForNext;
        player.experienceTotal = (int) totalXp;
    }

    /**
     * 给玩家增加经验值。
     *
     * @return 实际增加后的总经验值
     */
    public static long addPlayerXp(EntityPlayer player, long amount) {
        long current = getPlayerTotalXp(player);
        long newTotal = Math.max(0, current + amount);
        setPlayerTotalXp(player, newTotal);
        return newTotal;
    }

    /**
     * 将玩家当前等级与进度转换为浮点等级。
     */
    public static double levelAsDouble(EntityPlayer player) {
        int level = player.experienceLevel;
        int xpForNext = getExperienceForLevel(level);
        return level + (xpForNext > 0 ? player.experience : 0.0);
    }

    /**
     * 计算当前浮点等级超过目标等级的多余经验值。
     */
    public static long xpExcessAbove(double currentLevel, int targetLevel) {
        if (currentLevel <= targetLevel) return 0;
        long targetTotal = getTotalExperienceForLevel(targetLevel);
        long currentTotal = xpFromLevel(currentLevel);
        return Math.max(0, currentTotal - targetTotal);
    }

    /**
     * 计算从当前浮点等级至少到达目标等级还需要的经验值。
     */
    public static long xpToReachAtLeast(double currentLevel, int targetLevel) {
        if (currentLevel >= targetLevel) return 0;
        long targetTotal = getTotalExperienceForLevel(targetLevel);
        long currentTotal = xpFromLevel(currentLevel);
        return Math.max(0, targetTotal - currentTotal);
    }

    private static long xpFromLevel(double level) {
        int intLevel = (int) level;
        long total = getTotalExperienceForLevel(intLevel);
        int xpForNext = getExperienceForLevel(intLevel);
        total += (long) ((level - intLevel) * xpForNext);
        return total;
    }

    /**
     * 从玩家扣除经验值。
     *
     * @return 实际扣除后的总经验值，如果不满足则返回 -1
     */
    public static long removePlayerXp(EntityPlayer player, long amount) {
        long current = getPlayerTotalXp(player);
        if (current < amount) {
            return -1;
        }
        long newTotal = current - amount;
        setPlayerTotalXp(player, newTotal);
        return newTotal;
    }
}
