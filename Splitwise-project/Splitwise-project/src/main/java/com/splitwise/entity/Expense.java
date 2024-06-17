package com.splitwise.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Table(name = "expense")
@Data
public class Expense {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@NotNull
	@Column(name = "expense_id")
	private Long expenseId;

	@Column(name = "expense_desc")
	@NotBlank(message = "Expense description is madatory")
	private String expenseDescription;

	@Column(name = "expense_amt")
	private Double expenseAmount;

	@Column(name = "expense_date")
	private Date expenseDate;

	@ManyToOne
	private User createdBy;

}
