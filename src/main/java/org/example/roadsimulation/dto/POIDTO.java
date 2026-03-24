package org.example.roadsimulation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.roadsimulation.entity.POI;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class POIDTO {
    private Long id;
    private String name;

    @JsonProperty("poi_type")
    private POI.POIType poiType;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private String address;
    private String tel;

    public POIDTO() {}

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

