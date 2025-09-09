package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.repository.ActionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ActionService {

    private final ActionRepository actionRepository;

    @Autowired // 通过构造方法把ActionRepository的实例“注入”进来
    public ActionService(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    // 获取所有行为定义
    public List<Action> getAllActions() {
        return actionRepository.findAll();
    }

    // 根据ID获取一个行为
    public Action getActionById(Long id) {
        Optional<Action> action = actionRepository.findById(id);
        // .orElseThrow(...) 是操作Optional的方法：如果数据存在就返回，如果不存在就抛出异常
        return action.orElseThrow(() -> new RuntimeException("Action not found with id: " + id));
    }

    // 创建或更新一个行为
    public Action saveAction(Action action) {
        // 这里可以添加业务逻辑验证，例如事故率必须在0-1之间
        if (action.getAccidentRate() != null &&
                (action.getAccidentRate() < 0 || action.getAccidentRate() > 1)) {
            // 如果验证不通过，抛出异常，阻止无效数据被保存
            throw new IllegalArgumentException("Accident rate must be between 0.0 and 1.0");
        }
        // 验证通过，交给Repository去保存数据到数据库
        return actionRepository.save(action);
    }

    // 根据行为类型获取行为列表
    public List<Action> getActionsByType(String actionType) {
        // 这里调用的是我们在ActionRepository里自定义的方法
        // Service层并不关心这个方法在Repository里是怎么实现的（是方法名解析还是@Query），它只关心功能
        return actionRepository.findByActionType(actionType);
    }

    // 删除一个行为
    public void deleteAction(Long id) {
        // 直接委托Repository执行删除操作
        actionRepository.deleteById(id);
    }
}