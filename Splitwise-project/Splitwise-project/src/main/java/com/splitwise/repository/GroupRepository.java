package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.splitwise.entity.Group;

public interface GroupRepository extends JpaRepository<Group, Long>{

}
