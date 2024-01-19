package com.example.onlineorder.repository;

import com.example.onlineorder.entity.CustomerEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface CustomerRepository extends ListCrudRepository<CustomerEntity, Long> {


    List<CustomerEntity> findByFirstName(String firstName);

    List<CustomerEntity> findByLastName(String lastName);

    List<CustomerEntity> findByFirstNameStartingWith(String startWith);

    CustomerEntity findByEmail(String email);


    @Modifying
    @Query("UPDATE customers SET first_name = :firstName, last_name = :lastName WHERE id = :id")
    void updateNameById(long id, String firstName, String lastName);
}

