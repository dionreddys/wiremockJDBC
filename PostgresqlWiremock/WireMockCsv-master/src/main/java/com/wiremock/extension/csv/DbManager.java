package com.wiremock.extension.csv;

import java.sql.*;
import java.util.Map;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import com.zaxxer.hikari.HikariConfig;


public class DbManager {

	private final String csvPath;
	private Connection dbConnection;
	private CacheManager cacheManager;
	private Cache<String, QueryResults> cache;
	public DbManager(final String csvPath) {
		this.csvPath = csvPath;
	}

	/**
	 *
	 * Koneksi ke db
	 *
	 */
	public void dbConnect() throws WireMockPostgresException {
		try {
			Class.forName("org.postgresql.Driver");
			String jdbcUrl = "jdbc:postgresql://:5432/employee";
			String username = "postgres";
			String password = "root";
			this.dbConnection = DriverManager.getConnection(jdbcUrl, username, password);

			HikariConfig config = new HikariConfig();

			// Required Configuration
			config.setJdbcUrl("jdbc:postgresql://:5432/employee");
			config.setUsername("postgres");
			config.setPassword("root");

			// Optional Configuration
			config.setDriverClassName("org.postgresql.Driver");
			config.setConnectionTestQuery("SELECT 1");
			config.setPoolName("poolWiremock");
			config.setMaximumPoolSize(10);
			config.setMinimumIdle(5);
			config.setConnectionTimeout(30000);
			config.setIdleTimeout(600000);
			config.setMaxLifetime(1800000);
			config.setAutoCommit(false);
			config.setReadOnly(false);
			config.addDataSourceProperty("cachePrepStmts", "true");
			config.addDataSourceProperty("prepStmtCacheSize", "250");
			config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

			// Konfigurasi cache
			this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
			this.cacheManager.init();
			this.cache = this.cacheManager.createCache("myCache",
					CacheConfigurationBuilder.newCacheConfigurationBuilder(
									String.class,
									QueryResults.class,
									ResourcePoolsBuilder.heap(100)) // Ganti 100 dengan ukuran cache yang sesuai
							.build());

		} catch (ClassNotFoundException e) {
			throw new WireMockPostgresException("Kelas driver PostgreSQL tidak ditemukan.", e);
		} catch (SQLException e) {
			throw new WireMockPostgresException("Kesalahan saat membuat koneksi ke database PostgreSQL: " + e.getMessage(), e);
		}
	}

	/**
	 * Method untuk disconnect dari db
	 *
	 */
	public void dbDisconnect() throws WireMockPostgresException {
		try {
			final Statement statement = this.dbConnection.createStatement();
			statement.execute("SHUTDOWN");
			statement.close();
			this.cacheManager.removeCache("myCache");
			this.cacheManager.close();
			this.dbConnection.close();
		} catch (final SQLException e) {
			throw new WireMockPostgresException("Terjadi kesalahan saat memutuskan sambungan dari db : " + e.getMessage(), e);
		}

	}

	/**
	 * Fungsi yang digunakan untuk menjalankan queri SQL yang hasilnya akan dikembalikan.
	 *
	 */
	public QueryResults select(final String querySQL, final Map<String, Map<String, Object>> aliases) throws WireMockPostgresException {
		QueryResults cachedValue = getQueryResultsFromCache(querySQL);
		if (cachedValue == null) {
			try (final Statement stmt = this.dbConnection.createStatement();
				 final ResultSet results = stmt.executeQuery(querySQL)) {
				cachedValue = new QueryResults(results, aliases);
				cache.put(querySQL, cachedValue);
			} catch (final SQLException e) {
				throw new WireMockPostgresException("Kesalahan memilih dari query db " + querySQL + " : " + e.getMessage(), e);
			}
		}
		return cachedValue;
	}

	private QueryResults getQueryResultsFromCache(String querySQL) {
		return cache.get(querySQL); // key is unique
	}

}
