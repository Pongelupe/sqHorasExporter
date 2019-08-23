package com.prosegur.apontamento.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimesheetEntry {

	private int day;
	private int weekOfMonth;
	private LocalDate date;
	private String weekday;
	private LocalTime start1;
	private LocalTime end1;
	private LocalTime start2;
	private LocalTime end2;
	private ClarityTask clarityTask;

	public TimesheetEntry(LocalDate date, List<LocalTime> appointments) {
		this.date = date;
		start1 = getAppointmentByIndex(appointments, 0);
		end1 = getAppointmentByIndex(appointments, 1);
		start2 = getAppointmentByIndex(appointments, 2);
		end2 = getAppointmentByIndex(appointments, 3);
	}

	private LocalTime getAppointmentByIndex(List<LocalTime> appointments,
			int index) {
		try {
			return appointments.get(index);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

}
