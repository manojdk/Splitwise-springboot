package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.splitwise.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
