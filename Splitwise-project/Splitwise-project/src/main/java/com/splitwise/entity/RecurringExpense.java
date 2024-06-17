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
	private Long recurringId;

	@Column(name = "recurring_desp")
	private String recurringDescription;

	@Column(name = "recurring_amt")
	private Double recurringAmount;

	@Column(name = "start_date")
	private Date startDate;

	@Column(name = "recurring_period")
	private String recurringPeriod;

	@ManyToOne
	private Category category;

	@ManyToOne
	private Group group;

	@ManyToOne
	private User createdBy;

}
