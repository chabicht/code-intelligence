package com.chabicht.code_intelligence.util;

import java.io.IOException;
import java.util.List;

import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCallBatch.FunctionCallItem;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ChatMessageTypeAdapterFactory implements TypeAdapterFactory {

	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if (!ChatMessage.class.equals(type.getRawType())) {
			return null;
		}

		TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
		TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

		return new TypeAdapter<T>() {
			@Override
			public void write(JsonWriter out, T value) throws IOException {
				delegate.write(out, value);
			}

			@Override
			public T read(JsonReader in) throws IOException {
				JsonElement tree = elementAdapter.read(in);
				T value = delegate.fromJsonTree(tree);
				if (value instanceof ChatMessage message && tree != null && tree.isJsonObject()) {
					normalizeLegacyToolFields(message, tree.getAsJsonObject(), gson);
				}
				return value;
			}
		};
	}

	private void normalizeLegacyToolFields(ChatMessage message, JsonObject json, Gson gson) {
		if (message.getFunctionCallBatch().isPresent() && hasMeaningfulBatchItems(message.getFunctionCallBatch().get())) {
			return;
		}

		FunctionCall legacyCall = deserializeIfPresent(json, "functionCall", FunctionCall.class, gson);
		FunctionResult legacyResult = deserializeIfPresent(json, "functionResult", FunctionResult.class, gson);
		if (legacyCall == null && legacyResult == null) {
			return;
		}

		FunctionCallBatch batch = message.getFunctionCallBatch().orElseGet(FunctionCallBatch::new);
		List<FunctionCallItem> items = batch.getItems();
		if (items.isEmpty()) {
			items.add(new FunctionCallItem());
		}

		FunctionCallItem firstItem = items.get(0);
		if (firstItem == null) {
			firstItem = new FunctionCallItem();
			items.set(0, firstItem);
		}

		if (legacyCall != null) {
			firstItem.setCall(legacyCall);
		}
		if (legacyResult != null) {
			firstItem.setResult(legacyResult);
		}
		message.setFunctionCallBatch(batch);
	}

	private boolean hasMeaningfulBatchItems(FunctionCallBatch batch) {
		for (FunctionCallItem item : batch.getItems()) {
			if (item != null && (item.getCall() != null || item.getResult() != null)) {
				return true;
			}
		}
		return false;
	}

	private <T> T deserializeIfPresent(JsonObject json, String propertyName, Class<T> type, Gson gson) {
		if (json == null || !json.has(propertyName)) {
			return null;
		}

		JsonElement value = json.get(propertyName);
		if (value == null || value.isJsonNull()) {
			return null;
		}
		if (value.isJsonObject() && value.getAsJsonObject().isEmpty()) {
			return null;
		}
		return gson.fromJson(value, type);
	}
}
