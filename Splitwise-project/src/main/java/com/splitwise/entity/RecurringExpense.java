package com.splitwise.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "recurring_expense")
public class RecurringExpense {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recurring_id")
	private Long id;

	@Column(name = "recurring_desp")
	private String description;

	@Column(name = "recurring_amt")
	private Double amount;

	private Date startDate;

	@Column(name = "recurring_period")
	private String recurrencePeriod;

	@ManyToOne
	private Category category;

	@ManyToOne
	private Group group;

	@ManyToOne
	private User createdBy;

}
