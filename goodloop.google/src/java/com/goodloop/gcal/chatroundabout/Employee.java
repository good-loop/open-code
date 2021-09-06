package com.goodloop.gcal.chatroundabout;

import java.util.Objects;

public class Employee {

	String email;
	String name;
	String office;
	String team;
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Employee other = (Employee) obj;
		return Objects.equals(email, other.email);
	}
	@Override
	public int hashCode() {
		return Objects.hash(email);
	}
	
	public Employee(String email, String name, String office, String team) {
		super();
		this.email = email;
		this.name = name;
		this.office = office;
		this.team = team;
	}
	
	@Override
	public String toString() {
		return "Employee[email=_" + email + "_, team=" + team + "]";
	}
	
}
