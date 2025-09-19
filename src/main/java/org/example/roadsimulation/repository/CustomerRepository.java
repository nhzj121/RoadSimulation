package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 根据客户编码查找客户
    Optional<Customer> findByCode(String code);

    // 根据客户名称模糊查询
    List<Customer> findByNameContainingIgnoreCase(String name);

    // 根据联系人姓名查询
    List<Customer> findByContactPersonContainingIgnoreCase(String contactPerson);

    // 查询指定编码或名称的客户
    @Query("SELECT c FROM Customer c WHERE c.code = :code OR c.name = :name")
    List<Customer> findByCodeOrName(@Param("code") String code, @Param("name") String name);

    // 检查客户编码是否存在
    boolean existsByCode(String code);

    List<Customer> code(String code);
}