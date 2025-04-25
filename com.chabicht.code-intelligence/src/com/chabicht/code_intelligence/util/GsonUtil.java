package com.chabicht.code_intelligence.util;

import java.time.Instant;
import java.util.Optional;

import com.chabicht.code_intelligence.InstantTypeAdapter;
import com.chabicht.code_intelligence.OptionalTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonUtil {
	private GsonUtil() {
		// No Instances
	}

	public static Gson createGson() {
		return new GsonBuilder().registerTypeAdapter(Instant.class, new InstantTypeAdapter())
				.registerTypeAdapter(Optional.class, new OptionalTypeAdapter()).create();
	}
}
