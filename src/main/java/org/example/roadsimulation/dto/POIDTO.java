// POIDTO.java
package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.POI;

import java.math.BigDecimal;

public class POIDTO {
    private Long id;
    private String name;
    private POI.POIType poiType;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String address;
    private String tel;

    // 构造器
    public POIDTO() {}

    public POIDTO(Long id, String name, POI.POIType poiType, BigDecimal longitude,
                  BigDecimal latitude) {
        this.id = id;
        this.name = name;
        this.poiType = poiType;
        this.longitude = longitude;
        this.latitude = latitude;
    }

    // Getter和Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public POI.POIType getPoiType() { return poiType; }
    public void setPoiType(POI.POIType poiType) { this.poiType = poiType; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getTel() { return tel; }
    public void setTel(String tel) { this.tel = tel; }
}