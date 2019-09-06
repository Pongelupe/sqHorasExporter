package com.prosegur.apontamento.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class Timesheet {

	boolean allowWeekend;
	String mesano;
	Date targetMonth;
	List<TimesheetEntry> entries = new ArrayList<>();
	List<ClarityTimesheet> clarityTimesheets = new ArrayList<>();

	public int getYear() {
		return Integer.valueOf(getMesano().substring(3, 7));
	}

	public int getMonth() {
		return Integer.valueOf(getMesano().substring(0, 2));
	}

	public void addEntry(TimesheetEntry entry) {
		this.entries.add(entry);
	}

}
