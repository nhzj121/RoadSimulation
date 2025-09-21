package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Goods;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsRepository extends JpaRepository<Goods, Long> {
    // 根据名称模糊搜索（用于前端搜索功能）
    List<Goods> findByNameContainingIgnoreCase(String name);
    // 根据SKU查询货物
    Optional<Goods> findBySku(@Param("sku") String sku);
    // 根据类别查询
    List<Goods> findByCategory(String category);
    // 查询需要温控的货物
    List<Goods> findByRequireTempTrue();
    // 查询特定危险等级的货物
    List<Goods> findByHazmatLevel(String hazmatLevel);

    // 检查SKU是否存在
    boolean existsBySku(String sku);

    // 综合查询：多条件筛选
    @Query("SELECT g FROM Goods g WHERE " +
            "(:name IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:category IS NULL OR g.category = :category) AND " +
            "(:requireTemp IS NULL OR g.requireTemp = :requireTemp) AND " +
            "(:hazmatLevel IS NULL OR g.hazmatLevel = :hazmatLevel) AND " +
            "(:minWeight IS NULL OR g.weightPerUnit >= :minWeight) AND " +
            "(:maxWeight IS NULL OR g.weightPerUnit <= :maxWeight)")
    Page<Goods> searchGoods(
            @Param("name") String name,
            @Param("category") String category,
            @Param("requireTemp") Boolean requireTemp,
            @Param("hazmatLevel") String hazmatLevel,
            @Param("minWeight") Double minWeight,
            @Param("maxWeight") Double maxWeight,
            Pageable pageable);
}
