package com.wiremock.extension.csv;

import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import com.github.tomakehurst.wiremock.standalone.WireMockServerRunner;

public class Main {

	private static String filesRoot;

	public static void main(final String[] args) {

		final CommandLineOptions options = new CommandLineOptions(args);
		Main.filesRoot = options.filesRoot().getPath();

		final String[] args2 = new String[args.length + 3];
		System.arraycopy(args, 0, args2, 0, args.length);
		args2[args.length] = "--global-response-templating";
		args2[args.length + 1] = "--extensions";
		args2[args.length + 2] = "com.wiremock.extension.csv.WireMockPostgres";

		WireMockServerRunner.main(args2);
	}

	public static String filesRoot() {
		return Main.filesRoot;
	}

}
