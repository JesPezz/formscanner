package com.albertoborsetta.formscanner.api;

import com.albertoborsetta.formscanner.api.commons.Constants;
import com.albertoborsetta.formscanner.api.commons.Constants.CornerType;
import com.albertoborsetta.formscanner.api.commons.Constants.FieldType;
import com.albertoborsetta.formscanner.api.commons.Constants.ShapeType;
import com.albertoborsetta.formscanner.api.exceptions.FormScannerException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

/**
 * Genera automáticamente una plantilla OMR a partir de la imagen escaneada de
 * un formulario: detecta el marco de esquinas y las bandas de burbujas de
 * respuesta, y crea un {@link FormTemplate} con las preguntas ya pobladas.
 * <p>
 * Sustituye el trabajo manual que hoy se hace en la ventana de gestión de
 * plantillas (dos clics por campo + dimensiones) por pura imagen, eliminando
 * la mayor fricción del flujo de uso.
 * <p>
 * Heurística de detección (validada sobre los samples del proyecto):
 * <ol>
 * <li>Binariza con el mismo criterio OMR (píxel negro si el canal más claro
 * queda bajo el umbral).</li>
 * <li>Etiqueta componentes conexos y conserva los blobs cuasi-cuadrados del
 * tamaño de una burbuja (~30-75 px).</li>
 * <li>Agrupa los centroides en líneas de columna y de fila y los parte en
 * "bandas" por huecos grandes (rellenos, separadores, zonas de texto).</li>
 * <li>Descarta las bandas con menos de 3 líneas en ambos ejes (la decoración
 * de cabecera aparece como filas sueltas) y emite un grupo por banda.</li>
 * </ol>
 * El tipo de pregunta se elige según el número de líneas: horizontal
 * (más columnas que filas) sale como QUESTIONS_BY_COLS y vertical como
 * QUESTIONS_BY_ROWS. Todas las preguntas se marcan como de opción múltiple.
 *
 * @see FormTemplate
 */
public class TemplateAutoGenerator {

	public static final int DEFAULT_THRESHOLD = 127;
	public static final int DEFAULT_DENSITY = 40;
	public static final int DEFAULT_SIZE = 15;

	private static final int MIN_BUBBLE_BBOX = 30;
	private static final int MAX_BUBBLE_BBOX = 75;
	private static final int MAX_BBOX_ASYMMETRY = 20;
	// Los anillos de burbuja son más tenues que las marcas de esquina: se
	// detectan con un umbral más bajo que el de corrección (DEFAULT_THRESHOLD).
	private static final int RING_DETECTION_THRESHOLD = 150;
	private static final int COLUMN_TOLERANCE = 30;
	private static final int ROW_TOLERANCE = 25;
	private static final int MIN_BAND_LINES = 3;
	private static final double MIN_BAND_GAP = 120.0;
	private static final double BAND_GAP_FACTOR = 2.5;

	private TemplateAutoGenerator() {
	}

	/**
	 * Genera una plantilla a partir de la imagen de un formulario.
	 *
	 * @param image        la imagen escaneada del formulario
	 * @param templateName el nombre que recibirá la plantilla
	 * @return una plantilla con esquinas, rotación y preguntas ya detectadas
	 * @throws FormScannerException si la búsqueda de esquinas falla
	 */
	public static FormTemplate generate(BufferedImage image, String templateName) throws FormScannerException {
		FormTemplate template = new FormTemplate(templateName);
		template.setThreshold(DEFAULT_THRESHOLD);
		template.setDensity(DEFAULT_DENSITY);
		template.setSize(DEFAULT_SIZE);
		template.setShape(ShapeType.CIRCLE);
		template.setCornerType(CornerType.ANGULAR);
		template.setVersion(Constants.CURRENT_TEMPLATE_VERSION);
		template.setCrop(0, 0, 0, 0);

		HashMap<String, Integer> crop = new HashMap<>();
		crop.put(Constants.TOP, 0);
		crop.put(Constants.LEFT, 0);
		crop.put(Constants.RIGHT, 0);
		crop.put(Constants.BOTTOM, 0);
		template.findCorners(image, DEFAULT_THRESHOLD, DEFAULT_DENSITY, CornerType.ANGULAR, crop);

		List<Bubble> bubbles = detectBubbles(image);
		for (Band band : splitBands(bubbles)) {
			addGroup(template, band);
		}
		return template;
	}

	/**
	 * Serializa la plantilla a XML con la misma indentación y codificación que
	 * usa el proyecto ({@code getXml()} + Transformer).
	 */
	public static void saveToFile(File file, FormTemplate template) throws Exception {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

		Document xml = template.getXml();
		FileOutputStream fos = new FileOutputStream(file);
		try (OutputStreamWriter out = new OutputStreamWriter(fos, Charset.forName("UTF-8"))) {
			transformer.transform(new DOMSource(xml), new StreamResult(out));
		} catch (TransformerException ex) {
			throw new FormScannerException(ex.getMessage(), ex);
		}
	}

	/**
	 * Punto de entrada CLI: genera la plantilla de la imagen dada y la guarda
	 * como {@code <nombre>.xtmpl} en el directorio actual.
	 * <p>
	 * Uso: {@code TemplateAutoGenerator <imagen> [nombreDePlantilla]}
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Uso: TemplateAutoGenerator <imagen> [nombreDePlantilla]");
			System.exit(1);
		}
		File imageFile = new File(args[0]);
		if (!imageFile.isFile()) {
			System.err.println("No existe el archivo: " + imageFile);
			System.exit(1);
		}
		String name = args.length >= 2 ? args[1] : imageFile.getName().replaceFirst("\\.\\w+$", "");
		BufferedImage image = ImageIO.read(imageFile);
		FormTemplate template = generate(image, name);
		File output = new File(name + ".xtmpl");
		saveToFile(output, template);
		System.out.println("Plantilla generada: " + output.getAbsolutePath());
		System.out.println("Esquinas: " + template.getCorners().size()
				+ " | Grupos: " + template.getGroups().keySet());
		System.exit(0);
	}

	// ------------------------------------------------------------------
	// Detección de burbujas
	// ------------------------------------------------------------------

	private static class Bubble {
		double x;
		double y;
		int row;
		int col;
	}

	private static List<Bubble> detectBubbles(BufferedImage image) {
		final int width = image.getWidth();
		final int height = image.getHeight();
		int[] label = new int[width * height];
		java.util.Arrays.fill(label, -1);
		int[] parent = new int[width * height];
		int count = 0;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int px = image.getRGB(x, y) & 0xffffff;
				int r = (px >> 16) & 0xff;
				int g = (px >> 8) & 0xff;
				int b = px & 0xff;
				boolean black = Math.max(r, Math.max(g, b)) < RING_DETECTION_THRESHOLD;
				if (!black) {
					continue;
				}
				int idx = y * width + x;
				int left = x > 0 ? label[idx - 1] : -1;
				int up = y > 0 ? label[idx - width] : -1;
				if (left < 0 && up < 0) {
					label[idx] = ++count;
					parent[count] = count;
				} else if (left >= 0 && up < 0) {
					label[idx] = root(left, parent);
				} else if (left < 0) {
					label[idx] = root(up, parent);
				} else {
					int rl = root(left, parent);
					int ru = root(up, parent);
					int rootMin = Math.min(rl, ru);
					label[idx] = rootMin;
					parent[rl] = rootMin;
					parent[ru] = rootMin;
				}
			}
		}

		int[] minX = new int[count + 1];
		int[] minY = new int[count + 1];
		int[] maxX = new int[count + 1];
		int[] maxY = new int[count + 1];
		long[] sumX = new long[count + 1];
		long[] sumY = new long[count + 1];
		int[] area = new int[count + 1];
		java.util.Arrays.fill(minX, Integer.MAX_VALUE);
		java.util.Arrays.fill(minY, Integer.MAX_VALUE);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int idx = y * width + x;
				if (label[idx] < 0) {
					continue;
				}
				int r = root(label[idx], parent);
				if (x < minX[r]) {
					minX[r] = x;
				}
				if (x > maxX[r]) {
					maxX[r] = x;
				}
				if (y < minY[r]) {
					minY[r] = y;
				}
				if (y > maxY[r]) {
					maxY[r] = y;
				}
				sumX[r] += x;
				sumY[r] += y;
				area[r]++;
			}
		}

		List<Bubble> bubbles = new ArrayList<>();
		for (int r = 1; r <= count; r++) {
			if (area[r] == 0) {
				continue;
			}
			int w = maxX[r] - minX[r] + 1;
			int h = maxY[r] - minY[r] + 1;
			if (w < MIN_BUBBLE_BBOX || w > MAX_BUBBLE_BBOX || h < MIN_BUBBLE_BBOX || h > MAX_BUBBLE_BBOX
					|| Math.abs(w - h) > MAX_BBOX_ASYMMETRY) {
				continue;
			}
			Bubble bubble = new Bubble();
			bubble.x = (double) sumX[r] / area[r];
			bubble.y = (double) sumY[r] / area[r];
			bubbles.add(bubble);
		}
		return bubbles;
	}

	private static int root(int i, int[] parent) {
		while (parent[i] != i) {
			i = parent[i];
		}
		return i;
	}

	// ------------------------------------------------------------------
	// Agrupación en líneas y bandas
	// ------------------------------------------------------------------

	private static class Band {
		List<Double> cols = new ArrayList<>();
		List<Double> rows = new ArrayList<>();
		List<Bubble> bubbles = new ArrayList<>();
	}

	private static List<Band> splitBands(List<Bubble> bubbles) {
		List<Double> rowLines = cluster(bubbles, false);
		List<Double> colLines = cluster(bubbles, true);
		if (colLines.size() < MIN_BAND_LINES || rowLines.size() < MIN_BAND_LINES) {
			return new ArrayList<>();
		}

		for (Bubble bubble : bubbles) {
			bubble.col = nearest(bubble.x, colLines);
			bubble.row = nearest(bubble.y, rowLines);
		}

		List<Band> bands = new ArrayList<>();
		for (List<Double> rowBand : splitLines(rowLines)) {
			if (rowBand.size() < MIN_BAND_LINES) {
				continue;
			}
			// Columnas con presencia real (al menos 2 burbujas) dentro de esta
			// banda de filas: descarta columnas de la cabecera y decoración.
			List<Integer> colPresent = new ArrayList<>();
			for (int c = 0; c < colLines.size(); c++) {
				int hits = 0;
				for (Bubble bubble : bubbles) {
					if (bubble.col == c && rowBand.contains(rowLines.get(bubble.row))) {
						hits++;
					}
				}
				if (hits >= 2) {
					colPresent.add(c);
				}
			}
			if (colPresent.size() < MIN_BAND_LINES) {
				continue;
			}
			List<Double> colSubset = new ArrayList<>();
			for (int c : colPresent) {
				colSubset.add(colLines.get(c));
			}

			for (List<Double> colBand : splitLines(colSubset)) {
				if (colBand.size() < MIN_BAND_LINES) {
					continue;
				}
				List<Double> rowsWithBubbles = new ArrayList<>();
				for (double rowLine : rowBand) {
					for (Bubble bubble : bubbles) {
						if (bubble.row >= 0 && rowLines.get(bubble.row) == rowLine
								&& colBand.contains(colLines.get(bubble.col))) {
							rowsWithBubbles.add(rowLine);
							break;
						}
					}
				}
				if (rowsWithBubbles.size() < MIN_BAND_LINES) {
					continue;
				}
				Band band = new Band();
				band.cols = colBand;
				band.rows = rowsWithBubbles;
				for (Bubble bubble : bubbles) {
					if (colBand.contains(colLines.get(bubble.col)) && rowsWithBubbles.contains(rowLines.get(bubble.row))) {
						Bubble copy = new Bubble();
						copy.x = bubble.x;
						copy.y = bubble.y;
						copy.col = colBand.indexOf(colLines.get(bubble.col));
						copy.row = rowsWithBubbles.indexOf(rowLines.get(bubble.row));
						band.bubbles.add(copy);
					}
				}
				bands.add(band);
			}
		}
		return bands;
	}

	private static List<List<Double>> splitLines(List<Double> lines) {
		List<List<Double>> bands = new ArrayList<>();
		List<Double> current = new ArrayList<>();
		current.add(lines.get(0));
		double previousGap = 0.0;
		for (int i = 1; i < lines.size(); i++) {
			double gap = lines.get(i) - lines.get(i - 1);
			// Un hueco real separa bloques sí y solo sí es grande en absoluto
			// (>= MIN_BAND_GAP) y además sensiblemente mayor que el paso previo.
			if (gap >= MIN_BAND_GAP && gap >= 1.6 * previousGap) {
				bands.add(current);
				current = new ArrayList<>();
			}
			current.add(lines.get(i));
			previousGap = gap;
		}
		bands.add(current);
		return bands;
	}

	private static List<Double> cluster(List<Bubble> bubbles, boolean columns) {
		double tolerance = columns ? COLUMN_TOLERANCE : ROW_TOLERANCE;
		double[] values = new double[bubbles.size()];
		for (int i = 0; i < bubbles.size(); i++) {
			values[i] = columns ? bubbles.get(i).x : bubbles.get(i).y;
		}
		java.util.Arrays.sort(values);

		// Barrido voraz sobre valores ordenados: nueva línea cuando el valor
		// no encaja en ninguna línea existente dentro de la tolerancia.
		List<Double> means = new ArrayList<>();
		for (double value : values) {
			int nearest = nearest(value, means);
			if (nearest < 0 || Math.abs(means.get(nearest) - value) > tolerance) {
				means.add(value);
			}
		}
		java.util.Collections.sort(means);

		// Una pasada de refinamiento: reasignar cada burbuja a la línea más
		// cercana y recalcular el centro como media de sus miembros.
		int[] count = new int[means.size()];
		double[] sum = new double[means.size()];
		for (Bubble bubble : bubbles) {
			int idx = nearest(columns ? bubble.x : bubble.y, means);
			count[idx]++;
			sum[idx] += columns ? bubble.x : bubble.y;
		}
		List<Double> refined = new ArrayList<>();
		for (int i = 0; i < means.size(); i++) {
			if (count[i] > 0) {
				refined.add(sum[i] / count[i]);
			}
		}
		return refined;
	}

	private static int nearest(double value, List<Double> means) {
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < means.size(); i++) {
			double dist = Math.abs(means.get(i) - value);
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------
	// Construcción de la plantilla a partir de las bandas
	// ------------------------------------------------------------------

	private static void addGroup(FormTemplate template, Band band) {
		FormGroup group = new FormGroup();
		String groupName = "GROUP_" + (template.getGroups().size() + 1);
		boolean byColumns = band.cols.size() > band.rows.size();
		List<Double> questions = byColumns ? band.cols : band.rows;
		List<Double> values = byColumns ? band.rows : band.cols;

		for (int q = 0; q < questions.size(); q++) {
			FormQuestion question = new FormQuestion(String.format("Question %02d", q + 1));
			question.setType(byColumns ? FieldType.QUESTIONS_BY_COLS : FieldType.QUESTIONS_BY_ROWS);
			question.setMultiple(true);
			question.setRejectMultiple(false);
			for (int v = 0; v < values.size(); v++) {
				FormPoint point = cellPoint(band, q, v, byColumns);
				question.setPoint(valueName(v), point);
			}
			group.setField(question.getName(), question);
		}
		template.setGroupsEnabled(true);
		template.addGroup(groupName, group);
	}

	private static String valueName(int index) {
		return index < 26 ? String.valueOf((char) ('A' + index)) : "V" + (index + 1);
	}

	private static FormPoint cellPoint(Band band, int q, int v, boolean byColumns) {
		double col = band.cols.get(byColumns ? q : v);
		double row = band.rows.get(byColumns ? v : q);
		for (Bubble bubble : band.bubbles) {
			if ((byColumns && bubble.col == q && bubble.row == v)
					|| (!byColumns && bubble.col == v && bubble.row == q)) {
				return new FormPoint(bubble.x, bubble.y);
			}
		}
		return new FormPoint(col, row);
	}
}