package com.chabicht.code_intelligence.apiclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.Prompt;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Implementation for OpenAI or compatible API using the chat completion
 * endpoint.
 */
public class OpenAiApiClient implements IAiApiClient {

	private final AiApiConnection apiConnection;
	private transient final Gson gson = new Gson();

	public OpenAiApiClient(AiApiConnection apiConnection) {
		this.apiConnection = apiConnection;
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "/models");
		return res.get("data").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("id").getAsString();
			return new AiModel(apiConnection, id, id);
		}).collect(Collectors.toList());
	}

	public AiApiConnection getApiConnection() {
		return apiConnection;
	}

	@SuppressWarnings("unchecked")
	<T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + relPath)).GET();
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
			}
			HttpRequest request = requestBuilder.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			statusCode = response.statusCode();
			responseBody = response.body();
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			Activator.logError(String.format("""
					Error during API request:
					URI: %s
					Method: GET
					Status code: %d
					Response:
					%s
					""", apiConnection.getBaseUri() + relPath, statusCode, responseBody), e);
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	<T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath, U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = "(nothing)";
		try {
			requestBodyString = gson.toJson(requestBody);
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString));
			requestBuilder.header("Content-Type", "application/json");
			if (StringUtils.isNotBlank(apiConnection.getApiKey())) {
				requestBuilder = requestBuilder.header("Authorization", "Bearer " + apiConnection.getApiKey());
			}
			HttpRequest request = requestBuilder.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			statusCode = response.statusCode();
			responseBody = response.body();

			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException(
						String.format("API request failed with code %s:\n%s", statusCode, responseBody));
			}
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			Activator.logError(String.format("""
					Error during API request:
					URI: %s
					Method: POST
					Status code: %d
					Request:
					%s
					Response:
					%s
					""", apiConnection.getBaseUri() + relPath, statusCode, requestBodyString, responseBody), e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public CompletionResult performCompletion(String modelName, Prompt completionPrompt) {
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("temperature", completionPrompt.getTemperature());

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", completionPrompt.compile());
		messages.add(userMessage);

		req.add("messages", messages);

		JsonObject res = performPost(JsonObject.class, "/chat/completions", req);
		return new CompletionResult(res.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message")
				.getAsJsonObject().get("content").getAsString());
	}
}
