package com.chabicht.code_intelligence.chat;

import java.util.Map;
import java.util.Optional;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.chat.tools.ApplyChangeTool;
import com.chabicht.code_intelligence.chat.tools.ResourceAccess;
import com.chabicht.code_intelligence.model.ChatConversation.ChatMessage;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionCall;
import com.chabicht.code_intelligence.model.ChatConversation.FunctionResult;
import com.chabicht.code_intelligence.util.GsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken; // Import needed for Map type

public class FunctionCallSession {

	private final ResourceAccess resourceAccess = new ResourceAccess();
	private final ApplyChangeTool applyChangeTool = new ApplyChangeTool(resourceAccess);
	private final Gson gson = GsonUtil.createGson();

	public FunctionCallSession() {
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
			String resultJson = null;

			switch (functionName) {
			case "apply_change":
				handleApplyChange(argsJson);
				break;
			default:
				Activator.logError("Unsupported function call: " + functionName);
				break;
			}

			message.setFunctionResult(new FunctionResult(call.getId(), functionName, resultJson));
		}
	}

	/**
	 * Specifically handles the "apply_change" function call.
	 * Parses arguments and adds the change to the ApplyChangeTool queue.
	 *
	 * @param functionArgsJson JSON arguments for apply_change.
	 */
	private void handleApplyChange(String functionArgsJson) {
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
				Activator.logError("Missing required argument for apply_change. Args: " + functionArgsJson);
				// Decide how to handle missing args: skip this change, stop processing, etc.
				// For now, just log and skip adding the change.
				return;
			}

			// Add the change to the tool's queue
			applyChangeTool.addChange(fileName, location, originalText, replacementText);

		} catch (JsonSyntaxException e) {
			Activator.logError("Failed to parse JSON arguments for apply_change: " + functionArgsJson, e);
		} catch (Exception e) { // Catch unexpected errors during handling
			Activator.logError("Error handling apply_change function call: " + e.getMessage(), e);
		}
	}

	/**
	 * Checks if there are any pending changes accumulated.
	 * @return true if there are pending changes, false otherwise.
	 */
	public boolean hasPendingChanges() {
		return applyChangeTool.getPendingChangeCount() > 0;
	}

	/**
	 * Triggers the application of all accumulated changes using the ApplyChangeTool.
	 * This typically shows a preview dialog to the user.
	 */
	public void applyPendingChanges() {
		if (hasPendingChanges()) {
			Activator.logInfo("Applying " + applyChangeTool.getPendingChangeCount() + " pending changes.");
			// ApplyChangeTool.applyChanges() handles the UI interaction (refactoring wizard)
			// It should already ensure it runs on the UI thread.
			applyChangeTool.applyChanges();
			// Note: ApplyChangeTool clears its own changes after the wizard is handled.
		} else {
			Activator.logInfo("No pending changes to apply.");
		}
	}

	/**
	 * Clears any pending changes that have been accumulated but not yet applied.
	 */
	public void clearPendingChanges() {
		applyChangeTool.clearChanges();
	}
}
