package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    // 根据动作名称模糊查询
    List<Action> findByActionNameContainingIgnoreCase(String actionName);

    // 根据动作类型查询
    List<Action> findByActionType(Action.ActionType actionType);

    // 根据是否锁定状态查询
    List<Action> findByIsLocked(Boolean isLocked);

    // 组合查询：根据类型和名称查询
    List<Action> findByActionTypeAndActionNameContainingIgnoreCase(
            Action.ActionType actionType, String actionName);

    // 查询所有移动类型的动作
    @Query("SELECT a FROM Action a WHERE a.actionType = org.example.roadsimulation.entity.Action.ActionType.MOVE_TO")
    List<Action> findAllMoveActions();

    // 查询所有装卸货动作
    @Query("SELECT a FROM Action a WHERE a.actionType IN (org.example.roadsimulation.entity.Action.ActionType.LOAD, org.example.roadsimulation.entity.Action.ActionType.UNLOAD)")
    List<Action> findAllCargoActions();

    // 根据持续时间范围查询
    @Query("SELECT a FROM Action a WHERE a.durationMinutes BETWEEN :minDuration AND :maxDuration")
    List<Action> findByDurationBetween(@Param("minDuration") Integer minDuration,
                                       @Param("maxDuration") Integer maxDuration);
}