package com.prosegur.apontamento;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prosegur.apontamento.model.LoginInfo;
import com.prosegur.apontamento.model.Timesheet;
import com.prosegur.apontamento.model.TimesheetEntry;
import com.prosegur.apontamento.service.CertpontoService;
import com.prosegur.apontamento.service.ClarityService;
import com.prosegur.apontamento.service.ExcelService;
import com.prosegur.apontamento.service.T2Service;
import com.prosegur.apontamento.service.squadra.RMPortalService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@RestController
@RequestMapping("/api/timesheet")
public class TimesheetResource {

	@Autowired
	private ExcelService excelService;

	@Autowired
	private T2Service t2Service;

	@Autowired
	private CertpontoService certpontoService;

	@Autowired
	private ClarityService clarityService;

	@Autowired
	private RMPortalService portalRMService;

	@RequestMapping(method = RequestMethod.GET)
	public Timesheet get(@RequestParam(value = "mesano") String mesano) {
		Timesheet result = new Timesheet();
		result.setMesano(mesano);
		result.setAllowWeekend(false);
		YearMonth yearMonth = YearMonth.parse(mesano,
				DateTimeFormatter.ofPattern("MM/yyyy"));
		LocalDate initialDate = LocalDate.from(yearMonth.atDay(1));
		LocalDate finalDate = LocalDate.from(yearMonth.plusMonths(1).atDay(1));
		int weekOfMonthAdjustmentconstant = initialDate
				.get(WeekFields.ISO.weekOfMonth()) == 0 ? 1 : 0;
		for (LocalDate date = initialDate; date
				.isBefore(finalDate); date = date.plusDays(1)) {
			result.getEntries().add(TimesheetEntry.builder()
					.weekOfMonth(date.get(WeekFields.ISO.weekOfMonth())
							+ weekOfMonthAdjustmentconstant)
					.weekday(date.getDayOfWeek().getDisplayName(TextStyle.SHORT,
							Locale.forLanguageTag("pt")))
					.day(date.getDayOfMonth()).start1(LocalTime.of(0, 0))
					.end1(LocalTime.of(0, 0)).start2(LocalTime.of(0, 0))
					.end2(LocalTime.of(0, 0)).build());
		}

		return result;
	}

	@RequestMapping(value = "/excel", method = RequestMethod.POST)
	public ResponseEntity<Resource> generatePlanilha(
			@RequestBody Timesheet timesheet) {
		try {
			ByteArrayOutputStream sheet = excelService.execute(timesheet);
			ByteArrayResource resource = new ByteArrayResource(
					sheet.toByteArray());
			HttpHeaders headers = new HttpHeaders();
			headers.add(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=" + timesheet.getMesano() + ".xlsx");
			return ResponseEntity.ok().headers(headers)
					.contentLength(resource.contentLength())
					.contentType(MediaType
							.parseMediaType("application/octet-stream"))
					.body(resource);

		} catch (IOException e) {
			return ResponseEntity.badRequest().build();
		}
	}

	@Data
	public static class T2Request {
		LoginInfo loginInfo;
		Timesheet timesheet;
	}

	@RequestMapping(value = "/t2", method = RequestMethod.POST)
	public ResponseEntity apontarT2(@RequestBody T2Request request) {
		try {
			t2Service.execute(request.timesheet, request.loginInfo);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Data
	public static class CertpontoRequest {
		LocalDate start;
		LocalDate end;
		LoginInfo loginInfo;
	}

	@RequestMapping(value = "/certponto", method = RequestMethod.POST)
	public ResponseEntity apontarCertponto(
			@RequestBody CertpontoRequest request) {
		try {
			certpontoService.execute(request.start, request.end,
					request.loginInfo);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Data
	public static class ClarityRequest {
		LoginInfo loginInfo;
		Timesheet timesheet;
	}

	@RequestMapping(value = "/clarity", method = RequestMethod.POST)
	public ResponseEntity apontarClarity(@RequestBody ClarityRequest request) {
		try {
			clarityService.execute(request.timesheet, request.loginInfo);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@RequestMapping(value = "/clarity/sync", method = RequestMethod.POST)
	public ResponseEntity<Timesheet> sincronizarClarity(
			@RequestBody ClarityRequest request) {
		try {
			clarityService.sync(request.timesheet, request.loginInfo);
			return ResponseEntity.ok(request.timesheet);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Data
	public static class PortalRMRequest {
		LoginInfo loginInfo;
		List<Entry> entries;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class Entry {
			String date;
			List<String> appointments;
		}
	}

	@PostMapping("/portalRM")
	public ResponseEntity<?> apontarPortalRM(
			@RequestBody PortalRMRequest request) {

		try {
			portalRMService.execute(request);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

}
