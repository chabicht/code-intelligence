package com.chabicht.code_intelligence;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class OptionalTypeAdapter implements JsonSerializer<Optional<?>>, JsonDeserializer<Optional<?>> {

	@Override
	public JsonElement serialize(Optional<?> src, Type typeOfSrc, JsonSerializationContext context) {
		if (src.isPresent()) {
			// Get the type of the value inside the Optional
			Type valueType = ((ParameterizedType) typeOfSrc).getActualTypeArguments()[0];
			// Delegate serialization to Gson for the actual value
			return context.serialize(src.get(), valueType);
		} else {
			// Serialize empty Optional as JSON null
			return JsonNull.INSTANCE;
		}
	}

	@Override
	public Optional<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		if (json.isJsonNull() || isEmptyObject(json)) {
			// Deserialize JSON null as empty Optional
			return Optional.empty();
		} else {
			// Get the type of the value inside the Optional
			Type valueType = ((ParameterizedType) typeOfT).getActualTypeArguments()[0];
			// Delegate deserialization to Gson for the actual value
			Object value = context.deserialize(json, valueType);
			// Wrap the deserialized value in an Optional
			return Optional.ofNullable(value);
		}
	}

	private boolean isEmptyObject(JsonElement json) {
		return json.isJsonObject() && json.getAsJsonObject().isEmpty();
	}
}
