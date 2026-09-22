package com.albertoborsetta.formscanner.commons;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.albertoborsetta.formscanner.api.FormGroup;
import com.albertoborsetta.formscanner.api.FormQuestion;
import com.albertoborsetta.formscanner.api.FormTemplate;
import com.albertoborsetta.formscanner.api.commons.Constants;
import com.albertoborsetta.formscanner.commons.translation.FormScannerTranslation;
import com.albertoborsetta.formscanner.commons.translation.FormScannerTranslationKeys;

/**
 * Exporta los resultados de corrección a un libro Excel (.xlsx).
 * <p>
 * Misma estructura que el CSV de {@link FormFileUtils}: primera columna con el
 * nombre del archivo del examen y una columna por cada pregunta/área. Clase sin
 * dependencias Swing.
 */
public class ExcelExporter {

	private static final Logger logger = LogManager.getLogger(ExcelExporter.class.getName());

	private ExcelExporter() {
	}

	/**
	 * Escribe el libro .xlsx en el archivo indicado.
	 *
	 * @param file        destino del fichero .xlsx
	 * @param filledForms mapas con los formularios rellenados a exportar
	 * @return el fichero generado, o {@code null} si no hay formularios
	 */
	public static File export(File file, HashMap<String, FormTemplate> filledForms) {
		if (filledForms == null || filledForms.isEmpty()) {
			return null;
		}

		ArrayList<String> headerKeys = new ArrayList<>();
		HashMap<String, String> groupNames = new HashMap<>();
		HashMap<String, String> fieldNames = new HashMap<>();
		FormTemplate aForm = filledForms.values().iterator().next();
		buildHeader(aForm, headerKeys, groupNames, fieldNames);

		ArrayList<String> sortedKeys = new ArrayList<>(headerKeys);
		Collections.sort(sortedKeys);
		ArrayList<HashMap<String, String>> results = buildResults(filledForms, headerKeys, groupNames, fieldNames);

		String[] columnKeys = new String[sortedKeys.size() + 1];
		columnKeys[0] = firstColumn();
		for (int i = 0; i < sortedKeys.size(); i++) {
			columnKeys[i + 1] = sortedKeys.get(i);
		}

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Resultados");

			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);

			Row headerRow = sheet.createRow(0);
			for (int c = 0; c < columnKeys.length; c++) {
				Cell cell = headerRow.createCell(c);
				cell.setCellValue(columnKeys[c]);
				cell.setCellStyle(headerStyle);
			}

			int rowIndex = 1;
			for (HashMap<String, String> result : results) {
				Row row = sheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(result.remove(firstColumn()));
				for (int c = 1; c < columnKeys.length; c++) {
					String value = result.get(columnKeys[c]);
					row.createCell(c).setCellValue(value == null ? "" : value);
				}
			}

			for (int c = 0; c < columnKeys.length; c++) {
				sheet.autoSizeColumn(c);
			}

			try (FileOutputStream fos = new FileOutputStream(file)) {
				workbook.write(fos);
			}
		} catch (IOException e) {
			logger.debug("Error exportando a Excel", e);
			return null;
		}

		return file;
	}

	private static void buildHeader(FormTemplate template, ArrayList<String> headerKeys,
			HashMap<String, String> groupNames, HashMap<String, String> fieldNames) {
		for (Entry<String, FormGroup> groupEntry : template.getGroups().entrySet()) {
			FormGroup group = groupEntry.getValue();
			if (group.getFields() == null) {
				continue;
			}
			for (Entry<String, FormQuestion> fieldEntry : group.getFields().entrySet()) {
				String headerKey = fieldEntry.getKey();
				if (!groupEntry.getKey().equals(Constants.EMPTY_GROUP_NAME)) {
					headerKey = groupEntry.getKey() + "." + headerKey;
				}
				headerKeys.add(headerKey);
				groupNames.put(headerKey, groupEntry.getKey());
				fieldNames.put(headerKey, fieldEntry.getKey());
			}
		}
		for (Entry<String, FormGroup> groupEntry : template.getGroups().entrySet()) {
			FormGroup group = groupEntry.getValue();
			if (group.getAreas() == null) {
				continue;
			}
			for (Entry<String, com.albertoborsetta.formscanner.api.FormArea> areaEntry : group.getAreas().entrySet()) {
				String headerKey = areaEntry.getKey();
				if (!groupEntry.getKey().equals(Constants.EMPTY_GROUP_NAME)) {
					headerKey = groupEntry.getKey() + "." + headerKey;
				}
				headerKeys.add(headerKey);
				groupNames.put(headerKey, groupEntry.getKey());
				fieldNames.put(headerKey, areaEntry.getKey());
			}
		}
	}

	private static ArrayList<HashMap<String, String>> buildResults(HashMap<String, FormTemplate> filledForms,
			ArrayList<String> headerKeys, HashMap<String, String> groupNames, HashMap<String, String> fieldNames) {
		ArrayList<HashMap<String, String>> results = new ArrayList<>();
		for (Entry<String, FormTemplate> filledForm : filledForms.entrySet()) {
			String fileName = filledForm.getKey();
			FormTemplate form = filledForm.getValue();
			HashMap<String, FormGroup> groups = form.getGroups();
			HashMap<String, String> result = new HashMap<>();

			FormGroup group = groups.get(groups.isEmpty() ? null : groups.keySet().iterator().next());
			for (String headerKey : headerKeys) {
				FormGroup rowGroup = groups.get(groupNames.get(headerKey));
				String fieldName = fieldNames.get(headerKey);

				FormQuestion field = rowGroup == null ? null : rowGroup.getFields().get(fieldName);
				if (field != null) {
					String value = field.getValues();
					if (!value.isEmpty()) {
						result.put(headerKey, value);
					}
					continue;
				}
				com.albertoborsetta.formscanner.api.FormArea area = rowGroup == null ? null
						: rowGroup.getAreas().get(fieldName);
				if (area != null) {
					String text = area.getText();
					if (text != null) {
						result.put(headerKey, text);
					}
				}
			}
			result.put(firstColumn(), fileName);
			results.add(result);
		}
		return results;
	}

	private static String firstColumn() {
		try {
			return FormScannerTranslation.getTranslationFor(FormScannerTranslationKeys.FIRST_CSV_COLUMN);
		} catch (Exception e) {
			return "";
		}
	}
}