package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Customer;
import org.example.roadsimulation.repository.CustomerRepository;
import org.example.roadsimulation.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Autowired
    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public Customer createCustomer(Customer customer) {
        // 检查客户编码是否已存在
        if (customerRepository.existsByCode(customer.getCode())) {
            throw new IllegalArgumentException("客户编码已存在: " + customer.getCode());
        }

        return customerRepository.save(customer);
    }

    @Override
    public Customer updateCustomer(Long id, Customer customerDetails) {
        return customerRepository.findById(id)
                .map(customer -> {
                    // 检查新编码是否与其他客户冲突
                    if (!customer.getCode().equals(customerDetails.getCode()) &&
                            customerRepository.existsByCode(customerDetails.getCode())) {
                        throw new IllegalArgumentException("客户编码已存在: " + customerDetails.getCode());
                    }

                    customer.setCode(customerDetails.getCode());
                    customer.setName(customerDetails.getName());
                    customer.setContactPerson(customerDetails.getContactPerson());
                    customer.setContactPhone(customerDetails.getContactPhone());
                    customer.setAddress(customerDetails.getAddress());

                    return customerRepository.save(customer);
                })
                .orElseThrow(() -> new RuntimeException("客户不存在，ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Customer> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerByCode(String code) {
        return customerRepository.findByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> searchCustomersByName(String name) {
        return customerRepository.findByNameContainingIgnoreCase(name);
    }

    @Override
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("客户不存在，ID: " + id));

        // 检查是否有关联的运单
        if (!customer.getShipments().isEmpty()) {
            throw new IllegalStateException("无法删除客户，存在关联的运单");
        }

        customerRepository.delete(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return customerRepository.existsByCode(code);
    }
}