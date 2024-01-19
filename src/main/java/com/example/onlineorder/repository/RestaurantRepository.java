package com.example.onlineorder.repository;

import com.example.onlineorder.entity.RestaurantEntity;
import org.springframework.data.repository.ListCrudRepository;

public interface RestaurantRepository extends ListCrudRepository<RestaurantEntity, Long> {
    //the function in super class is enough
    //don't need to create other APIs here
}
