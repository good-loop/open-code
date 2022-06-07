package com.goodloop.gcal.chatroundabout;

import java.util.Objects;

public class Employee {

	String name;
	String office;
	String team;
	String firstName;
	String lastName;
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
	
	public Employee(String firstName, String lastName, String office, String team) {
		super();
		this.firstName = firstName;
		this.lastName = lastName;
		this.name = firstName + " " + lastName;
		this.office = office;
		this.team = team;
		this.email = getEmail();
	}
	
	/**
	 * @deprecated
	 */
	String getFirstName() {
		// HACK: non-standard names
		switch(name) {
		case "Natasha Taylor": return "Tash";
		case "Abdikarim Mohamed": return "Karim";
		}
		return name.split(" ")[0];
	}
	
	String getEmail() {
		String emailTail = "@good-loop.com";
		String emailHead = new String();
		switch(firstName) {
			case "Natasha": emailHead="tash"; break;
			case "Abdikarim": emailHead="karim"; break;
			case "Wing Sang": emailHead="wing"; break;
			case "Claire": emailHead= (lastName.equals("Gleeson-Landry") ? "claire" : "clairedillon"); break;
			default: emailHead=firstName.toLowerCase(); break;
		}
//		if (emailHead.length() < 1) emailHead = firstName.toLowerCase();
		return emailHead + emailTail;
	}
	
	@Override
	public String toString() {
		return "Employee[ " + email + ", " + office + " ]";
	}
	
}
