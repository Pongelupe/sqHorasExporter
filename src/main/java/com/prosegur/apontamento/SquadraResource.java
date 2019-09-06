package com.prosegur.apontamento;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prosegur.apontamento.model.LoginInfo;
import com.prosegur.apontamento.model.Timesheet;
import com.prosegur.apontamento.service.ExcelService;
import com.prosegur.apontamento.service.squadra.RMPortalService;
import com.prosegur.apontamento.service.squadra.SqHorasService;

import lombok.Data;

@RestController
@RequestMapping("/api/squadra")
public class SquadraResource {

	@Autowired
	private SqHorasService sqHorasService;

	@Autowired
	private ExcelService excelService;

	@Autowired
	private RMPortalService rmPortalService;

	@Data
	public static class SquadraRequest {
		private LoginInfo loginInfoSqhoras;
		private LoginInfo loginInfoRMPortal;
		private boolean generateExcel;
		private Date month;
	}

	@PostMapping
	public ResponseEntity<Resource> generateExcelFromAppointmentsInSqHoras(
			@RequestBody SquadraRequest request) {
		try {
			Timesheet timesheet = sqHorasService.retrieveTimesheetFromSqHoras(
					request.getLoginInfoSqhoras(), request.getMonth());
			rmPortalService.appointFromTimesheet(timesheet,
					request.getLoginInfoRMPortal());
			if (request.isGenerateExcel()) {
				ByteArrayOutputStream sheet = excelService
						.executeTemplateProsegur(timesheet);

				ByteArrayResource resource = new ByteArrayResource(
						sheet.toByteArray());
				HttpHeaders headers = new HttpHeaders();
				headers.add(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=" + timesheet.getMesano()
								+ ".xls");
				return ResponseEntity.ok().headers(headers)
						.contentLength(resource.contentLength())
						.contentType(MediaType
								.parseMediaType("application/octet-stream"))
						.body(resource);
			} else {
				return ResponseEntity.ok().build();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().build();
		}
	}

}
