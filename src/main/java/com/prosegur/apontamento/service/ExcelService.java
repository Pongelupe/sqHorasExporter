package com.prosegur.apontamento.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.prosegur.apontamento.model.Timesheet;

@Service
public class ExcelService {

	private static final String HH_MM = "HH:mm";
	private static final String TEMPLATE_XLSX = "template.xlsx";
	private static final String TEMPLATE_PROSEGUR_XLS = "template_prosegur.xls";

	public ByteArrayOutputStream execute(Timesheet timesheet)
			throws IOException {

		Resource template = new ClassPathResource(TEMPLATE_XLSX);
		try (InputStream input = template.getInputStream()) {
			try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
				XSSFSheet sheetPonto = workbook.getSheetAt(0);

				int year = timesheet.getYear();
				int month = timesheet.getMonth();

				Cell mesCell = sheetPonto.getRow(4).getCell(5);
				Cell anoCell = sheetPonto.getRow(5).getCell(5);
				mesCell.setCellValue(month);
				anoCell.setCellValue(year);

				final AtomicInteger row = new AtomicInteger(15);
				final AtomicInteger col = new AtomicInteger(6);

				timesheet.getEntries().forEach(entry -> {
					XSSFCell cell = sheetPonto.getRow(row.get())
							.getCell(col.getAndIncrement());
					cell.setCellValue(getCellValue(entry.getStart1()));
					cell = sheetPonto.getRow(row.get())
							.getCell(col.getAndIncrement());
					cell.setCellValue(getCellValue(entry.getEnd1()));
					cell = sheetPonto.getRow(row.get())
							.getCell(col.getAndIncrement());
					cell.setCellValue(getCellValue(entry.getStart2()));
					cell = sheetPonto.getRow(row.getAndIncrement())
							.getCell(col.getAndSet(6));
					cell.setCellValue(getCellValue(entry.getEnd2()));
				});

				executeFormulas(workbook);

				try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
					workbook.write(out);
					return out;
				}
			}
		}
	}

	public ByteArrayOutputStream executeTemplateProsegur(Timesheet timesheet)
			throws IOException {
		Resource template = new ClassPathResource(TEMPLATE_PROSEGUR_XLS);
		try (InputStream input = template.getInputStream()) {
			try (Workbook workbook = WorkbookFactory.create(input)) {
				Sheet sheet = workbook.getSheetAt(0);

				Supplier<Stream<Row>> streamSupplier = () -> StreamSupport
						.stream(Spliterators.spliteratorUnknownSize(
								sheet.iterator(), Spliterator.ORDERED), false);
				streamSupplier.get().filter(row -> row.getRowNum() == 4)
						.map(row -> row.getCell(2)).findAny()
						.ifPresent(cell -> cell
								.setCellValue(timesheet.getTargetMonth()));

				final AtomicInteger row = new AtomicInteger(8);
				final AtomicInteger col = new AtomicInteger(5);

				timesheet.getEntries().forEach(entry -> {
					Cell cell = sheet.getRow(row.get())
							.getCell(col.getAndIncrement());
					cell.setCellValue(getCellValue(entry.getStart1()));
					cell = sheet.getRow(row.get())
							.getCell(col.getAndIncrement());
					cell.setCellValue(getCellValue(entry.getEnd1()));
					cell = sheet.getRow(row.get())
							.getCell(col.getAndIncrement());
					cell.setCellValue(getCellValue(entry.getStart2()));
					cell = sheet.getRow(row.getAndIncrement())
							.getCell(col.getAndSet(5));
					cell.setCellValue(getCellValue(entry.getEnd2()));
				});

				try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
					workbook.write(out);
					return out;
				}
			}
		}
	}

	private void executeFormulas(Workbook workbook) {
		FormulaEvaluator evaluator = workbook.getCreationHelper()
				.createFormulaEvaluator();
		for (Sheet sheet : workbook) {
			for (Row r : sheet) {
				for (Cell c : r) {
					if (c.getCellType() == CellType.FORMULA) {
						try {
							evaluator.evaluateFormulaCell(c);
						} catch (Exception e) {
							// ignore cell
						}
					}
				}
			}
		}
	}

	private String getCellValue(LocalTime time) {
		return Optional.ofNullable(time)
				.map(t -> t.format(DateTimeFormatter.ofPattern(HH_MM)))
				.orElse(StringUtils.EMPTY);
	}

}
