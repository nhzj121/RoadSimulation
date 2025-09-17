package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CustomerService {

    // 创建客户
    Customer createCustomer(Customer customer);

    // 更新客户信息
    Customer updateCustomer(Long id, Customer customerDetails);

    // 获取所有客户
    List<Customer> getAllCustomers();

    // 分页获取客户
    Page<Customer> getAllCustomers(Pageable pageable);

    // 根据ID获取客户
    Optional<Customer> getCustomerById(Long id);

    // 根据编码获取客户
    Optional<Customer> getCustomerByCode(String code);

    // 根据名称搜索客户
    List<Customer> searchCustomersByName(String name);

    // 删除客户
    void deleteCustomer(Long id);

    // 检查客户编码是否存在
    boolean existsByCode(String code);
}