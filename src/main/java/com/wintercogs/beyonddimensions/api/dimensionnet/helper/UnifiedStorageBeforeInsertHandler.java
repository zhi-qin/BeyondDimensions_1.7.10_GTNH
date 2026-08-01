package com.wintercogs.beyonddimensions.api.dimensionnet.helper;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;

/**
 * 一些辅助工具，让 UnifiedStorage 可以在插入操作真实执行前进行一些调整
 */
public final class UnifiedStorageBeforeInsertHandler {

    private static final List<BeforeInsertHandler> handlers = new ArrayList<>();

    @FunctionalInterface
    public interface BeforeInsertHandler {

        /**
         * @param originalInsert 本次插入最原始的堆叠
         * @param tryInsert      当前调用链上传递的堆叠
         * @param net            网络信息，可为空
         */
        @Nonnull
        BeforeInsertHandlerReturnInfo beforeInsert(@Nonnull KeyAmount originalInsert, @Nonnull KeyAmount tryInsert,
            @Nullable DimensionsNet net);
    }

    public static final class BeforeInsertHandlerReturnInfo {

        private final KeyAmount beforeInsert;
        private final boolean cancel;

        public BeforeInsertHandlerReturnInfo(@Nonnull KeyAmount beforeInsert, boolean cancel) {
            this.beforeInsert = beforeInsert;
            this.cancel = cancel;
        }

        public KeyAmount beforeInsert() {
            return beforeInsert;
        }

        public boolean isEmpty() {
            return beforeInsert.isEmpty();
        }

        public boolean cancel() {
            return cancel;
        }

        public boolean isCanceled() {
            return cancel;
        }
    }

    /**
     * 调用此函数以添加处理
     */
    public static void addHandler(BeforeInsertHandler handler) {
        handlers.add(handler);
    }

    /**
     * @param tryInsert 本次尝试插入的原始堆叠
     * @param net       携带的网络信息，可为空
     * @return 维度网络最终实际处理的堆叠
     *         <p>
     *         会对 handlers 表进行链式调用，每一次处理完的 insert 会被传递给下一次调用，
     *         最终返回时，如果 cancel，则网络不接受此次任何输入，将原始堆叠返回给玩家或机器，
     *         如果不为 cancel，则尝试将最后一次调用得到的输入给维度网络
     */
    @Nonnull
    public static BeforeInsertHandlerReturnInfo onBeforeInsert(@Nullable KeyAmount tryInsert,
        @Nullable DimensionsNet net) {
        final KeyAmount original = (tryInsert == null) ? new KeyAmount(EmptyStackKey.INSTANCE, 0) : tryInsert;

        if (tryInsert == null) {
            return new BeforeInsertHandlerReturnInfo(original, true);
        }

        if (tryInsert.isEmpty()) {
            return new BeforeInsertHandlerReturnInfo(tryInsert, true);
        }

        KeyAmount current = tryInsert;

        for (BeforeInsertHandler handler : handlers) {
            if (handler == null) {
                continue;
            }

            BeforeInsertHandlerReturnInfo ret = handler.beforeInsert(original, current, net);

            current = ret.beforeInsert();

            if (ret.cancel()) {
                return new BeforeInsertHandlerReturnInfo(current, true);
            }

            if (current.isEmpty()) {
                return new BeforeInsertHandlerReturnInfo(current, false);
            }
        }

        return new BeforeInsertHandlerReturnInfo(current, false);
    }
}
