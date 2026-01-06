package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户实体类
 * 说明：客户是发起运输需求的主体，与Shipment存在一对多关系
 */
@Entity
@Table(
        name = "customer",
        indexes = {
                @Index(name = "idx_customer_code", columnList = "code"),
                @Index(name = "idx_customer_name", columnList = "name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customer_code", columnNames = "code")
        }
)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "客户编码不能为空")
    @Size(max = 50, message = "客户编码长度不能超过50个字符")
    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 100, message = "客户名称长度不能超过100个字符")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 100, message = "联系人姓名长度不能超过100个字符")
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Size(max = 200, message = "地址长度不能超过200个字符")
    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // 与运单的一对多关系
    @OneToMany(mappedBy = "customer", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH}, fetch = FetchType.LAZY)
    private Set<Shipment> shipments = new HashSet<>();

    // 进行修改的对象和时间
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    public Customer() {}

    public Customer(String code, String name) {
        this.code = code;
        this.name = name;
    }

    // 便捷方法：维护双向关系
    public void addShipment(Shipment shipment) {
        if (shipment != null) {
            shipments.add(shipment);
            shipment.setCustomer(this);
        }
    }

    public void removeShipment(Shipment shipment) {
        if (shipment != null) {
            shipments.remove(shipment);
            shipment.setCustomer(null);
        }
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Set<Shipment> getShipments() { return shipments; }
    public void setShipments(Set<Shipment> shipments) { this.shipments = shipments; }

    // 四元组字段的getter和setter
    public String getUpdatedBy() {return updatedBy;}
    public void setUpdatedBy(String updatedBy) {this.updatedBy = updatedBy;}


    @PreUpdate
    public void touchUpdateTime() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}