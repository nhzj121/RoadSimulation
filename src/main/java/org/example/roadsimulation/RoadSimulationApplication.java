package org.example.roadsimulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RoadSimulationApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoadSimulationApplication.class, args);
        /*状态同步机制缺失
        问题：各实体状态更新缺乏事务性保证：当前状态更新分散在不同地方,如果某一步失败，状态将不一致
        需要尝试在Assignment中统一管理状态 ToDo
        */
	}

}
