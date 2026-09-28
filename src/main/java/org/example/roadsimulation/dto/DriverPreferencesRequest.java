package org.example.roadsimulation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 司机接单偏好更新请求：字段全可空，非空字段才覆盖。
 */
@Getter
@Setter
public class DriverPreferencesRequest {

    /** 货类偏好（白名单见 DriverPreferenceScorer.ALLOWED_CARGO_CATEGORIES），如 "水泥" */
    private String preferredCargoType;

    /** 能接受的运输距离上限（公里），须 > 0 */
    private Double preferredMaxDistanceKm;

    /** 能接受的货物重量上限（吨），须 > 0 */
    private Double preferredMaxWeightTons;
}
