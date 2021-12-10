package com.goodloop.gcal.chatroundabout;

import java.util.Objects;

public class Employee {

	String name;
	String office;
	String team;
	String firstName;
	String email;
	
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
	
	public Employee(String name, String office, String team) {
		super();
		this.name = name;
		this.office = office;
		this.team = team;
		this.firstName = getFirstName();
		this.email = firstName + "@good-loop.com";
	}
	
	String getFirstName() {
		// HACK: non-standard names
		switch(name) {
		case "Natasha Taylor": return "Tash";
		case "Abdikarim Mohamed": return "Karim";
		}
		return name.split(" ")[0];
	}
	
	@Override
	public String toString() {
		return "Employee[ " + email + " ]";
	}
	
}
