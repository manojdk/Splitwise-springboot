package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.splitwise.entity.Group;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long>{

}
