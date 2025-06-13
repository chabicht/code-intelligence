package com.chabicht.code_intelligence.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.MultiStateTextFileChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ApplyChangeTool;
import com.chabicht.code_intelligence.chat.tools.ApplyPatchTool;
import com.chabicht.code_intelligence.chat.tools.BufferedResourceAccess;
import com.chabicht.code_intelligence.chat.tools.CreateFileTool; // Added import
import com.chabicht.code_intelligence.chat.tools.FindFilesTool; // Add this import
import com.chabicht.code_intelligence.chat.tools.IResourceAccess;
import com.chabicht.code_intelligence.chat.tools.ListProjectsTool;
import com.chabicht.code_intelligence.chat.tools.ReadFileContentTool;
import com.chabicht.code_intelligence.chat.tools.ResourceAccess;
import com.chabicht.code_intelligence.chat.tools.TextSearchTool;
import com.chabicht.code_intelligence.chat.tools.ToolChangePreparationResult;
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
	private final IResourceAccess realResourceAccess;
	private final BufferedResourceAccess bufferedResourceAccess;

	// Tools use the buffered resource access to see pending changes
	private final ApplyChangeTool applyChangeTool;
	private final ApplyPatchTool applyPatchTool;
	private final ReadFileContentTool readFileContentTool;
	private final CreateFileTool createFileTool;
	private final TextSearchTool searchTool;
	private final FindFilesTool findFilesTool; // Add this field
	private final ListProjectsTool listProjectsTool;
	private final Gson gson = GsonUtil.createGson();

	private final Map<String, MultiStateTextFileChange> pendingTextFileChanges = new HashMap<>();
	private final Map<String, Change> pendingCreateFileChanges = new HashMap<>();
	private final List<UUID> messagesWithPendingChanges = new ArrayList<>();

	public FunctionCallSession() {
		// Create the real resource access
		this.realResourceAccess = new ResourceAccess();

		// Create the buffered wrapper that applies pending changes transparently
		this.bufferedResourceAccess = new BufferedResourceAccess(realResourceAccess, this);

		// Initialize all tools with the buffered resource access so they can see
		// pending changes
		this.applyChangeTool = new ApplyChangeTool(bufferedResourceAccess);
		this.applyPatchTool = new ApplyPatchTool(bufferedResourceAccess);
		this.readFileContentTool = new ReadFileContentTool(bufferedResourceAccess);
		this.createFileTool = new CreateFileTool(); // Doesn't use resource access
		this.searchTool = new TextSearchTool(bufferedResourceAccess);
		this.findFilesTool = new FindFilesTool(bufferedResourceAccess); // Add this line
		this.listProjectsTool = new ListProjectsTool(realResourceAccess);

		Activator.logInfo("FunctionCallSession: Initialized with BufferedResourceAccess");
	}

	private MultiStateTextFileChange getOrCreateMultiStateTextFileChange(IFile file) {
		String fullPath = file.getFullPath().toString();
		return pendingTextFileChanges.computeIfAbsent(fullPath, k -> {
			MultiStateTextFileChange mstfc = new MultiStateTextFileChange("Changes for " + file.getName(), file);
			if (file.getFileExtension() != null) {
				mstfc.setTextType(file.getFileExtension());
			} else {
				mstfc.setTextType("txt");
			}
			Activator.logInfo("Created new MultiStateTextFileChange for: " + fullPath);
			return mstfc;
		});
	}

	public ApplyChangeTool getApplyChangeTool() {
		return applyChangeTool;
	}

	public ApplyPatchTool getApplyPatchTool() {
		return applyPatchTool;
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
				handleApplyChange(message.getId(), call, result, argsJson);
				break;
			case "apply_patch":
				handleApplyPatch(message.getId(), call, result, argsJson);
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
			case "create_file": // Added case for create_file
				handleCreateFile(message.getId(), call, result, argsJson);
				break;
			case "find_files": // Add this new case
				handleFindFiles(call, result, argsJson);
				break;
			case "list_projects":
				handleListProjects(call, result, argsJson);
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
	private void handleApplyChange(UUID messageId, FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);
			String fileName = args.has("file_name") ? args.get("file_name").getAsString() : null;
			String location = args.has("location_in_file") ? args.get("location_in_file").getAsString() : null;
			String originalText = args.has("original_text") ? args.get("original_text").getAsString() : null;
			String replacementText = args.has("replacement_text") ? args.get("replacement_text").getAsString() : null;

			// Basic validation
			if (fileName == null || location == null || originalText == null || replacementText == null) {
				String errorMsg = "Missing required argument for apply_change. Args: " + functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			ToolChangePreparationResult prepResult = applyChangeTool.prepareChange(fileName, location, originalText,
					replacementText);

			call.addPrettyParam("file_name", fileName, false);
			call.addPrettyParam("location_in_file", location, false);
			call.addPrettyParam("original_text", originalText, true);
			call.addPrettyParam("replacement_text", replacementText, true);

			JsonObject jsonResult = new JsonObject();
			if (prepResult.isSuccess()) {
				MultiStateTextFileChange mstfc = getOrCreateMultiStateTextFileChange(prepResult.getFile());
				TextFileChange tfc = new TextFileChange("Apply Change", prepResult.getFile());
				MultiTextEdit multiEdit = new MultiTextEdit();
				multiEdit.addChildren(prepResult.getEdits().toArray(new TextEdit[0]));
				tfc.setEdit(multiEdit);
				mstfc.addChange(tfc);

				result.addPrettyResult("preview", "```diff\n" + prepResult.getDiffPreview() + "\n```", true);
				result.addPrettyResult("status", "Change Queued", false);
				jsonResult.addProperty("status", "Queued");
				messagesWithPendingChanges.add(messageId);
			} else {
				result.addPrettyResult("status", "Error", false);
				jsonResult.addProperty("status", "Error");
			}
			result.addPrettyResult("message", prepResult.getMessage(), false);
			jsonResult.addProperty("message", prepResult.getMessage());
			result.setResultJson(gson.toJson(jsonResult));
		} catch (Exception e) {
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
	private void handleApplyPatch(UUID messageId, FunctionCall call, FunctionResult result, String functionArgsJson) {
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

			ToolChangePreparationResult prepResult = applyPatchTool.preparePatchChange(fileName, patchContent);

			call.addPrettyParam("file_name", fileName, false);
			call.addPrettyParam("patch_content", "```diff\n" + patchContent + "\n```", true);

			JsonObject jsonResult = new JsonObject();
			if (prepResult.isSuccess()) {
				MultiStateTextFileChange mstfc = getOrCreateMultiStateTextFileChange(prepResult.getFile());
				TextFileChange tfc = new TextFileChange("Apply Patch", prepResult.getFile());
				tfc.setEdit(prepResult.getEdits().get(0)); // Patch tool returns one big ReplaceEdit
				mstfc.addChange(tfc);

				result.addPrettyResult("status", "Patch Queued", false);
				jsonResult.addProperty("status", "Queued");
				messagesWithPendingChanges.add(messageId);
			} else {
				result.addPrettyResult("status", "Error", false);
				jsonResult.addProperty("status", "Error");
			}
			result.addPrettyResult("message", prepResult.getMessage(), true);
			jsonResult.addProperty("message", prepResult.getMessage());
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

			boolean isCaseSensitive = args.has("is_case_sensitive") ? args.get("is_case_sensitive").getAsBoolean()
					: false;
			boolean isWholeWord = args.has("is_whole_word") ? args.get("is_whole_word").getAsBoolean() : false;

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
	 *                         {"file_name": "...", "start_line": ..., "end_line":
	 *                         ...}
	 */
	private void handleReadFileContent(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);

			String fileName = args.has("file_name") ? args.get("file_name").getAsString() : null;
			Integer startLine = args.has("start_line") && !args.get("start_line").isJsonNull()
					? args.get("start_line").getAsInt()
					: null;
			Integer endLine = args.has("end_line") && !args.get("end_line").isJsonNull()
					? args.get("end_line").getAsInt()
					: null;

			if (fileName == null) {
				String errorMsg = "Missing required argument 'file_name' for read_file_content. Args: "
						+ functionArgsJson;
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

			ReadFileContentTool.ReadFileContentResult readResult = readFileContentTool.readFileContent(fileName,
					startLine, endLine);

			JsonObject jsonResponse = new JsonObject();
			if (readResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				result.addPrettyResult("message", readResult.getMessage(), false);
				// Add file path and actual range to pretty results for clarity
				result.addPrettyResult("file_path_read",
						readResult.getFilePath() != null ? readResult.getFilePath() : "N/A", false);
				if (readResult.getActualStartLine() > 0 || readResult.getActualEndLine() > 0) { // Check if a valid
																								// range was read
					result.addPrettyResult("lines_read",
							readResult.getActualStartLine() + " - " + readResult.getActualEndLine(), false);
				} else if (readResult.getFilePath() != null && readResult.getContentWithLineNumbers() != null
						&& readResult.getContentWithLineNumbers().isEmpty()) {
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
	 * Specifically handles the "create_file" function call. Parses arguments and
	 * attempts to create a new file with the given content.
	 *
	 * @param call             The FunctionCall object
	 * @param result           The FunctionResult object to populate
	 * @param functionArgsJson JSON arguments for create_file. Expected:
	 *                         {"file_path": "...", "content": "..."}
	 */
	private void handleCreateFile(UUID messageId, FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);
			String filePath = args.has("file_path") ? args.get("file_path").getAsString() : null;
			String content = args.has("content") ? args.get("content").getAsString() : null;

			if (filePath == null || content == null) {
				String errorMsg = "Missing required arguments for create_file. Expected 'file_path' and 'content'. Args: "
						+ functionArgsJson;
				Activator.logError(errorMsg);
				result.addPrettyResult("status", "Error", false);
				result.addPrettyResult("message", errorMsg, false);
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			CreateFileTool.CreateFilePreparationResult prepResult = createFileTool.prepareCreateFileChange(filePath,
					content);

			call.addPrettyParam("file_path", filePath, false);
			call.addPrettyParam("content", content, true);

			JsonObject jsonResponse = new JsonObject();
			if (prepResult.isSuccess()) {
				pendingCreateFileChanges.put(filePath, prepResult.getChange());
				result.addPrettyResult("status", "Queued for Review", false);
				jsonResponse.addProperty("status", "Queued");
				messagesWithPendingChanges.add(messageId);
			} else {
				result.addPrettyResult("status", "Error", false);
				jsonResponse.addProperty("status", "Error");
			}
			result.addPrettyResult("message", prepResult.getMessage(), false);
			jsonResponse.addProperty("message", prepResult.getMessage());
			result.setResultJson(gson.toJson(jsonResponse));
		} catch (Exception e) {
			String errorMsg = "Error processing create_file function call: " + e.getMessage();
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
		return !pendingTextFileChanges.isEmpty() || !pendingCreateFileChanges.isEmpty();
	}

	/**
	 * Triggers the application of all accumulated changes from all tools. This
	 * typically shows a preview dialog to the user for each tool's changes.
	 */
	public void applyPendingChanges() {
		CompositeChange rootChange = new CompositeChange("Apply AI Suggested Code Changes");

		rootChange.addAll(pendingTextFileChanges.values().toArray(new Change[0]));
		rootChange.addAll(pendingCreateFileChanges.values().toArray(new Change[0]));

		try {
			if (rootChange.getChildren().length > 0) {
				launchRefactoringWizard(rootChange);
			} else {
				Activator.logInfo("No pending changes from any tool to apply.");
			}
		} catch (Exception e) {
			Activator.logError("Failed to initiate refactoring process: " + e.getMessage(), e);
		} finally {
			clearPendingChanges();
		}
	}

	/**
	 * Clears any pending changes that have been accumulated but not yet applied
	 * from all tools.
	 */
	public void clearPendingChanges() {
		pendingTextFileChanges.clear();
		pendingCreateFileChanges.clear();
		messagesWithPendingChanges.clear();

		// Clear the buffer caches
		if (bufferedResourceAccess != null) {
			bufferedResourceAccess.clearCaches();
			Activator.logInfo("FunctionCallSession: Cleared pending changes and buffer caches");
		}
	}

	public List<UUID> getMessagesWithPendingChanges() {
		return messagesWithPendingChanges;
	}

	/**
	 * Gets the map of pending text file changes. The key is the file path, the
	 * value is the MultiStateTextFileChange.
	 * 
	 * @return Unmodifiable view of pending text file changes
	 */
	public Map<String, MultiStateTextFileChange> getPendingTextFileChanges() {
		return java.util.Collections.unmodifiableMap(pendingTextFileChanges);
	}

	/**
	 * Gets the map of pending create file changes. The key is the file path, the
	 * value is the Change object.
	 * 
	 * @return Unmodifiable view of pending create file changes
	 */
	public Map<String, Change> getPendingCreateFileChanges() {
		return java.util.Collections.unmodifiableMap(pendingCreateFileChanges);
	}

	/**
	 * Generates a user-friendly summary of all pending file creations and
	 * modifications.
	 * 
	 * @return A string summarizing the pending changes, formatted for display in
	 *         the chat.
	 */
	public String getPendingChangesSummary() {
		List<String> modifiedFiles = new ArrayList<>(pendingTextFileChanges.keySet());
		List<String> createdFiles = new ArrayList<>(pendingCreateFileChanges.keySet());

		java.util.Collections.sort(modifiedFiles);
		java.util.Collections.sort(createdFiles);

		if (modifiedFiles.isEmpty() && createdFiles.isEmpty()) {
			return "Tool usage complete. No file changes were queued.";
		}

		StringBuilder summary = new StringBuilder("Tool usage complete. The following changes are queued for review:");

		for (String path : createdFiles) {
			summary.append("\n- **Create:** `").append(path).append("`");
		}
		for (String path : modifiedFiles) {
			summary.append("\n- **Modify:** `").append(path).append("`");
		}

		return summary.toString();
	}

	private void launchRefactoringWizard(CompositeChange rootChange) {
		Refactoring refactoring = new Refactoring() {
			@Override
			public String getName() {
				return "Apply AI Suggested Code Changes"; // Wizard title
			}

			@Override
			public RefactoringStatus checkInitialConditions(IProgressMonitor pm) {
				return new RefactoringStatus();
			}

			@Override
			public RefactoringStatus checkFinalConditions(IProgressMonitor pm) {
				return new RefactoringStatus();
			}

			@Override
			public Change createChange(IProgressMonitor pm) {
				return rootChange;
			}
		};

		Display.getDefault().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window == null) {
					Activator.logError("Cannot apply AI changes: No active workbench window.");
					return;
				}
				RefactoringWizard wizard = new RefactoringWizard(refactoring,
						RefactoringWizard.DIALOG_BASED_USER_INTERFACE | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
					@Override
					protected void addUserInputPages() {
						// No custom input pages needed
					}
				};
				RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
				operation.run(window.getShell(), "Preview AI Suggested Changes"); // Dialog title
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Activator.logError("AI changes refactoring wizard interrupted: " + e.getMessage(), e);
			} catch (Exception e) {
				Activator.logError("Failed to open or run AI changes refactoring wizard: " + e.getMessage(), e);
			}
		});
	}

	private void handleFindFiles(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			JsonObject args = gson.fromJson(functionArgsJson, JsonObject.class);

			String pattern = args.has("file_path_pattern") ? args.get("file_path_pattern").getAsString() : null;

			List<String> projectNames = null;
			if (args.has("project_names") && !args.get("project_names").isJsonNull()) {
				JsonArray projectsArray = args.get("project_names").getAsJsonArray();
				projectNames = new ArrayList<>();
				for (JsonElement element : projectsArray) {
					projectNames.add(element.getAsString());
				}
			}

			boolean isCaseSensitive = args.has("is_case_sensitive") ? args.get("is_case_sensitive").getAsBoolean()
					: false;

			if (pattern == null) {
				String errorMsg = "Missing required argument 'file_path_pattern' for find_files.";
				Activator.logError(errorMsg);
				result.addPrettyResult("error", errorMsg, false);
				JsonObject jsonResult = new JsonObject();
				jsonResult.addProperty("status", "Error");
				jsonResult.addProperty("message", errorMsg);
				result.setResultJson(gson.toJson(jsonResult));
				return;
			}

			// Add pretty params to the call for UI display
			call.addPrettyParam("file_path_pattern", pattern, true); // True for code formatting
			if (projectNames != null && !projectNames.isEmpty()) {
				call.addPrettyParam("project_names", gson.toJson(projectNames), false);
			} else {
				call.addPrettyParam("project_names", "all projects", false);
			}
			call.addPrettyParam("is_case_sensitive", String.valueOf(isCaseSensitive), false);

			FindFilesTool.FindFilesResult findResult = findFilesTool.findFiles(pattern, projectNames, isCaseSensitive);

			JsonObject jsonResponse = new JsonObject();
			if (findResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				jsonResponse.addProperty("status", "Success");

				// Create a markdown list for the pretty result (for the user)
				StringBuilder filesPreview = new StringBuilder();
				filesPreview.append("Found ").append(findResult.getFilePaths().size()).append(" matching files:\n");
				for (String path : findResult.getFilePaths()) {
					filesPreview.append("- `").append(path).append("`\n");
				}
				result.addPrettyResult("found_files_summary", filesPreview.toString(), true);

				// Create a structured JSON array for the model, similar to TextSearchTool
				JsonArray resultsArray = new JsonArray();
				for (String path : findResult.getFilePaths()) {
					JsonObject fileObject = new JsonObject();
					fileObject.addProperty("file_path", path);
					resultsArray.add(fileObject);
				}
				jsonResponse.add("results", resultsArray);

			} else {
				result.addPrettyResult("status", "Error", false);
				jsonResponse.addProperty("status", "Error");
			}
			result.addPrettyResult("message", findResult.getMessage(), false);
			jsonResponse.addProperty("message", findResult.getMessage());
			result.setResultJson(gson.toJson(jsonResponse));

		} catch (Exception e) {
			String errorMsg = "Error processing find_files function call: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);
			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("status", "Error");
			jsonResult.addProperty("message", errorMsg);
			result.setResultJson(gson.toJson(jsonResult));
		}
	}

	private void handleListProjects(FunctionCall call, FunctionResult result, String functionArgsJson) {
		try {
			// This tool takes no arguments.
			call.addPrettyParam("action", "list all projects", false);

			ListProjectsTool.ListProjectsResult listResult = listProjectsTool.listProjects();

			JsonObject jsonResponse = new JsonObject();
			if (listResult.isSuccess()) {
				result.addPrettyResult("status", "Success", false);
				jsonResponse.addProperty("status", "Success");

				// Create a markdown list for the pretty result (for the user)
				StringBuilder projectsPreview = new StringBuilder();
				projectsPreview.append("Found ").append(listResult.getProjects().size())
						.append(" projects in the workspace:\n");
				for (ListProjectsTool.ProjectInfo info : listResult.getProjects()) {
					projectsPreview.append("- `").append(info.getProjectName()).append("` (")
							.append(info.isOpen() ? "Open" : "Closed").append(")\n");
				}
				result.addPrettyResult("projects_summary", projectsPreview.toString(), true);

				// Create a structured JSON array for the model
				jsonResponse.add("projects", gson.toJsonTree(listResult.getProjects()));

			} else {
				result.addPrettyResult("status", "Error", false);
				jsonResponse.addProperty("status", "Error");
			}
			result.addPrettyResult("message", listResult.getMessage(), false);
			jsonResponse.addProperty("message", listResult.getMessage());
			result.setResultJson(gson.toJson(jsonResponse));

		} catch (Exception e) {
			String errorMsg = "Error processing list_projects function call: " + e.getMessage();
			Activator.logError(errorMsg, e);
			result.addPrettyResult("status", "Error", false);
			result.addPrettyResult("message", errorMsg, false);
			JsonObject jsonResult = new JsonObject();
			jsonResult.addProperty("status", "Error");
			jsonResult.addProperty("message", errorMsg);
			result.setResultJson(gson.toJson(jsonResult));
		}
	}
}
