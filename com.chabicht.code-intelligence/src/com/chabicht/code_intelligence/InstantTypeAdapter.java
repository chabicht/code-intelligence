package com.chabicht.code_intelligence;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

	@Override
	public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
		return new JsonPrimitive(FORMATTER.format(src));
	}

	@Override
	public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		return Instant.parse(json.getAsString());
	}
}