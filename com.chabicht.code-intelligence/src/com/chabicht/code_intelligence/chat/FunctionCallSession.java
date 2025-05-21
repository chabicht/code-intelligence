package com.chabicht.code_intelligence.chat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ApplyChangeTool;
import com.chabicht.code_intelligence.chat.tools.ApplyPatchTool; // Added import
import com.chabicht.code_intelligence.chat.tools.ResourceAccess;
import com.chabicht.code_intelligence.chat.tools.TextSearchTool;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.util.GsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken; // Import needed for Map type

public class FunctionCallSession {

	private final ResourceAccess resourceAccess = new ResourceAccess();
	private final ApplyChangeTool applyChangeTool = new ApplyChangeTool(resourceAccess);
	private final ApplyPatchTool applyPatchTool = new ApplyPatchTool(resourceAccess); // Added tool instance
	private final TextSearchTool searchTool = new TextSearchTool(resourceAccess); // Added SearchTool instance
	private final Gson gson = GsonUtil.createGson();

	public FunctionCallSession() {
		// Initialization if needed
	}

	/**
	 * Handles an incoming function call from the AI model. Routes the call to the
	 * appropriate tool based on the function name.
	 * 
	 * @param message
	 *
	 * @param functionName     The name of the function to call (e.g.,
	 *                         "apply_change").
	 * @param functionArgsJson A JSON string representing the arguments for the
	 *                         function.
	 * @return
	 */
	public void handleFunctionCall(ChatMessage message) {
		Optional<FunctionCall> callOpt = message.getFunctionCall();
		if (callOpt.isPresent()) {
			FunctionCall call = callOpt.get();
			String functionName = call.getFunctionName();
			String argsJson = call.getArgsJson();

			FunctionResult result = new FunctionResult(call.getId(), functionName);

			switch (functionName) {
			case "apply_change":
				handleApplyChange(call, result, argsJson);
				break;
			case "apply_patch": // Added case for apply_patch
				handleApplyPatch(call, result, argsJson);
				break;
			case "perform_text_search":
				handlePerformTextSearch(call, result, argsJson);
				break;
			default:
				Activator.logError("Unsupported function call: " + functionName);
				break;
			}

			message.setFunctionResult(result);
		}
	}

	/**
	 * Specifically handles the "apply_change" function call. Parses arguments and
	 * adds the change to the ApplyChangeTool queue.
	 *
	 * @param call             The FunctionCall object
	 * @param result           The FunctionResult object to populate
	 * @param functionArgsJson JSON arguments for apply_change.
	 */
	private void handleApplyChange(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			// Define the type for Gson parsing: Map<String, String>
			java.lang.reflect.Type type = new TypeToken<Map<String, String>>() {
			}.getType();
			Map<String, String> args = gson.fromJson(functionArgsJson, type);

			// Extract arguments using the names defined in the function declaration
			String fileName = args.get("file_name");
			String location = args.get("location_in_file");
			String originalText = args.get("original_text");
			String replacementText = args.get("replacement_text");

			// Basic validation
			if (fileName == null || location == null || originalText == null || replacementText == null) {
				String errorMsg = "Missing required argument for apply_change. Args: " + functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);

				// Add JSON result for error
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			// Apply the change and get the result with the diff preview
			ApplyChangeTool.ApplyChangeResult changeResult = applyChangeTool.addChange(fileName, location, originalText,
					replacementText);

			// Store it in the result object so it can be displayed to the user
			// The 'true' parameter marks this as markdown content for proper rendering
			result.addPrettyResult("preview", "```diff\n" + changeResult.getDiffPreview() + "\n```", true);

			// Populate pretty params for this function call
			call.addPrettyParam("file_name", fileName, false);
			call.addPrettyParam("location_in_file", location, false);
			call.addPrettyParam("original_text", originalText, true); // Using markdown for code
			call.addPrettyParam("replacement_text", replacementText, true); // Using markdown for code

			// Create JSON result object
			JsonObject jsonResult = new JsonObject();

			// Set result based on validation outcome
			if (changeResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", changeResult.getMessage(), false);

				jsonResult.addProperty("status", "Success");
				jsonResult.addProperty("message", changeResult.getMessage());
			} else {
				result.addPrettyResult("status", "Error", false);
				result.addPrettyResult("message", changeResult.getMessage(), false);

				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", changeResult.getMessage());
			}

			// Set the JSON result
			result.setResultJson(gson.toJson(jsonResult));

		} catch (JsonSyntaxException e) {
			String errorMsg = "Failed to parse JSON arguments for apply_change: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);

			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("status", "Error");
			jsonResult.addProperty("message", errorMsg);
			result.setResultJson(gson.toJson(jsonResult));
		} catch (Exception e) { // Catch unexpected errors during handling
			String errorMsg = "Error processing apply_change function call: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);

			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("status", "Error");
			jsonResult.addProperty("message", errorMsg);
			result.setResultJson(gson.toJson(jsonResult));
		}
	}

	/**
	 * Specifically handles the "apply_patch" function call. Parses arguments and
	 * adds the changes from the patch to the ApplyPatchTool queue.
	 *
	 * @param call             The FunctionCall object
	 * @param result           The FunctionResult object to populate
	 * @param functionArgsJson JSON arguments for apply_patch. Expected: {"file_name": "...", "patch_content": "..."}
	 */
	private void handleApplyPatch(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			java.lang.reflect.Type type = new TypeToken<Map<String, String>>() {}.getType();
			Map<String, String> args = gson.fromJson(functionArgsJson, type);

			String fileName = args.get("file_name");
			String patchContent = args.get("patch_content");

			if (fileName == null || patchContent == null) {
				String errorMsg = "Missing required argument for apply_patch. Expected file_name and patch_content. Args: " + functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);

				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			ApplyPatchTool.ApplyPatchResult patchResult = applyPatchTool.addChangesFromPatch(fileName, patchContent);

			// Use the input patch content directly as the preview
			if (patchResult.getInputPatchPreview() != null) {
			    result.addPrettyResult("preview", "```diff\n" + patchResult.getInputPatchPreview() + "\n```", true);
			}

			call.addPrettyParam("file_name", fileName, false);
			call.addPrettyParam("patch_content", patchContent, true); // patch_content is markdown (diff)

			JsonObject jsonResult = new JsonObject();
			if (patchResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", patchResult.getMessage(), false);
				jsonResult.addProperty("status", "Success");
				jsonResult.addProperty("message", patchResult.getMessage());
			} else {
				result.addPrettyResult("status", "Error", false);
				result.addPrettyResult("message", patchResult.getMessage(), false);
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", patchResult.getMessage());
			}
			result.setResultJson(gson.toJson(jsonResult));

		} catch (JsonSyntaxException e) {
			String errorMsg = "Failed to parse JSON arguments for apply_patch: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);

			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("status", "Error");
			jsonResult.addProperty("message", errorMsg);
			result.setResultJson(gson.toJson(jsonResult));
		} catch (Exception e) {
			String errorMsg = "Error processing apply_patch function call: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);

			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("status", "Error");
			jsonResult.addProperty("message", errorMsg);
			result.setResultJson(gson.toJson(jsonResult));
		}
	}

	@SuppressWarnings("unchecked") // For casting List<String> from Map<String, Object>
	private void handlePerformTextSearch(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			java.lang.reflect.Type type = new TypeToken<Map<String, Object>>() {}.getType();
			Map<String, Object> args = gson.fromJson(functionArgsJson, type);

			String searchText = (String) args.get("search_text");
			boolean isRegEx = args.containsKey("is_regex") && args.get("is_regex") != null
					&& (args.get("is_regex") instanceof Boolean) ? (Boolean) args.get("is_regex") : false;
			List<String> fileNamePatterns = null;
			if (args.containsKey("file_name_patterns") && args.get("file_name_patterns") != null) {
				// Gson might parse to ArrayList<String> or similar, ensure it's correctly cast
				Object patternsObj = args.get("file_name_patterns");
				if (patternsObj instanceof List) {
					fileNamePatterns = (List<String>) patternsObj;
				}
			}

			boolean isCaseSensitive = args.containsKey("is_case_sensitive") ? (Boolean) args.get("is_case_sensitive") : false;
			boolean isWholeWord = args.containsKey("is_whole_word") ? (Boolean) args.get("is_whole_word") : false;

			if (searchText == null) {
				String errorMsg = "Missing required arguments (search_text, is_regex) for perform_text_search. Args: " + functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			TextSearchTool.SearchExecutionResult searchExecResult = searchTool.performSearch(
					searchText, isRegEx, isCaseSensitive, isWholeWord, fileNamePatterns
			);

			call.addPrettyParam("search_text", searchText, isRegEx); // Mark as code if regex
			call.addPrettyParam("is_regex", String.valueOf(isRegEx), false);
			if (fileNamePatterns != null) {
				call.addPrettyParam("file_name_patterns", gson.toJson(fileNamePatterns), false);
			} else {
				call.addPrettyParam("file_name_patterns", "all files", false);
			}
			call.addPrettyParam("is_case_sensitive", String.valueOf(isCaseSensitive), false);
			call.addPrettyParam("is_whole_word", String.valueOf(isWholeWord), false);

			JsonObject jsonResult = new JsonObject();
			if (searchExecResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", searchExecResult.getMessage(), false);
				jsonResult.addProperty("status", "Success");
				jsonResult.addProperty("message", searchExecResult.getMessage());

				StringBuilder resultsPreview = new StringBuilder();
				resultsPreview.append("Found ").append(searchExecResult.getResults().size()).append(" matches:\n");
				for (TextSearchTool.SearchResultItem item : searchExecResult.getResults()) {
					resultsPreview.append(String.format("- %s (Line %d): `%s` (Matched: `%s`)\n",
							item.getFilePath(), item.getLineNumber(), item.getLineContent(), item.getMatchedText()));
				}
				result.addPrettyResult("search_results_summary", resultsPreview.toString(), true); // Markdown for code backticks
				jsonResult.add("results", gson.toJsonTree(searchExecResult.getResults()));

			} else {
				result.addPrettyResult("status", "Error", false);
				result.addPrettyResult("message", searchExecResult.getMessage(), false);
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", searchExecResult.getMessage());
			}
			result.setResultJson(gson.toJson(jsonResult));

		} catch (JsonSyntaxException e) {
			String errorMsg = "Failed to parse JSON arguments for perform_text_search: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);
			// ... set JSON error result ...
		} catch (ClassCastException e) {
			String errorMsg = "Type error in arguments for perform_text_search: " + e.getMessage() + ". Args: " + functionArgsJson;
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);
			// ... set JSON error result ...
		} catch (Exception e) {
			String errorMsg = "Error processing perform_text_search function call: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);
			// ... set JSON error result ...
		}
	}

	/**
	 * Checks if there are any pending changes accumulated from any tool.
	 * @return true if there are pending changes, false otherwise.
	 */
	public boolean hasPendingChanges() {
		return applyChangeTool.getPendingChangeCount() > 0 || applyPatchTool.getPendingChangeCount() > 0;
	}

	/**
	 * Triggers the application of all accumulated changes from all tools.
	 * This typically shows a preview dialog to the user for each tool's changes.
	 */
	public void applyPendingChanges() {
		boolean appliedAny = false;
		if (applyChangeTool.getPendingChangeCount() > 0) {
			Activator.logInfo("Applying " + applyChangeTool.getPendingChangeCount() + " pending changes from ApplyChangeTool.");
			applyChangeTool.applyChanges();
			appliedAny = true;
		}
		if (applyPatchTool.getPendingChangeCount() > 0) {
			Activator.logInfo("Applying " + applyPatchTool.getPendingChangeCount() + " pending changes from ApplyPatchTool.");
			applyPatchTool.applyPendingChanges();
			appliedAny = true;
		}

		if (!appliedAny) {
			Activator.logInfo("No pending changes to apply from any tool.");
		}
	}

	/**
	 * Clears any pending changes that have been accumulated but not yet applied from all tools.
	 */
	public void clearPendingChanges() {
		if (applyChangeTool.getPendingChangeCount() > 0) {
			applyChangeTool.clearChanges();
			Activator.logInfo("Cleared pending changes from ApplyChangeTool.");
		}
		if (applyPatchTool.getPendingChangeCount() > 0) {
			applyPatchTool.clearChanges();
			Activator.logInfo("Cleared pending changes from ApplyPatchTool.");
		}
	}
}