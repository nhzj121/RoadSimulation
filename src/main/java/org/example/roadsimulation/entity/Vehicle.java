package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import org.example.roadsimulation.entity.Action;
import org.example.roadsimulation.entity.POI;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "vehicle")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 20, message = "车牌号长度不能超过20个字符")
    @Column(name = "license_plate", nullable = false, unique = true)
    private String licensePlate;

    @Min(value = 0, message = "载重量不能为负数")
    @Column(name = "max_load_capacity", precision = 10)
    private Double maxLoadCapacity;

    // 对其他数值字段也可以添加类似的验证
    @Min(value = 0, message = "容积不能为负数")
    @Column(name = "cargo_volume")
    private Double cargoVolume;

    @Column(name = "brand")
    private String brand; // 品牌名称，如：东风、解放

    @Column(name = "model_type")
    private String modelType; // 具体车型

    @Column(name = "vehicle_type")
    private String vehicleType; // 平板车，高护栏，全封闭等

    @Column(name = "current_load")
    private Double currentLoad; // 载重能力（吨）

    @Min(value = 0, message = "长度不能为负数")
    @Column(name = "length")
    private Double length;

    @Min(value = 0, message = "宽度不能为负数")
    @Column(name = "width")
    private Double width;

    @Min(value = 0, message = "高度不能为负数")
    @Column(name = "height")
    private Double height;

    // @ManyToOne: 多对一关系，多辆车可以关联到一个POI或一个Action
    // fetch = FetchType.LAZY: 懒加载，只有在访问关联对象时才从数据库加载
    // @JoinColumn: 指定外键列名，即从表的外键字段名
    /*
    * 主表：被关联的一方（这里是 POI 表），它有自己的主键（比如 poi_id），是 “一” 的那一方（一个 POI 可以对应多个订单）；
    * 从表：主动关联的一方（比如 Order 订单表，即当前实体对应的表），它需要通过 “外键” 关联主表的主键，是 “多” 的那一方（多个订单可能对应同一个 POI）。
    * */
    // 多辆车可以位于同一个POI
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "current_poi_id")
    private POI currentPOI;
    // 多辆车可以对应同一个Action
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "current_action_id")
    private Action currentAction;
    // 当前所在的经度
    @Column(name = "current_longitude")
    private Double currentLongitude;
    // 当前所在的纬度
    @Column(name = "current_latitude")
    private Double currentLatitude;

    public enum VehicleStatus {
        IDLE,           // 空闲
        TRANSPORTING,   // 运输中
        UNLOADING,      // 卸货
        MAINTAINING,    // 保养
        REFUELING,      // 加油
        RESTING,        // 休息
        ACCIDENT        // 事故
    }

    // 在实体类中使用
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", length = 20)
    private VehicleStatus currentStatus;


    public Vehicle(){

    }

    // 内容有参构造
    public Vehicle(String licensePlate, String brand, String modelType, Double maxLoadCapacity) {
        this.licensePlate = licensePlate;
        this.brand = brand;
        this.modelType = modelType;
        this.maxLoadCapacity = maxLoadCapacity;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getLicensePlate() {
        return licensePlate;
    }
    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }
    public Double getMaxLoadCapacity() {return maxLoadCapacity;}
    public void setMaxLoadCapacity(Double maxLoadCapacity) {this.maxLoadCapacity = maxLoadCapacity;}
    public String getBrand() {return brand;}
    public void setBrand(String brand) {this.brand = brand;}
    public String getModelType() {
        return modelType;
    }
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
    public Double getcurrentLoad() {
        return currentLoad;
    }
    public void setcurrentLoad(Double currentLoad) {
        this.currentLoad = currentLoad;
    }
    public Double getCargoVolume() {return cargoVolume;}
    public void setCargoVolume(Double cargoVolume) {this.cargoVolume = cargoVolume;}
    public Double getLength() {return length;}
    public void setLength(Double length) {this.length = length;}
    public Double getWidth() {return width;}
    public void setWidth(Double width) {this.width = width;}
    public Double getHeight() {return height;}
    public void setHeight(Double height) {this.height = height;}
    public POI getCurrentPOI() {return currentPOI;}
    public void setCurrentPOI(POI currentPOI) { this.currentPOI = currentPOI;}
    public Action getCurrentAction() {return currentAction;}
    public void setCurrentAction(Action currentAction) {this.currentAction = currentAction;}
    public Double getCurrentLongitude() {return currentLongitude;}
    public void setCurrentLongitude(Double currentLongitude) {this.currentLongitude = currentLongitude;}
    public Double getCurrentLatitude() {return currentLatitude;}
    public void setCurrentLatitude(Double currentLatitude) {this.currentLatitude = currentLatitude;}
    public VehicleStatus getCurrentStatus() {return currentStatus;}
    public void setCurrentStatus(VehicleStatus currentStatus) {this.currentStatus = currentStatus;}

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", licensePlate='" + licensePlate + '\'' +
                ", modelType='" + modelType + '\'' +
                ", currentStatus=" + currentStatus +
                ", currentPOI=" + (currentPOI != null ? currentPOI.getId() : "null") +
                '}';
    }
}
