/**
 * Copyright 2017.
 */

package com.wiremock.extension.csv;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.common.ListOrSingle;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RequestTemplateModel;
import com.github.tomakehurst.wiremock.http.Request;
import com.wiremock.extension.csv.QueryResults.QueryResult;

/**
 * Memungkinkan untuk mengelola konfigurasi dan parameter pada eksekusi permintaan.<br>
 * Kelas ini memungkinkan penumpukan konfigurasi global, parameter HTTP, parameter khusus, dan satu atau beberapa hasil queri.
 */
public class ConfigHandler {
	private final Map<String, Object> globalConfig;
	private final DbManager manager;

	public ConfigHandler(final DbManager manager, final JsonConverter jsonConverter) throws WireMockPostgresException {
		final File configFile = new File(WireMockPostgresUtils.getFilesRoot() + File.separatorChar + "csv" + File.separatorChar + "WireMockCsv.json.conf");
		if (configFile.exists()) {
			this.globalConfig = Collections.unmodifiableMap(jsonConverter.readJsonToMap(configFile));
		} else {
			this.globalConfig = Collections.emptyMap();
		}
		this.manager = manager;
	}

	public Map<String, Object> getGlobalConfig() {
		return this.globalConfig;
	}

	public RequestConfigHandler getRequestConfigHandler(final Request request, final Parameters parameters) throws WireMockPostgresException {
		return new RootConfigHandler(request, parameters);
	}

	/**
	 *
	 * Antarmuka mewakili konfigurasi runtime dari tingkat kueri.
	 *
	 */
	public interface RequestConfigHandler {
		Request getRequest();
		Parameters getTransformerParameters();
		RequestConfigHandler addQueryResult(QueryResult qr, Map<String, Map<String, Object>> customParametersConfig)
				throws WireMockPostgresException;
		QueryResult getNearestQueryResult();
		Object getParamValue(String paramName);
		List<?> getParamValues(String paramName);
	}

	private abstract class AbstractConfigHandler implements RequestConfigHandler {
		private Map<String, List<?>> customParameters;


		protected void initCustomParameters(final Map<String, Map<String, Object>> customParametersConfig) throws WireMockPostgresException {
			if (customParametersConfig != null) {
				this.customParameters = new HashMap<>();
				for (final Map.Entry<String, Map<String, Object>> e: customParametersConfig.entrySet()) {
					this.initCustomParameter(e);
				}
			}
		}

		private void initCustomParameter(final Map.Entry<String, Map<String, Object>> e) throws WireMockPostgresException {
			final String action = (String) e.getValue().get("action");
			if ("split".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final Object regexp = e.getValue().get("regexp");
				final Object srcValue = this.getParamValue(src.toString());
				if (srcValue != null) {
					this.customParameters.put(e.getKey(), Arrays.asList(Pattern.compile(regexp.toString()).split(srcValue.toString())));
				} else {
					this.customParameters.remove(e.getKey());
				}
			} else if ("replace".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final Object regexp = e.getValue().get("regexp");
				final Object replacement = e.getValue().getOrDefault("replacement", "");
				final Object srcValue = this.getParamValue(src.toString());
				if (srcValue != null) {
					this.customParameters.put(e.getKey(),
							Arrays.asList(Pattern.compile(regexp.toString()).matcher(srcValue.toString()).replaceAll(replacement.toString())));
				} else {
					this.customParameters.remove(e.getKey());
				}
			} else if ("concatenate".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final Object prefix = e.getValue().getOrDefault("prefix", "");
				final Object suffix = e.getValue().getOrDefault("suffix", "");
				final Object separator = e.getValue().getOrDefault("separator", "");
				final List<?> srcValues = this.getParamValues(src.toString());
				final StringBuilder sb = new StringBuilder();
				sb.append(prefix);
				if (srcValues != null && !srcValues.isEmpty()) {
					sb.append(srcValues.get(0));
					for (int i = 1 ; i < srcValues.size() ; ++i) {
						sb.append(separator).append(srcValues.get(i));
					}
				}
				sb.append(suffix);
				this.customParameters.put(e.getKey(), Arrays.asList(sb.toString()));
			} else if ("fromQuery".equals(action)) {
				Object query = e.getValue().get("query");
				query = WireMockPostgresUtils.replaceQueryVariables(query.toString(), this);
				final QueryResults result = ConfigHandler.this.manager.select(query.toString(), null);
				if (result.getColumns().length > 1 && result.getLines().size() > 1) {
					throw new WireMockPostgresException(
							"Can't handle queries with multiple columns and lines in fromQuery custom parameter");
				}
				if (result.getLines().size() > 1) {
					// Daftar semua baris, hanya kolom pertama.
					this.customParameters.put(e.getKey(),
							result.getLines().stream().map(qr -> qr.getResult()[0]).collect(Collectors.toList()));
				} else {
					if (result.getLines().isEmpty()) {
						// Tidak ada hasil.
						this.customParameters.remove(e.getKey());
					} else {
						// Daftar semua kolom
						this.customParameters.put(e.getKey(), Arrays.asList(result.getLines().get(0).getResult()));
					}
				}
			} else if ("escapeSql".equals(action)) {
				final Object src = e.getValue().get("sourceParam");
				final List<?> srcValue = this.getParamValues(src.toString());
				if (srcValue != null) {
					this.customParameters.put(e.getKey(),
							srcValue.stream().map(o -> o == null ? null : o.toString().replaceAll("'", "''")).collect(Collectors.toList()));
				} else {
					this.customParameters.remove(e.getKey());
				}
			} else {
				throw new WireMockPostgresException("Unknown action: " + action);
			}
		}

		protected Map<String, List<?>> getCustomParameters() {
			return this.customParameters;
		}
	}

	/**
	 * Kelas yang digunakan untuk menyimpan konfigurasi tingkat pertama.
	 */
	private class RootConfigHandler extends AbstractConfigHandler {
		private final Request request;
		private final Map<String, ListOrSingle<String>> requestParams;
		private final Parameters transformerParameters;

		public RootConfigHandler(final Request request, final Parameters transformerParameters) throws WireMockPostgresException {
			this.request = request;
			this.requestParams = RequestTemplateModel.from(request).getQuery();
			this.transformerParameters = transformerParameters;
			@SuppressWarnings("unchecked")
			final Map<String, Map<String, Object>> customParametersConfig =
			(Map<String, Map<String, Object>>) this.transformerParameters.get("customParameters");
			this.initCustomParameters(customParametersConfig);
		}

		@Override
		public Request getRequest() {
			return this.request;
		}

		@Override
		public Parameters getTransformerParameters() {
			return this.transformerParameters;
		}

		@Override
		public RequestConfigHandler addQueryResult(final QueryResult qr, final Map<String, Map<String, Object>> customParametersConfig)
				throws WireMockPostgresException {
			return new QueryResultConfigHandler(this, qr, customParametersConfig);
		}

		@Override
		public QueryResult getNearestQueryResult() {
			return null;
		}

		@Override
		public Object getParamValue(final String paramName) {
			List<?> value;
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				value = this.getCustomParameters().get(paramName);
			} else {
				value = this.requestParams.get(paramName);
			}
			return value == null || value.isEmpty() ? null : value.get(0);
		}

		@Override
		public List<?> getParamValues(final String paramName) {
			List<?> value;
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				value = this.getCustomParameters().get(paramName);
			} else {
				value = this.requestParams.get(paramName);
			}
			return value;
		}
	}

	/**
	 *
	 * Kelas digunakan untuk menyimpan tingkat konfigurasi berikutnya.
	 *
	 */
	private class QueryResultConfigHandler extends AbstractConfigHandler {
		private final RequestConfigHandler parent;
		private final QueryResult queryResult;

		public QueryResultConfigHandler(final RequestConfigHandler parent, final QueryResult queryResult,
				final Map<String, Map<String, Object>> customParametersConfig) throws WireMockPostgresException {
			super();
			this.parent = parent;
			this.queryResult = queryResult;
			this.initCustomParameters(customParametersConfig);
		}

		@Override
		public Request getRequest() {
			return this.parent.getRequest();
		}

		@Override
		public Parameters getTransformerParameters() {
			return this.parent.getTransformerParameters();
		}

		@Override
		public RequestConfigHandler addQueryResult(final QueryResult qr, final Map<String, Map<String, Object>> customParametersConfig)
				throws WireMockPostgresException {
			return new QueryResultConfigHandler(this, qr, customParametersConfig);
		}

		@Override
		public QueryResult getNearestQueryResult() {
			return this.queryResult == null ? this.parent.getNearestQueryResult() : this.queryResult;
		}

		@Override
		public Object getParamValue(final String paramName) {
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				List<?> paramValues = this.getCustomParameters().get(paramName);
				return paramValues.isEmpty() ? null : paramValues.get(0);
			} else if (this.queryResult != null) {
				for (int idx = 0 ; idx < this.queryResult.getColumns().length ; ++idx) {
					if (this.queryResult.getColumns()[idx].equals(paramName)) {
						return this.queryResult.getResult()[idx];
					}
				}
			}
			return this.parent.getParamValue(paramName);
		}

		@Override
		public List<?> getParamValues(final String paramName) {
			if (this.getCustomParameters() != null && this.getCustomParameters().containsKey(paramName)) {
				return this.getCustomParameters().get(paramName);
			} else if (this.queryResult != null) {
				for (int idx = 0 ; idx < this.queryResult.getColumns().length ; ++idx) {
					if (this.queryResult.getColumns()[idx].equals(paramName)) {
						final List<Object> res = new ArrayList<>(1);
						res.add(this.queryResult.getResult()[idx]);
						return res;
					}
				}
			}
			return this.parent.getParamValues(paramName);
		}
	}
}
