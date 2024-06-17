package com.splitwise.entity;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "group")
public class Group {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long groupId;

	@Column(name = "group_name")
	private String groupName;

	private String currency;

	@ManyToMany
	@JoinTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
	private Set<User> members;

}
