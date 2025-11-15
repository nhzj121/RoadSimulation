package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.POIDTO;
import org.example.roadsimulation.entity.POI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * POIService 接口
 *
 * 功能：
 * 1. 提供 POI（Point of Interest，关键点）相关的业务操作方法
 * 2. 支持基本的 CRUD 操作（增、删、改、查）
 * 3. 支持分页查询、按名称模糊查询、按类型查询
 * 4. 提供判断 POI 名称是否存在的方法，用于防止重复
 */
public interface POIService {

    /**
     * 创建一个新的 POI
     *
     * @param poi 待创建的 POI 实体对象
     * @return 返回保存后的 POI 对象（包含自动生成的 ID）
     * @throws IllegalArgumentException 如果 POI 名称已存在，则抛出异常
     */
    POI create(POI poi);

    /**
     * 更新指定 ID 的 POI 信息
     *
     * @param id 待更新的 POI ID
     * @param poi 包含更新内容的 POI 实体对象
     * @return 返回更新后的 POI 对象
     * @throws RuntimeException 如果 POI 不存在，则抛出异常
     * @throws IllegalArgumentException 如果更新后的 POI 名称与其他 POI 冲突，则抛出异常
     */
    POI update(Long id, POI poi);

    /**
     * 删除指定 ID 的 POI
     *
     * @param id 待删除的 POI ID
     * @throws RuntimeException 如果 POI 不存在，则抛出异常
     * @throws IllegalStateException 如果 POI 仍有关联的车辆，则不允许删除
     */
    void delete(Long id);

    /**
     * 根据 ID 查询 POI
     *
     * @param id POI 的唯一 ID
     * @return 返回 Optional<POI>，如果存在则包含 POI 对象，否则为空
     */
    Optional<POI> getById(Long id);

    /**
     * 查询系统中所有 POI
     *
     * @return 返回包含所有 POI 的列表
     */
    List<POI> getAll();

    /**
     * 查询当前可以展示的POI数据
     */
    List<POI> getPOIAbleToShow();

    /**
     * 分页查询所有 POI
     *
     * @param pageable 分页参数（页码、每页条数、排序规则）
     * @return 返回 Page<POI>，包含分页信息和当前页 POI 数据
     */
    Page<POI> getAll(Pageable pageable);

    /**
     * 根据名称模糊查询 POI
     *
     * @param name POI 名称关键字
     * @return 返回匹配的 POI 列表，忽略大小写
     */
    List<POI> searchByName(String name);

    /**
     * 根据 POI 类型查询
     *
     * @param poiType POI 类型枚举（例如 WAREHOUSE, DISTRIBUTION_CENTER）
     * @return 返回指定类型的 POI 列表
     */
    List<POI> findByType(POI.POIType poiType);

    /**
     * 判断 POI 名称是否已存在
     *
     * @param name 待检查的 POI 名称
     * @return true 表示名称已存在，false 表示不存在
     */
    boolean existsByName(String name);

    // ================ 新增的方法 ================

    /**
     * 根据 ID 判断 POI 是否存在
     * @param id POI ID
     * @return true 表示存在，false 表示不存在
     */
    boolean existsById(Long id);

    /**
     * 根据 ID 获取 POI 实体（不包装为 Optional）
     * @param id POI ID
     * @return POI 实体
     * @throws RuntimeException 如果 POI 不存在
     */
    POI getPOIEntityById(Long id);

    /**
     * 批量保存POI数据
     */
    List<POI> batchSavePOIs(List<POIDTO> poiDTOs);

    /**
     * 转换前端POI类型字符串为枚举类型
     */
    POI.POIType convertPOIType(String frontendType);

    void resetAutoIncrement();
    boolean isTableEmpty();
}