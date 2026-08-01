package cofh.api.energy;

/**
 * CoFH RF API IEnergyHandler 接口存根（1.7.10 编译时占位）。
 * <p>
 * 此接口为 CoFH RF API 的编译时存根，仅用于让本模组的 RF 能量通道变体
 * （{@code integration/rf/RfNetEnergyPathwayBlockEntity}）在缺少 CoFH 依赖时也能编译通过。
 * 运行时真实的 {@code cofh.api.energy.IEnergyHandler} 类会由 CoFHCore 或内嵌 CoFH API 的
 * 模组覆盖此存根。
 * <p>
 * 原始版权归 CoFH 团队所有，此处仅保留公共方法签名。
 */
public interface IEnergyHandler extends IEnergyProvider, IEnergyReceiver {

    // 便捷聚合接口：方法签名由 IEnergyProvider / IEnergyReceiver 提供
}
