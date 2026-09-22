package com.albertoborsetta.formscanner.main;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OmrServerTest {

	private static final String IMAGE_RESOURCE = "/samples/test/T00001.jpg";
	private static final String TEMPLATE_RESOURCE = "/samples/test/test_template.xtmpl";

	private HttpServer httpServer;
	private String baseUrl;

	@TempDir
	Path templatesDir;

	@BeforeEach
	void setUp() throws Exception {
		Files.copy(
				OmrServerTest.class.getResourceAsStream(TEMPLATE_RESOURCE),
				templatesDir.resolve("test.xtmpl"));
		httpServer = OmrServer.start(0, templatesDir.toString());
		baseUrl = "http://localhost:" + httpServer.getAddress().getPort();
	}

	@AfterEach
	void tearDown() {
		if (httpServer != null) {
			httpServer.stop(0);
		}
	}

	@Test
	void healthDevuelveOk() throws Exception {
		Response response = request("GET", baseUrl + "/health", null);
		assertTrue(response.status == 200, "status " + response.status);
		assertTrue(response.body.contains("\"status\":\"ok\""), response.body);
	}

	@Test
	void scanDetectaQuestion31ComoC() throws Exception {
		byte[] image = resourceBytes(IMAGE_RESOURCE);
		Response response = request("POST", baseUrl + "/scan?template=test", image);
		assertTrue(response.status == 200, "status " + response.status + " body " + response.body);
		assertTrue(response.body.contains("\"question\":\"Question31\""), response.body);
		assertTrue(response.body.contains("\"response\":\"C\""), response.body);
	}

	@Test
	void scanConPlantillaInexistenteDa404() throws Exception {
		byte[] image = resourceBytes(IMAGE_RESOURCE);
		Response response = request("POST", baseUrl + "/scan?template=noexiste", image);
		assertTrue(response.status == 404, "status " + response.status + " body " + response.body);
	}

	@Test
	void scanSinParametroDa400() throws Exception {
		byte[] image = resourceBytes(IMAGE_RESOURCE);
		Response response = request("POST", baseUrl + "/scan", image);
		assertTrue(response.status == 400, "status " + response.status + " body " + response.body);
	}

	private byte[] resourceBytes(String resource) throws IOException {
		try (InputStream in = OmrServerTest.class.getResourceAsStream(resource)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}

	private Response request(String method, String url, byte[] body) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod(method);
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(30000);
		if (body != null) {
			connection.setDoOutput(true);
			try (OutputStream out = connection.getOutputStream()) {
				out.write(body);
			}
		}
		int status = connection.getResponseCode();
		InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = stream.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		connection.disconnect();
		return new Response(status, new String(out.toByteArray(), "UTF-8"));
	}

	private static final class Response {
		private final int status;
		private final String body;

		private Response(int status, String body) {
			this.status = status;
			this.body = body;
		}
	}
}