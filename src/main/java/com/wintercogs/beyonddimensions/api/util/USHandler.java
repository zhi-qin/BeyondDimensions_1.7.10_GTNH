package com.wintercogs.beyonddimensions.api.util;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/**
 * 统一存储处理器（1.7.10 适配版）。
 * 封装 UnifiedStorage 的常用操作，提供与玩家维度网络交互的便捷方法。
 */
public class USHandler extends CommonHandler {

    @Nullable
    protected DimensionsNet net;

    public USHandler(IStackHandler handler) {
        super(handler);
        // 接线 UnifiedStorage 内部的网络引用：此前 net 从未被赋值，权限判断恒拒绝（审计 M1-3）
        if (handler instanceof UnifiedStorage) {
            this.net = ((UnifiedStorage) handler).getNet();
        }
    }

    public USHandler(UnifiedStorage storage) {
        super(storage);
        this.net = storage.getNet();
    }

    /**
     * 从玩家获取其主维度网络的统一存储
     */
    @Nullable
    public static USHandler fromPlayer(@Nullable EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) return null;
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net == null) return null;
        return new USHandler(net.getUnifiedStorage());
    }

    /**
     * 从网络 ID 获取统一存储
     */
    @Nullable
    public static USHandler fromNetId(int netId) {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return null;
        return new USHandler(net.getUnifiedStorage());
    }

    /**
     * 获取关联的维度网络
     */
    @Nullable
    public DimensionsNet getNet() {
        return net;
    }

    /**
     * 设置关联的维度网络
     */
    public void setNet(@Nullable DimensionsNet net) {
        this.net = net;
    }

    /**
     * 获取当前存储的统一存储实例
     */
    @Nullable
    public UnifiedStorage getUnifiedStorage() {
        if (handler instanceof UnifiedStorage) {
            return (UnifiedStorage) handler;
        }
        return null;
    }

    /**
     * 检查玩家是否拥有访问权限
     */
    public boolean hasAccess(@Nullable EntityPlayer player) {
        if (player == null || net == null) return false;
        UUID uuid = player.getUniqueID();
        return net.isPlayer(uuid) || net.isManager(uuid);
    }

    /**
     * 检查玩家是否拥有管理权限
     */
    public boolean isManager(@Nullable EntityPlayer player) {
        if (player == null || net == null) return false;
        return net.isManager(player.getUniqueID());
    }

    /**
     * 带权限检查的插入操作
     */
    @Nonnull
    public KeyAmount insertWithAccess(@Nullable EntityPlayer player, IStackKey<?> key, long amount, boolean simulate) {
        if (!hasAccess(player)) {
            if (player != null) {
                player.addChatMessage(new ChatComponentText("你没有此网络的访问权限"));
            }
            return new KeyAmount(key, amount);
        }
        return handler.insert(key, amount, simulate);
    }

    /**
     * 带权限检查的提取操作
     */
    @Nonnull
    public KeyAmount extractWithAccess(@Nullable EntityPlayer player, IStackKey<?> key, long amount, boolean simulate,
        boolean fuzzy) {
        if (!hasAccess(player)) {
            if (player != null) {
                player.addChatMessage(new ChatComponentText("你没有此网络的访问权限"));
            }
            return new KeyAmount(key, 0);
        }
        return handler.extract(key, amount, simulate, fuzzy);
    }
}
