package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long>{
    // 自定义查询：根据行为类型查找（例如，找到所有"运输中"类型的行为）
    List<Action> findByActionType(String actionType);

    // 自定义查询：查找事故率低于某个值的安全行为
    List<Action> findByAccidentRateLessThan(Double maxAccidentRate);

    // 自定义查询：根据行为名称精确查找
    Action findByActionName(String actionName);
}
