package com.chabicht.code_intelligence.chat.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.jface.preference.IPreferenceStore;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.util.GsonUtil;
import com.chabicht.code_intelligence.util.Log;
import com.chabicht.codeintelligence.preferences.PreferenceConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ToolDefinitions {
	private static final JsonObject NO_TOOLS = GsonUtil.createGson()
			.fromJson("{}", JsonObject.class);

	private static ToolDefinitions INSTANCE;
	private final String TOOL_DEFINITION_GEMINI;

	private final List<Tool> tools = new ArrayList<>();

	public static synchronized ToolDefinitions getInstance() {
		if (INSTANCE == null) {
			synchronized (ToolDefinitions.class) {
				INSTANCE = new ToolDefinitions();
			}
		}

		return INSTANCE;
	}

	private ToolDefinitions() {
		String toolDefinition;
		try (InputStream is = ToolDefinitions.class.getResourceAsStream("tool_definitions.json")) {
			toolDefinition = IOUtils.toString(is, StandardCharsets.UTF_8);
		} catch (IOException e) {
			Log.logError("Could not load tool definitions", e);
			toolDefinition = "{}";
		}
		TOOL_DEFINITION_GEMINI = toolDefinition;

		JsonObject allTools = GsonUtil.createGson().fromJson(TOOL_DEFINITION_GEMINI, JsonObject.class);
		JsonArray jsonArray = allTools.get("tools").getAsJsonArray().get(0).getAsJsonObject()
				.get("functionDeclarations").getAsJsonArray();
		for (JsonElement el : jsonArray) {
			JsonObject o = el.getAsJsonObject();
			tools.add(new Tool(o.get("name").getAsString(), o.get("description").getAsString(), true));
		}
	}

	public JsonObject getToolDefinitionsGemini() {
		return getEnabledTools();
	}

	public JsonObject getToolDefinitionsOllama() {
		return toOpenAiToolFormat();
	}

	public JsonObject getToolDefinitionsOpenAi() {
		return toOpenAiToolFormat();
	}

	public JsonObject getToolDefinitionsOpenAiLegacy() {
		return toOpenAiFunctionFormat();
	}

	public JsonObject getToolDefinitionsXAi() {
		return toOpenAiToolFormat();
	}

	public JsonObject getToolDefinitionsAnthropic() {
		com.google.gson.Gson gson = GsonUtil.createGson();
		JsonObject toolDefinitionGemini = getEnabledTools();

		JsonArray geminiToolsArray = toolDefinitionGemini.getAsJsonArray("tools");
		if (geminiToolsArray == null || geminiToolsArray.isEmpty()) {
			JsonObject result = new JsonObject();
			result.add("tools", new JsonArray());
			return result;
		}

		JsonObject geminiToolContainer = geminiToolsArray.get(0).getAsJsonObject();
		JsonArray functionDeclarations = geminiToolContainer.getAsJsonArray("functionDeclarations");

		JsonArray anthropicToolsArray = new JsonArray();
		if (functionDeclarations != null) {
			for (JsonElement funcDeclElement : functionDeclarations) {
				JsonObject geminiFuncDecl = funcDeclElement.getAsJsonObject();
				JsonObject anthropicTool = new JsonObject();
				anthropicTool.addProperty("name", geminiFuncDecl.get("name").getAsString());
				anthropicTool.addProperty("description", geminiFuncDecl.get("description").getAsString());
				anthropicTool.add("input_schema", geminiFuncDecl.getAsJsonObject("parameters").deepCopy()); // Renaming
																											// 'parameters'
																											// to
																											// 'input_schema'
				anthropicToolsArray.add(anthropicTool);
			}
		}

		JsonObject finalAnthropicJson = new JsonObject();
		finalAnthropicJson.add("tools", anthropicToolsArray);
		return finalAnthropicJson;
	}

	private JsonObject toOpenAiToolFormat() {
		com.google.gson.Gson gson = GsonUtil.createGson();
		JsonObject toolDefinitionGemini = getEnabledTools();

		JsonArray geminiToolsArray = toolDefinitionGemini.getAsJsonArray("tools");
		if (geminiToolsArray == null || geminiToolsArray.isEmpty()) {
			JsonObject result = new JsonObject();
			result.add("tools", new JsonArray()); // OpenAI expects a "tools" array
			return result;
		}

		JsonObject geminiToolContainer = geminiToolsArray.get(0).getAsJsonObject();
		JsonArray functionDeclarations = geminiToolContainer.getAsJsonArray("functionDeclarations");

		JsonArray openAiToolsArray = new JsonArray();
		if (functionDeclarations != null) {
			for (JsonElement funcDeclElement : functionDeclarations) {
				JsonObject geminiFuncDecl = funcDeclElement.getAsJsonObject();
				JsonObject openAiFunctionDetails = new JsonObject();
				openAiFunctionDetails.addProperty("name", geminiFuncDecl.get("name").getAsString());
				openAiFunctionDetails.addProperty("description", geminiFuncDecl.get("description").getAsString());

				JsonObject parameters = geminiFuncDecl.getAsJsonObject("parameters").deepCopy();

				parameters = patchOpenAiRequiredFields(parameters);
				parameters.addProperty("additionalProperties", false);
				openAiFunctionDetails.add("parameters", parameters);
				openAiFunctionDetails.addProperty("strict", true);

				JsonObject openAiTool = new JsonObject();
				openAiTool.addProperty("type", "function");
				openAiTool.add("function", openAiFunctionDetails);
				openAiToolsArray.add(openAiTool);
			}
		}

		JsonObject finalOpenAiJson = new JsonObject();
		finalOpenAiJson.add("tools", openAiToolsArray);
		return finalOpenAiJson;
	}

	public List<Tool> getTools() {
		return tools;
	}

	// OpenAI tools require all parameters be present in the required array.
	private JsonObject patchOpenAiRequiredFields(JsonObject parameters) {
		// Remove existing 'required' field if present
		if (parameters.has("required")) {
			parameters.remove("required");
		}

		// Get the 'properties' object
		JsonObject properties = parameters.getAsJsonObject("properties");

		// Create a new 'required' array and populate it with all property names
		JsonArray requiredArray = new JsonArray();
		if (properties != null) {
			for (String propertyName : properties.keySet()) {
				requiredArray.add(propertyName);
			}
		}
		parameters.add("required", requiredArray);
		return parameters;
	}

	private JsonObject getEnabledTools() {
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		if (prefs.getBoolean(PreferenceConstants.CHAT_TOOLS_ENABLED)) {
			JsonObject toolsJson = GsonUtil.createGson().fromJson(TOOL_DEFINITION_GEMINI, JsonObject.class);
			JsonObject wrapperObj = toolsJson.get("tools").getAsJsonArray().get(0).getAsJsonObject();
			JsonArray toolsArray = wrapperObj.get("functionDeclarations").getAsJsonArray();
			JsonArray res = new JsonArray();
			for (JsonElement el : toolsArray) {
				String toolName = el.getAsJsonObject().get("name").getAsString();
				if (prefs.getBoolean(String.format("%s.%s.%s", PreferenceConstants.CHAT_TOOL_ENABLED_PREFIX, toolName,
						PreferenceConstants.CHAT_TOOL_ENABLED_SUFFIX))) {
					res.add(el);
				}
			}
			wrapperObj.remove("functionDeclarations");
			wrapperObj.add("functionDeclarations", res);
			return toolsJson;
		} else {
			return NO_TOOLS;
		}
	}

	private JsonObject toOpenAiFunctionFormat() {
		com.google.gson.Gson gson = GsonUtil.createGson();
		JsonObject toolDefinitionGemini = getEnabledTools();

		JsonArray geminiToolsArray = toolDefinitionGemini.getAsJsonArray("tools");
		if (geminiToolsArray == null || geminiToolsArray.isEmpty()) {
		    JsonObject result = new JsonObject();
		    result.add("functions", new JsonArray());
			return result;
		}

		JsonObject geminiToolContainer = geminiToolsArray.get(0).getAsJsonObject();
		JsonArray functionDeclarations = geminiToolContainer.getAsJsonArray("functionDeclarations");

		JsonArray openAiFunctionsArray = new JsonArray();
		if (functionDeclarations != null) {
		    for (JsonElement funcDeclElement : functionDeclarations) {
			    JsonObject geminiFuncDecl = funcDeclElement.getAsJsonObject();
			    JsonObject openAiFunction = new JsonObject();
			    openAiFunction.addProperty("name", geminiFuncDecl.get("name").getAsString());
			    openAiFunction.addProperty("description", geminiFuncDecl.get("description").getAsString());

			    JsonObject parameters = geminiFuncDecl.getAsJsonObject("parameters").deepCopy();
			    parameters.addProperty("additionalProperties", false);
			    openAiFunction.add("parameters", parameters);
			    openAiFunctionsArray.add(openAiFunction);
		    }
		}

		JsonObject finalOpenAiJson = new JsonObject();
		finalOpenAiJson.add("functions", openAiFunctionsArray);
		return finalOpenAiJson;
	}

	public static class Tool {
		private String name;
		private String description;
		private boolean enabled;

		public Tool(String name, String description, boolean enabled) {
			super();
			this.name = name;
			this.description = description;
			this.enabled = enabled;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
