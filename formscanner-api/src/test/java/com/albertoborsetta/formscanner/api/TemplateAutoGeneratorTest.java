package com.albertoborsetta.formscanner.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.albertoborsetta.formscanner.api.commons.Constants;
import com.albertoborsetta.formscanner.api.commons.Constants.Corners;

/**
 * Valida que la generación automática de plantilla reproduce la geometría del
 * sample del proyecto: esquinas del marco y burbujas de las 40 preguntas.
 */
class TemplateAutoGeneratorTest {

	private static BufferedImage image;
	private static FormTemplate generated;

	@BeforeAll
	static void setUp() throws Exception {
		image = ImageIO.read(TemplateAutoGeneratorTest.class
				.getResourceAsStream("/samples/test/T00001.jpg"));
		generated = TemplateAutoGenerator.generate(image, "autogenerada");
	}

	@Test
	void detectaLasCuatroEsquinas() {
		Map<Corners, FormPoint> genCorners = generated.getCorners();
		assertEquals(4, genCorners.size(), "esquinas detectadas: " + genCorners.size());
		for (FormPoint corner : genCorners.values()) {
			assertTrue(corner.getX() >= 0 && corner.getX() < image.getWidth(),
					"esquina fuera de la imagen en x=" + corner.getX());
			assertTrue(corner.getY() >= 0 && corner.getY() < image.getHeight(),
					"esquina fuera de la imagen en y=" + corner.getY());
		}
	}	@Test
	void detectaLasCuarentaPreguntasDeRespuesta() {
		List<FormQuestion> preguntas = preguntasDeRespuesta();
		assertTrue(preguntas.size() >= 40,
				"preguntas de 4 opciones detectadas: " + preguntas.size());
	}

	@Test
	void laTerceraOpcionDeLaBurbujaDeLaPregunta31() {
		// La burbuja C de la pregunta 31 del sample real está en (2048, 1903);
		// la plantilla generada debe reproducir esa geometría.
		FormQuestion encontrada = preguntaConPuntoCercaDe("C", 2048.0, 1903.0, 8.0);
		assertTrue(encontrada != null, "ninguna pregunta de respuesta tiene su opción C en (2048,1903)");
	}

	@Test
	void laPlantillaGeneradaReconoceLasMarcas() throws Exception {
		FormTemplate filled = new FormTemplate("rellena", generated);
		filled.findCorners(image, TemplateAutoGenerator.DEFAULT_THRESHOLD, TemplateAutoGenerator.DEFAULT_DENSITY,
				Constants.CornerType.ANGULAR, cropCero());
		filled.findPoints(image, TemplateAutoGenerator.DEFAULT_THRESHOLD, TemplateAutoGenerator.DEFAULT_DENSITY,
				TemplateAutoGenerator.DEFAULT_SIZE);
		FormQuestion q31 = preguntaConPuntoCercaDe("C", 2048.0, 1903.0, 8.0);
		assertTrue(q31 != null, "no se localizó geométricamente la pregunta 31");
		assertTrue(q31.getValues().contains("C"),
				"la pregunta 31 debía marcar C pero devolvió: " + q31.getValues());
		int respondidas = 0;
		for (FormQuestion question : preguntasDeRespuesta()) {
			if (!question.getValues().isEmpty()) {
				respondidas++;
			}
		}
		assertTrue(respondidas >= 20, "preguntas de respuesta marcadas: " + respondidas);
	}

	private static FormQuestion preguntaConPuntoCercaDe(String valor, double x, double y, double tol) {
		for (FormQuestion question : preguntasDeRespuesta()) {
			FormPoint punto = question.getPoints().get(valor);
			if (punto == null) {
				continue;
			}
			if (Math.abs(punto.getX() - x) <= tol && Math.abs(punto.getY() - y) <= tol) {
				return question;
			}
		}
		return null;
	}

	private static List<FormQuestion> preguntasDeRespuesta() {
		List<FormQuestion> result = new ArrayList<>();
		for (FormGroup group : generated.getGroups().values()) {
			result.addAll(group.getFields().values());
		}
		return result;
	}

	private static HashMap<String, Integer> cropCero() {
		HashMap<String, Integer> crop = new HashMap<>();
		crop.put(Constants.TOP, 0);
		crop.put(Constants.LEFT, 0);
		crop.put(Constants.RIGHT, 0);
		crop.put(Constants.BOTTOM, 0);
		return crop;
	}
}