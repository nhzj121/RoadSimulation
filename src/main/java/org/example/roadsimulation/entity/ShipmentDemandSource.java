package org.example.roadsimulation.entity;

/** 运单需求来源；用于隔离旧随机需求、加工链需求、人工需求和实验数据。 */
public enum ShipmentDemandSource {
    LEGACY,
    PRODUCTION,
    MANUAL,
    EXPERIMENT
}
