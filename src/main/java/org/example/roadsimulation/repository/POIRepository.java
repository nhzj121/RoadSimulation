package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.POI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository层：POI 实体的数据库操作接口
 * 继承 JpaRepository<POI, Long> 就可以获得 CRUD 基本方法：
 *  - save() 新增或更新
 *  - findById() 按ID查找
 *  - findAll() 查询全部
 *  - deleteById() 按ID删除
 *  - existsById() 判断是否存在
 */
@Repository
public interface POIRepository extends JpaRepository<POI, Long> {
}
