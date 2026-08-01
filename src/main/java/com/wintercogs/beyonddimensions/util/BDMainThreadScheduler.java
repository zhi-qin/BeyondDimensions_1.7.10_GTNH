package com.wintercogs.beyonddimensions.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;

import com.wintercogs.beyonddimensions.BeyondDimensions;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 主线程调度工具（1.7.10 适配）。
 * <p>
 * 1.7.10 的 {@code SimpleNetworkWrapper} 包 handler 在 Netty IO 线程执行（客户端
 * "Netty Client IO #N" / 服务端 "Netty IO #N"），而主线程独占对象（Container、World、
 * WorldSavedData、玩家背包、客户端 GUI 数据集合）只能在主线程访问。1.20.1 源项目
 * 所有包 handler 用 {@code context.enqueueWork} 切主线程，1.7.10 无等价 API：
 * <ul>
 * <li>客户端：{@code Minecraft.getMinecraft().func_152344_a(Runnable)}（即
 * {@code addScheduledTask}，MCP stable_12 未映射方法名故用 SRG 名，既有用法见
 * RecipeTransferOverlayHandler）。</li>
 * <li>服务端：{@code MinecraftServer} 没有 addScheduledTask（1.8+ 才有），统一经
 * {@link #scheduleServer} 提交到队列，由 FML {@code ServerTickEvent} 在服务端主线程
 * 逐 tick 排空（与 GT 系模组的既有做法一致）。</li>
 * </ul>
 * <p>
 * 性能：服务端每 tick 仅对空队列执行一次 poll；队列只由用户操作触发的网络包填充，
 * 非逐 tick 路径，开销可忽略。
 */
public final class BDMainThreadScheduler {

    private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile boolean registered = false;

    private BDMainThreadScheduler() {}

    /**
     * 在 mod init 阶段调用一次（幂等），注册服务端 tick 排空。
     */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTickListener());
    }

    /**
     * 服务端 tick 排空监听器。
     * <p>
     * 必须为 public 命名类：1.7.10 FML 会为每个 {@code @SubscribeEvent} 监听器用 ASM 生成
     * {@code ASMEventHandler} 子类并置于独立 {@code ASMClassLoader}，JDK 9+ 强封装下
     * 匿名/包私有类跨类加载器访问会抛 {@code IllegalAccessError}（服务端 Java 21 实测崩溃）。
     */
    public static class ServerTickListener {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            // 1.7.10 ServerTickEvent 每 tick 触发两次（START + END），仅在 END 排空一次
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Runnable task;
            while ((task = PENDING.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    // 单个任务异常不应中断其余排队任务或服务端 tick
                    BeyondDimensions.LOGGER.warn("BDServerScheduler 主线程任务执行异常", t);
                }
            }
        }
    }

    /**
     * 提交一个在服务端主线程执行的任务（Netty 线程 → 服务端主线程）。
     * 若 {@link #init} 尚未调用（正常在 init 已注册），首次提交会懒注册兜底。
     */
    public static void scheduleServer(Runnable task) {
        if (task == null) {
            return;
        }
        init();
        PENDING.add(task);
    }

    /**
     * 提交一个在客户端主线程（渲染线程）执行的任务（Netty 线程 → 客户端主线程）。
     * 仅在客户端路径调用。
     */
    public static void scheduleClient(Runnable task) {
        if (task == null) {
            return;
        }
        // func_152344_a = Minecraft.addScheduledTask（MCP stable_12 未映射方法名，用 SRG 名）
        Minecraft.getMinecraft()
            .func_152344_a(task);
    }
}
