package com.albertoborsetta.formscanner.main;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.albertoborsetta.formscanner.api.FormGroup;
import com.albertoborsetta.formscanner.api.FormQuestion;
import com.albertoborsetta.formscanner.api.FormTemplate;
import com.albertoborsetta.formscanner.api.TemplateAutoGenerator;
import com.albertoborsetta.formscanner.api.commons.Constants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class OmrServer {

	private final File templatesDir;

	public OmrServer(File templatesDir) {
		this.templatesDir = templatesDir;
	}

	public static void main(String[] args) throws IOException {
		int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
		String dir = System.getProperty("formscanner.templates.dir", "./templates");
		try {
			start(port, dir);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	public static HttpServer start(int port, String templatesDir) throws IOException {
		File dir = new File(templatesDir);
		if (!dir.exists() && !dir.mkdirs()) {
			throw new IOException("No se pudo crear el directorio de plantillas: " + dir.getAbsolutePath());
		}

		OmrServer server = new OmrServer(dir);
		HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
		httpServer.createContext("/health", server::health);
		httpServer.createContext("/template", server::createTemplate);
		httpServer.createContext("/scan", server::scan);
		httpServer.setExecutor(Executors.newCachedThreadPool());
		httpServer.start();
		System.out.println("OMR server escuchando en http://0.0.0.0:" + port);
		System.out.println("Plantillas en: " + dir.getAbsolutePath());
		return httpServer;
	}

	private void health(HttpExchange exchange) throws IOException {
		respond(exchange, 200, "{\"status\":\"ok\"}");
	}

	private void createTemplate(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			respond(exchange, 405, "{\"error\":\"use POST\"}");
			return;
		}
		String name = parameter(exchange, "name");
		if (name == null || !validName(name)) {
			respond(exchange, 400, "{\"error\":\"parametro name invalido\"}");
			return;
		}
		try {
			BufferedImage image = readImage(exchange);
			FormTemplate template = TemplateAutoGenerator.generate(image, name);
			TemplateAutoGenerator.saveToFile(new File(templatesDir, name + ".xtmpl"), template);
			respond(exchange, 200, "{\"name\":\"" + name + "\"}");
		} catch (Exception e) {
			respond(exchange, 500, "{\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}");
		}
	}

	private void scan(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			respond(exchange, 405, "{\"error\":\"use POST\"}");
			return;
		}
		String name = parameter(exchange, "template");
		if (name == null || !validName(name)) {
			respond(exchange, 400, "{\"error\":\"parametro template invalido\"}");
			return;
		}
		File templateFile = new File(templatesDir, name + ".xtmpl");
		if (!templateFile.isFile()) {
			respond(exchange, 404, "{\"error\":\"plantilla no encontrada: " + name + "\"}");
			return;
		}
		try {
			BufferedImage image = readImage(exchange);
			FormTemplate template = new FormTemplate(templateFile);
			FormTemplate filledForm = new FormTemplate("form", template);
			Integer threshold = template.getThreshold();
			Integer density = template.getDensity();
			Integer size = template.getSize();
			filledForm.findCorners(image, threshold, density, template.getCornerType(), template.getCrop());
			filledForm.findPoints(image, threshold, density, size);
			filledForm.findAreas(image);
			respond(exchange, 200, toJson(filledForm));
		} catch (Exception e) {
			respond(exchange, 500, "{\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}");
		}
	}

	private String toJson(FormTemplate filledForm) {
		StringBuilder values = new StringBuilder("[");
		List<String> groups = new ArrayList<>(filledForm.getGroups().keySet());
		Collections.sort(groups);
		boolean first = true;
		for (String groupName : groups) {
			FormGroup group = filledForm.getGroups().get(groupName);
			List<String> fields = new ArrayList<>(group.getFields().keySet());
			Collections.sort(fields);
			for (String fieldName : fields) {
				if (!first) {
					values.append(",");
				}
				first = false;
				String key = groupName.equals(Constants.EMPTY_GROUP_NAME) ? fieldName : groupName + "." + fieldName;
				FormQuestion question = group.getFields().get(fieldName);
				values.append("{\"question\":\"").append(escape(key)).append("\",\"response\":\"")
						.append(escape(question.getValues())).append("\"}");
			}
		}
		values.append("]");
		return "{\"template\":\"" + escape(filledForm.getName()) + "\",\"results\":" + values + "}";
	}

	private BufferedImage readImage(HttpExchange exchange) throws IOException {
		byte[] body = readAll(exchange.getRequestBody());
		return ImageIO.read(new java.io.ByteArrayInputStream(body));
	}

	private byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private String parameter(HttpExchange exchange, String name) {
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null) {
			return null;
		}
		for (String pair : query.split("&")) {
			String[] parts = pair.split("=", 2);
			if (parts.length == 2 && parts[0].equals(name)) {
				return parts[1];
			}
		}
		return null;
	}

	private boolean validName(String name) {
		return name.matches("[A-Za-z0-9_-]+");
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes("UTF-8");
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(status, bytes.length);
		OutputStream out = exchange.getResponseBody();
		out.write(bytes);
		out.close();
	}
}