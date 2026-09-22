package com.albertoborsetta.formscanner.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.albertoborsetta.formscanner.api.FormGroup;
import com.albertoborsetta.formscanner.api.FormPoint;
import com.albertoborsetta.formscanner.api.FormQuestion;
import com.albertoborsetta.formscanner.api.FormTemplate;
import com.albertoborsetta.formscanner.api.commons.Constants;

class ExcelExporterTest {

	@TempDir
	File tempDir;

	private FormTemplate filled(String name, String... valores) {
		FormTemplate t = new FormTemplate(name);
		t.setGroupsEnabled(true);
		FormGroup group = new FormGroup();
		FormQuestion question = new FormQuestion("PREGUNTA1");
		question.setType(Constants.FieldType.QUESTIONS_BY_ROWS);
		for (String valor : valores) {
			question.setPoint(valor, new FormPoint(100, 100));
		}
		group.setField("PREGUNTA1", question);
		t.addGroup("GRUPO", group);
		return t;
	}

	@Test
	void exportaResultadosEnXlsx() throws IOException {
		FormTemplate a = filled("alumno1.jpg", "A");
		FormTemplate b = filled("alumno2.jpg", "B", "C");

		HashMap<String, FormTemplate> filledForms = new java.util.LinkedHashMap<>();
		filledForms.put(a.getName(), a);
		filledForms.put(b.getName(), b);

		File out = new File(tempDir, "resultados.xlsx");
		File exported = ExcelExporter.export(out, filledForms);

		assertNotNull(exported);
		assertTrue(exported.exists());
		assertTrue(exported.length() > 0);

		try (Workbook workbook = new XSSFWorkbook(new FileInputStream(exported))) {
			Sheet sheet = workbook.getSheet("Resultados");
			assertNotNull(sheet);

			assertEquals("GRUPO.PREGUNTA1", sheet.getRow(0).getCell(1).getStringCellValue());

			Row row1 = sheet.getRow(1);
			assertEquals("alumno1.jpg", row1.getCell(0).getStringCellValue());
			assertEquals("A", row1.getCell(1).getStringCellValue());

			Row row2 = sheet.getRow(2);
			assertEquals("alumno2.jpg", row2.getCell(0).getStringCellValue());
			assertEquals("B|C", row2.getCell(1).getStringCellValue());
		}
	}

	@Test
	void sinFormulariosDevuelveNull() {
		assertEquals(null, ExcelExporter.export(new File(tempDir, "vacio.xlsx"), new HashMap<>()));
	}
}