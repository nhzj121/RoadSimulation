package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.repository.ActionRepository;
import org.example.roadsimulation.service.ActionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ActionServiceImpl implements ActionService {

    private final ActionRepository actionRepository;

    public ActionServiceImpl(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Override
    public Action createAction(Action action) {
        // 数据验证
        if (!validateAction(action)) {
            throw new IllegalArgumentException("动作数据验证失败");
        }

        // 设置默认值
        if (action.getIsLocked() == null) {
            action.setIsLocked(false);
        }

        return actionRepository.save(action);
    }

    @Override
    @Transactional(readOnly = true)
    public Action getActionById(Long id) {
        Optional<Action> action = actionRepository.findById(id);
        return action.orElseThrow(() ->
                new RuntimeException("未找到ID为 " + id + " 的动作"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Action> getAllActions() {
        return actionRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Action> getAllActions(Pageable pageable) {
        return actionRepository.findAll(pageable);
    }

    @Override
    public Action updateAction(Long id, Action actionDetails) {
        Action existingAction = getActionById(id);

        // 检查是否被锁定
        if (existingAction.isLocked()) {
            throw new IllegalStateException("动作已被锁定，无法修改");
        }

        // 更新字段（使用setter方法以触发锁定检查）
        if (actionDetails.getActionName() != null) {
            existingAction.setName(actionDetails.getActionName());
        }

        if (actionDetails.getActionType() != null) {
            existingAction.setType(actionDetails.getActionType());
        }

        if (actionDetails.getDurationMinutes() != null) {
            existingAction.setDurationMinutes(actionDetails.getDurationMinutes());
        }

        if (actionDetails.getTargetPoiId() != null) {
            existingAction.setTargetPoiId(actionDetails.getTargetPoiId());
        }

        if (actionDetails.getDescription() != null) {
            existingAction.setDescription(actionDetails.getDescription());
        }

        return actionRepository.save(existingAction);
    }

    @Override
    public void deleteAction(Long id) {
        Action action = getActionById(id);

        // 检查是否被锁定
        if (action.isLocked()) {
            throw new IllegalStateException("动作已被锁定，无法删除");
        }

        actionRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Action> searchActionsByName(String name) {
        return actionRepository.findByActionNameContainingIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Action> getActionsByType(Action.ActionType actionType) {
        return actionRepository.findByActionType(actionType);
    }

    @Override
    public Action lockAction(Long id) {
        Action action = getActionById(id);
        action.lock();
        return actionRepository.save(action);
    }

    @Override
    public Action unlockAction(Long id) {
        Action action = getActionById(id);
        action.unlock();
        return actionRepository.save(action);
    }

    @Override
    public List<Action> batchCreateActions(List<Action> actions) {
        // 验证所有动作
        for (Action action : actions) {
            if (!validateAction(action)) {
                throw new IllegalArgumentException("批量创建中存在无效的动作数据");
            }
        }

        return actionRepository.saveAll(actions);
    }

    @Override
    public boolean validateAction(Action action) {
        if (action.getActionName() == null || action.getActionName().trim().isEmpty()) {
            return false;
        }

        if (action.getActionType() == null) {
            return false;
        }

            if (action.getDurationMinutes() != null && action.getDurationMinutes() <= 0) {
                return false;
            }

        return true;
    }
}