package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Enrollment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    // 查询方法
    List<Enrollment> findByPoi(POI poi);
    List<Enrollment> findByGoods(Goods goods);

    // 按货物SKU查询
    @Query("SELECT e FROM Enrollment e WHERE e.goods.sku = :sku")
    List<Enrollment> findByGoodsSku(@Param("sku") String sku);
}
