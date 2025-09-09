package org.example.roadsimulation;

import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.repository.ActionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ActionRepository actionRepository;

    public DataInitializer(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // 清空现有数据（可选，根据需求决定）
        // actionRepository.deleteAll();

        // 检查是否已有数据，避免重复初始化
        if (actionRepository.count() == 0) {
            // 创建一些预设的行为
            Action transportAction = new Action("长途运输", "运输中", 480, 0.05); // 8小时，5%事故率
            Action unloadAction = new Action("标准卸货", "卸货", 60, 0.01); // 1小时，1%事故率
            Action maintenanceAction = new Action("例行保养", "保养", 120, 0.0); // 2小时，0%事故率
            Action refuelAction = new Action("加油站加油", "加油", 30, 0.02); // 0.5小时，2%事故率
            Action restAction = new Action("司机休息", "休息", 240, 0.0); // 4小时，0%事故率

            // 保存到数据库
            actionRepository.save(transportAction);
            actionRepository.save(unloadAction);
            actionRepository.save(maintenanceAction);
            actionRepository.save(refuelAction);
            actionRepository.save(restAction);

            System.out.println("Initial action data has been loaded.");
        }
    }
}