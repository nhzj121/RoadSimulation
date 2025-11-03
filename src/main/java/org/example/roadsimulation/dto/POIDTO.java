// POIDTO.java
package org.example.roadsimulation.dto;

import java.math.BigDecimal;

public class POIDTO {
    private String id;
    private String name;
    private String type;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String address;
    private String tel;

    // 构造器
    public POIDTO() {}

    public POIDTO(String id, String name, String type, BigDecimal longitude,
                  BigDecimal latitude, String address, String tel) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.longitude = longitude;
        this.latitude = latitude;
        this.address = address;
        this.tel = tel;
    }

    // Getter和Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getTel() { return tel; }
    public void setTel(String tel) { this.tel = tel; }
}