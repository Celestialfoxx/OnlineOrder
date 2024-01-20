package com.example.onlineorder.service;
import com.example.onlineorder.entity.CustomerEntity;
import com.example.onlineorder.repository.CustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {


    private final CustomerRepository customerRepository;


    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }


    public CustomerEntity getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email);
    }
}
