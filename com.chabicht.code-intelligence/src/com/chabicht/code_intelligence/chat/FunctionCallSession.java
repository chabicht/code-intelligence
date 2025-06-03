package com.chabicht.code_intelligence.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ApplyChangeTool;
import com.chabicht.code_intelligence.chat.tools.ApplyPatchTool; // Added import
import com.chabicht.code_intelligence.chat.tools.ReadFileContentTool;
import com.chabicht.code_intelligence.chat.tools.ResourceAccess;
import com.chabicht.code_intelligence.chat.tools.TextSearchTool;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.util.GsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class FunctionCallSession {

	private final ResourceAccess resourceAccess = new ResourceAccess();
	private final ApplyChangeTool applyChangeTool = new ApplyChangeTool(resourceAccess);
	private final ApplyPatchTool applyPatchTool = new ApplyPatchTool(resourceAccess); // Added tool instance
	private final ReadFileContentTool readFileContentTool = new ReadFileContentTool(resourceAccess);
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
				handlePerformSearch(call, result, argsJson, false);
				break;
			case "perform_regex_search":
				handlePerformSearch(call, result, argsJson, true);
				break;
			case "read_file_content":
				handleReadFileContent(call, result, argsJson);
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
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);

			// Extract arguments using the names defined in the function declaration
			String fileName = args.has("file_name") ? args.get("file_name").getAsString() : null;
			String location = args.has("location_in_file") ? args.get("location_in_file").getAsString() : null;
			String originalText = args.has("original_text") ? args.get("original_text").getAsString() : null;
			String replacementText = args.has("replacement_text") ? args.get("replacement_text").getAsString() : null;

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
	 * @param functionArgsJson JSON arguments for apply_patch. Expected:
	 *                         {"file_name": "...", "patch_content": "..."}
	 */
	private void handleApplyPatch(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);
			String fileName = args.has("file_name") ? args.get("file_name").getAsString() : null;
			String patchContent = args.has("patch_content") ? args.get("patch_content").getAsString() : null;

			if (fileName == null || patchContent == null) {
				String errorMsg = "Missing required argument for apply_patch. Expected file_name and patch_content. Args: "
						+ functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);

				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			ApplyPatchTool.ApplyPatchResult patchResult = applyPatchTool.addChangesFromPatch(fileName, patchContent);

			call.addPrettyParam("file_name", fileName, false);
			call.addPrettyParam("patch_content", "```diff\n" + patchContent + "\n```", true);

			JsonObject jsonResult = new JsonObject();
			if (patchResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", patchResult.getMessage(), true);
				jsonResult.addProperty("status", "Success");
				jsonResult.addProperty("message", patchResult.getMessage());
			} else {
				result.addPrettyResult("status", "Error", false);
				result.addPrettyResult("message", patchResult.getMessage(), true);
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

	private void handlePerformSearch(FunctionCall call, FunctionResult result, String functionArgsJson,
			boolean isRegEx) {
		try {
			// Parse the JSON arguments directly into a JsonObject
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);

			// Extract arguments using the names defined in the function declaration
			String searchText = null;
			String searchParamName = null;
			if (args.has("search_text")) {
				searchText = args.get("search_text").getAsString();
				searchParamName = "search_text";
			} else if (args.has("search_pattern")) {
				searchText = args.get("search_pattern").getAsString();
				searchParamName = "search_pattern";
			}
			
			List<String> fileNamePatterns = null;
			if (args.has("file_name_patterns") && !args.get("file_name_patterns").isJsonNull()) {
				JsonArray patternsArray = args.get("file_name_patterns").getAsJsonArray();
				fileNamePatterns = new ArrayList<>();
				for (JsonElement element : patternsArray) {
					fileNamePatterns.add(element.getAsString());
				}
			}

			boolean isCaseSensitive = args.has("is_case_sensitive") 
					? args.get("is_case_sensitive").getAsBoolean() : false;
			boolean isWholeWord = args.has("is_whole_word") 
					? args.get("is_whole_word").getAsBoolean() : false;

			// Basic validation
			if (searchText == null) {
				String errorMsg = "Missing required argument (search_text) for perform_text_search. Args: "
						+ functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			TextSearchTool.SearchExecutionResult searchExecResult = searchTool.performSearch(searchText, isRegEx,
					isCaseSensitive, isWholeWord, fileNamePatterns);

			call.addPrettyParam(searchParamName, searchText, isRegEx); // Mark as code if regex
			if (fileNamePatterns != null) {
				call.addPrettyParam("file_name_patterns", gson.toJson(fileNamePatterns), false);
			} else {
				call.addPrettyParam("file_name_patterns", "all files", false);
			}
			call.addPrettyParam("is_case_sensitive", String.valueOf(isCaseSensitive), false);
			if (!isRegEx) {
				call.addPrettyParam("is_whole_word", String.valueOf(isWholeWord), false);
			}

			JsonObject jsonResult = new JsonObject();
			if (searchExecResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", searchExecResult.getMessage(), false);
				jsonResult.addProperty("status", "Success");
				jsonResult.addProperty("message", searchExecResult.getMessage());

				StringBuilder resultsPreview = new StringBuilder();
				resultsPreview.append("Found ").append(searchExecResult.getResults().size()).append(" matches:\n");
				for (TextSearchTool.SearchResultItem item : searchExecResult.getResults()) {
					resultsPreview.append(String.format("- %s (Line %d): `%s` (Matched: `%s`)\n", item.getFilePath(),
							item.getLineNumber(), item.getLineContent(), item.getMatchedText()));
				}
				result.addPrettyResult("search_results_summary", resultsPreview.toString(), true); // Markdown for code
																									// backticks
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
		} catch (Exception e) {
			String errorMsg = "Error processing perform_text_search function call: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);
			// ... set JSON error result ...
		}
	}

	/**
	 * Specifically handles the "read_file_content" function call. Parses arguments
	 * and reads the content of the specified file or line range.
	 *
	 * @param call             The FunctionCall object
	 * @param result           The FunctionResult object to populate
	 * @param functionArgsJson JSON arguments for read_file_content. Expected:
	 *                         {"file_name": "...", "start_line": ..., "end_line": ...}
	 */
	private void handleReadFileContent(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);

			String fileName = args.has("file_name") ? args.get("file_name").getAsString() : null;
			Integer startLine = args.has("start_line") && !args.get("start_line").isJsonNull() ? args.get("start_line").getAsInt() : null;
			Integer endLine = args.has("end_line") && !args.get("end_line").isJsonNull() ? args.get("end_line").getAsInt() : null;

			if (fileName == null) {
				String errorMsg = "Missing required argument 'file_name' for read_file_content. Args: " + functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			// Populate pretty params for the call
			call.addPrettyParam("file_name", fileName, false);
			if (startLine != null) {
				call.addPrettyParam("start_line", String.valueOf(startLine), false);
			}
			if (endLine != null) {
				call.addPrettyParam("end_line", String.valueOf(endLine), false);
			}

			ReadFileContentTool.ReadFileContentResult readResult = readFileContentTool.readFileContent(fileName, startLine, endLine);

			JsonObject jsonResponse = new JsonObject();
			if (readResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", readResult.getMessage(), false);
				// Add file path and actual range to pretty results for clarity
				result.addPrettyResult("file_path_read", readResult.getFilePath() != null ? readResult.getFilePath() : "N/A", false);
				if (readResult.getActualStartLine() > 0 || readResult.getActualEndLine() > 0) { // Check if a valid range was read
					result.addPrettyResult("lines_read", readResult.getActualStartLine() + " - " + readResult.getActualEndLine(), false);
				} else if (readResult.getFilePath() != null && readResult.getContentWithLineNumbers() != null && readResult.getContentWithLineNumbers().isEmpty()) {
					result.addPrettyResult("lines_read", "File is empty", false);
				}

				// The content itself is the main result, show it as markdown (code block)
				String contentToDisplay = readResult.getContentWithLineNumbers();
				result.addPrettyResult("file_content", "```\n" + contentToDisplay + "\n```", true);

				jsonResponse.addProperty("status", "Success");
				jsonResponse.addProperty("message", readResult.getMessage());
				jsonResponse.addProperty("file_path", readResult.getFilePath());
				jsonResponse.addProperty("content", readResult.getContentWithLineNumbers()); // Prefixed content
				jsonResponse.addProperty("actual_start_line", readResult.getActualStartLine());
				jsonResponse.addProperty("actual_end_line", readResult.getActualEndLine());
			} else {
				result.addPrettyResult("status", "Error", false);
				result.addPrettyResult("message", readResult.getMessage(), false);
				jsonResponse.addProperty("status", "Error");
				jsonResponse.addProperty("message", readResult.getMessage());
			}
			result.setResultJson(gson.toJson(jsonResponse));

		} catch (Exception e) { // Catch general Exception
			String errorMsg = "Error processing read_file_content function call: " + e.getMessage();
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
	 * Checks if there are any pending changes accumulated from any tool.
	 * 
	 * @return true if there are pending changes, false otherwise.
	 */
	public boolean hasPendingChanges() {
		return applyChangeTool.getPendingChangeCount() > 0 || applyPatchTool.getPendingChangeCount() > 0;
	}

	/**
	 * Triggers the application of all accumulated changes from all tools. This
	 * typically shows a preview dialog to the user for each tool's changes.
	 */
	public void applyPendingChanges() {
		boolean appliedAny = false;
		if (applyChangeTool.getPendingChangeCount() > 0) {
			Activator.logInfo(
					"Applying " + applyChangeTool.getPendingChangeCount() + " pending changes from ApplyChangeTool.");
			applyChangeTool.applyChanges();
			appliedAny = true;
		}
		if (applyPatchTool.getPendingChangeCount() > 0) {
			Activator.logInfo(
					"Applying " + applyPatchTool.getPendingChangeCount() + " pending changes from ApplyPatchTool.");
			applyPatchTool.applyPendingChanges();
			appliedAny = true;
		}

		if (!appliedAny) {
			Activator.logInfo("No pending changes to apply from any tool.");
		}
	}

	/**
	 * Clears any pending changes that have been accumulated but not yet applied
	 * from all tools.
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