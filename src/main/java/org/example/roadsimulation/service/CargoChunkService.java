package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.CargoChunk;
import org.example.roadsimulation.entity.Goods;

import java.util.List;

/**
 * 货物块拆分服务 —— 在分配前将大订单按车辆容量预拆分为标准化小块。
 *
 * <p>核心策略：
 * <ol>
 *   <li>查询系统中所有车辆类型，按载重去重后从大到小排序</li>
 *   <li>计算每类车的"理想装载量" = floor(载重/单位重)，自然保证80-100%装载率</li>
 *   <li>贪心拆分：大车类型优先，尽可能多装，直到剩余量小于该车型的理想装载量</li>
 *   <li>余数用"最小能装下的车"兜底，保证最高装载率</li>
 *   <li>如果没有任何车型能装下1单位（单件超重），标记为异常</li>
 * </ol>
 *
 * <p>与 CargoSplitService 的区别：
 * <ul>
 *   <li>CargoSplitService: 输入具体车辆列表 → 绑定到具体车辆 → 可能装载率低</li>
 *   <li>CargoChunkService: 只根据车型容量 → 生成标准化块 → 每个块天然高装载率</li>
 * </ul>
 */
public interface CargoChunkService {

    /**
     * 将总数量按车辆容量预拆分为多个标准化货物块。
     *
     * @param goods     货物主数据
     * @param totalQty  需要运输的总数量
     * @return 按车辆容量拆分后的块列表，每个块装一辆车
     */
    List<CargoChunk> chunkCargo(Goods goods, int totalQty);
}
