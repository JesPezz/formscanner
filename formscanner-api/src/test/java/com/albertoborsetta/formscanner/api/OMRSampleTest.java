package com.albertoborsetta.formscanner.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Pruebas golden del pipeline OMR real sobre las imágenes de muestra del
 * proyecto (samples/). Valores verificados a mano ejecutando el CLI original
 * sobre las mismas muestras.
 */
public class OMRSampleTest {

	private FormTemplate process(String templateResource, String imageResource)
			throws Exception {
		FormTemplate template = new FormTemplate(file(templateResource));
		BufferedImage image = ImageIO.read(file(imageResource));

		FormTemplate filled = new FormTemplate(imageResource, template);
		filled.findCorners(image, template.getThreshold(), template.getDensity(),
				template.getCornerType(), template.getCrop());
		filled.findPoints(image, template.getThreshold(), template.getDensity(),
				template.getSize());
		filled.findAreas(image);
		return filled;
	}

	private Map<String, String> valuesOf(FormTemplate filled) {
		Map<String, String> values = new HashMap<>();
		for (String groupName : filled.getGroups().keySet()) {
			FormGroup group = filled.getGroups().get(groupName);
			for (String fieldName : group.getFields().keySet()) {
				values.put(groupName + "." + fieldName,
						group.getFields().get(fieldName).getValues());
			}
			for (String areaName : group.getAreas().keySet()) {
				values.put(groupName + "." + areaName,
						group.getAreas().get(areaName).getText());
			}
		}
		return values;
	}

	@Test
	void cuestionarioGolden() throws Exception {
		String template = "samples/questionnaire/questionnaire_template.xtmpl";
		assertRow(process(template, "samples/questionnaire/Q00001.jpg"),
				new String[] { "Q1.gender=M", "Q2.age=25-29", "Q3.restaurants=3-4",
						"Q4.movies=strongly agree", "Q4.music=agree",
						"Q4.reading=disagree", "Q5.sources=TV|internet|magazines" });
		assertRow(process(template, "samples/questionnaire/Q00002.jpg"),
				new String[] { "Q1.gender=M", "Q2.age=40-49", "Q3.restaurants=<1",
						"Q4.movies=agree", "Q4.music=disagree",
						"Q4.reading=disagree", "Q5.sources=TV|newspapers|radio" });
		assertRow(process(template, "samples/questionnaire/Q00003.jpg"),
				new String[] { "Q1.gender=F", "Q2.age=30-39", "Q3.restaurants=5-6",
						"Q4.movies=agree", "Q4.music=agree",
						"Q4.reading=strongly agree",
						"Q5.sources=TV|internet|newspapers" });
		assertRow(process(template, "samples/questionnaire/Q00004.jpg"),
				new String[] { "Q1.gender=F", "Q2.age=<25", "Q3.restaurants=1-2",
						"Q4.movies=strongly agree", "Q4.music=strongly agree",
						"Q4.reading=strongly agree", "Q5.sources=TV|internet" });
		assertRow(process(template, "samples/questionnaire/Q00005.jpg"),
				new String[] { "Q1.gender=F", "Q2.age=>=60", "Q3.restaurants=",
						"Q4.movies=agree", "Q4.music=disagree",
						"Q4.reading=disagree", "Q5.sources=TV|radio" });
	}

	private void assertRow(FormTemplate filled, String[] expected) {
		Map<String, String> values = valuesOf(filled);
		values.put("Q6.interviewer",
				values.get("Q6.interviewer").replace("\r", ""));
		assertEquals("Interviewer: Mario Rossi\nInverviewer ID: 458788",
				values.get("Q6.interviewer"), "barcode Q6");
		for (String cell : expected) {
			String[] kv = cell.split("=", 2);
			assertEquals(kv[1], values.get(kv[0]), "campo " + kv[0]);
		}
	}

	@Test
	void muestraTestRegresionBurbujas() throws Exception {
		Map<String, String> values = valuesOf(process(
				"samples/test/test_template.xtmpl", "samples/test/T00001.jpg"));
		assertEquals("C", values.get("EMPTY.Question31"));
	}

	private File file(String resource) throws URISyntaxException {
		URL url = getClass().getClassLoader().getResource(resource);
		if (url == null) {
			throw new IllegalArgumentException("Recurso no encontrado: " + resource);
		}
		return new File(url.toURI());
	}
}