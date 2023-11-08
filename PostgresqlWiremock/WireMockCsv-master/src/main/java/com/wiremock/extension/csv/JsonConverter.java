/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.wiremock.extension.csv.QueryResults.QueryResult;

public class JsonConverter {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final ObjectWriter INDENT_MAPPER = JsonConverter.MAPPER.writerWithDefaultPrettyPrinter();

	public JsonConverter() {
	}

	/**
	 * Format json string
	 *
	 * @throws WireMockPostgresException
	 */
	public String formatJson(final String json) throws WireMockPostgresException {
		try {
			final Object obj = JsonConverter.MAPPER.readValue(json, Object.class);
			return this.writeValueAsString(obj);
		} catch (final IOException e) {
			throw new WireMockPostgresException("Kesalahan memformat JSON: " + e.getMessage(), e);
		}
	}

	/**
	 * Konversi objek apa pun ke json.
	 */
	public String convertObjectToJson(final Object object) throws WireMockPostgresException {
		try {
			return this.writeValueAsString(object);
		} catch (final IOException e) {
			throw new WireMockPostgresException("Kesalahan saat mengonversi ke JSON: " + e.getMessage(), e);
		}
	}

	/**
	 * Konversi json apa pun ke peta.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readJsonToMap(final File jsonFile) throws WireMockPostgresException {
		try {
			return JsonConverter.MAPPER.readValue(jsonFile, Map.class);
		} catch (final JsonProcessingException e) {
			throw new WireMockPostgresException("Kesalahan saat mengonversi ke JSON: " + e.getMessage(), e);
		} catch (final IOException e) {
			throw new WireMockPostgresException("Terjadi kesalahan saat menginisialisasi ekstensi CSV.", e);
		}
	}

	/**
	 * Mengonversi {@link QueryResults} ke json
	 */
	public String convertToJson(final QueryResults qr) throws WireMockPostgresException {
		try {
			return this.writeValueAsString(this.convert(qr));
		} catch (final IOException e) {
			throw new WireMockPostgresException("Kesalahan saat mengonversi ke JSON: " + e.getMessage(), e);
		}
	}

	/**
	 * Konversi {@link QueryResults} ke Daftar Peta, ke Peta, atau ke nilai, bergantung pada flag "resultType" dari QueryResults.
	 */
	protected Object convert(final QueryResults qr) throws WireMockPostgresException {
		final Object obj;
		if ("value".equals(qr.getResultType())) {
			if (qr.getLines().isEmpty() || qr.getLines().get(0).getResult().length == 0) {
				obj = null;
			} else {
				obj = qr.getLines().get(0).getResult()[0];
			}
		} else if ("object".equals(qr.getResultType())) {
			if (qr.getLines().isEmpty()) {
				obj = Collections.emptyMap();
			} else {
				obj = this.convertToMap(qr.getLines().get(0));
			}
		} else if ("array".equals(qr.getResultType())) {
			if (qr.getLines().isEmpty()) {
				obj = Collections.emptyList();
			} else {
				obj = qr.getLines().stream().map(line -> line.getResult()[0]).collect(Collectors.toList());
			}
		} else {
			obj = this.convertToMapList(qr);
		}
		return obj;
	}

	/**
	 * Mengonversi {@link QueryResults} ke Daftar Peta
	 */
	public List<Map<String, Object>> convertToMapList(final QueryResults qr) throws WireMockPostgresException {
		final List<Map<String, Object>> list = new ArrayList<>();
		for (final QueryResult line: qr.getLines()) {
			final Map<String, Object> obj = this.convertToMap(line);
			if (!obj.isEmpty()) {
				list.add(obj);
			}
		}
		return list;
	}

	/**
	 * Mengonversi {@link QueryResult} ke Peta
	 */
	public Map<String, Object> convertToMap(final QueryResult line) throws WireMockPostgresException {
		final Map<String, Object> obj = new LinkedHashMap<>();
		for (int i = 0; i < line.getColumns().length; i++) {
			if (! line.isMasked(line.getColumns()[i])) {
				this.addFieldToObject(obj, line.getColumns()[i].split("__"), line.getResult()[i]);
			}
		}
		if (line.getSubResults() != null) {
			for (final Map.Entry<String, QueryResults> subResult: line.getSubResults().entrySet()) {
				if (obj.containsKey(subResult.getKey())) {
					throw new WireMockPostgresException(
							"Dobloon di lapangan'" + subResult.getKey() + "' saat mengonversi ke JSON.");
				}
				this.addFieldToObject(obj, subResult.getKey().split("__"), this.convert(subResult.getValue()));
			}
		}
		if (line.getSubResultsLists() != null) {
			for (final Map.Entry<String, List<QueryResults>> subResult : line.getSubResultsLists().entrySet()) {
				if (obj.containsKey(subResult.getKey())) {
					throw new WireMockPostgresException(
							"Dobloon di lapangan '" + subResult.getKey() + "' saat mengonversi ke JSON.");
				}
				final ArrayList<Object> convSubResult = new ArrayList<>();
				for (final QueryResults qr: subResult.getValue()) {
					final Object convertedQr = this.convert(qr);
					if (convertedQr != null) {
						convSubResult.add(convertedQr);
					}
				}
				this.addFieldToObject(obj, subResult.getKey().split("__"), convSubResult);
			}
		}
		return obj;
	}

	private String writeValueAsString(final Object obj) throws IOException {
		//final ByteArrayOutputStream out = new ByteArrayOutputStream();
		//JsonConverter.INDENT_MAPPER.getFactory().createGenerator(out, JsonEncoding.UTF8).writeObject(obj);
		//return new String(out.toByteArray(), Charset.forName("UTF8"));
		return JsonConverter.INDENT_MAPPER.writeValueAsString(obj);
	}

	/**
	 * Menambahkan nilai di Peta sesuai namanya, sudah dipisah menurut "__".
	 */
	@SuppressWarnings("unchecked")
	private void addFieldToObject(final Map<String, Object> obj, final String[] split, final Object object)
			throws WireMockPostgresException {
		if (split.length == 1) {
			if (obj.containsKey(split[0])) {
				throw new WireMockPostgresException(
						"Dobloon di lapangan '" + split[0] + "' saat mengonversi ke JSON.");
			}
			if (object != null) {
				obj.put(split[0], object);
			}
		} else {
			// Sous objet
			Object sousObj = obj.get(split[0]);
			if (sousObj == null) {
				sousObj = new LinkedHashMap<>();
				obj.put(split[0], sousObj);
			} else if (! (sousObj instanceof Map)) {
				throw new WireMockPostgresException(
						"Dobloon di lapangan '" + split[0] + "' saat mengonversi ke JSON.");
			}
			final String[] sousSplit = new String[split.length - 1];
			System.arraycopy(split, 1, sousSplit, 0, split.length - 1);
			this.addFieldToObject((Map<String, Object>) sousObj, sousSplit, object);
		}
	}
}
