package com.chabicht.code_intelligence.apiclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.chabicht.code_intelligence.model.CompletionResult;
import com.chabicht.code_intelligence.model.Prompt;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class OllamaApiClient implements IAiApiClient {

	private final AiApiConnection apiConnection;
	private transient final Gson gson = new Gson();

	public OllamaApiClient(AiApiConnection apiConnection) {
		this.apiConnection = apiConnection;
	}

	@Override
	public List<AiModel> getModels() {
		JsonObject res = performGet(JsonObject.class, "api/tags");
		return res.get("models").getAsJsonArray().asList().stream().map(e -> {
			JsonObject o = e.getAsJsonObject();
			String id = o.get("name").getAsString();
			return new AiModel(apiConnection, id, id);
		}).collect(Collectors.toList());
	}

	public AiApiConnection getApiConnection() {
		return apiConnection;
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement> T performGet(Class<T> clazz, String relPath) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath)).GET().build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			statusCode = response.statusCode();
			responseBody = response.body();
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			throw new RuntimeException(String.format("Error during API request: %s\nStatus code: %d\nResponse: %s",
					apiConnection.getBaseUri() + relPath, statusCode, responseBody), e);
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends JsonElement, U extends JsonElement> T performPost(Class<T> clazz, String relPath,
			U requestBody) {
		int statusCode = -1;
		String responseBody = "(nothing)";
		String requestBodyString = gson.toJson(requestBody);
		try {
			HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(Duration.ofSeconds(5)).followRedirects(Redirect.ALWAYS).build();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(apiConnection.getBaseUri() + "/").resolve(relPath))
					.POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
					.header("Content-Type", "application/json").build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			statusCode = response.statusCode();
			responseBody = response.body();
			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException(
						String.format("API request failed with code %s:\n%s", statusCode, responseBody));
			}
			return (T) JsonParser.parseString(responseBody);
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			throw new RuntimeException(
					String.format("Error during API request:\nURI: %s\nStatus code: %d\nRequest: %s\nResponse: %s",
							apiConnection.getBaseUri() + relPath, statusCode, requestBodyString, responseBody),
					e);
		}
	}

	@Override
	public CompletionResult performCompletion(String modelName, Prompt completionPrompt) {
		JsonObject req = new JsonObject();
		req.addProperty("model", modelName);
		req.addProperty("prompt", completionPrompt.compile());
		JsonObject options = new JsonObject();
		options.addProperty("temperature", completionPrompt.getTemperature());
		options.addProperty("num_ctx", 8192);
		req.add("options", options);
		req.addProperty("stream", false);

		JsonObject res = performPost(JsonObject.class, "api/generate", req);

		return new CompletionResult(res.get("response").getAsString());
	}
}
