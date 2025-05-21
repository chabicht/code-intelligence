package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.FileTextSearchScope;
import org.eclipse.search.ui.text.Match;
import org.eclipse.search.ui.text.TextSearchQueryProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.chabicht.code_intelligence.util.Log; // Assuming you have a Log utility like in ApplyPatchTool

public class TextSearchTool {

	private final ResourceAccess resourceAccess;

	public TextSearchTool(ResourceAccess resourceAccess) {
		this.resourceAccess = resourceAccess;
	}

	public static class SearchResultItem {
		private final String filePath;
		private final int lineNumber;
		private final String lineContent;
		private final String matchedText;

		public SearchResultItem(String filePath, int lineNumber, String lineContent, String matchedText) {
			this.filePath = filePath;
			this.lineNumber = lineNumber;
			this.lineContent = lineContent;
			this.matchedText = matchedText;
		}

		// Getters
		public String getFilePath() {
			return filePath;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		public String getLineContent() {
			return lineContent;
		}

		public String getMatchedText() {
			return matchedText;
		}
	}

	public static class SearchExecutionResult {
		private final boolean success;
		private final String message;
		private final List<SearchResultItem> results;

		public SearchExecutionResult(boolean success, String message, List<SearchResultItem> results) {
			this.success = success;
			this.message = message;
			this.results = results != null ? results : new ArrayList<>();
		}

		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public List<SearchResultItem> getResults() {
			return results;
		}
	}

	private void collectMatchesFromSearchResult(ISearchResult searchResult, List<SearchResultItem> collectedResults) {
		Map<IFile, IDocument> documentMap = new HashMap<>();

		if (!(searchResult instanceof AbstractTextSearchResult)) {
			// This case should ideally not be reached if
			// TextSearchQueryProvider.getPreferred() is used,
			// as it should produce AbstractTextSearchResult. Logging it is fine, but for
			// the AI,
			// the main performSearch method's return will indicate overall success/failure.
			return;
		}
		AbstractTextSearchResult textResult = (AbstractTextSearchResult) searchResult;

		try {
			for (Object element : textResult.getElements()) {
				if (element instanceof IFile) {
					IFile file = (IFile) element;
					IDocument document = null;
					try {
						document = resourceAccess.getDocumentAndConnect(file, documentMap);
						if (document == null) {
							Log.logInfo("Could not get document for file (skipping matches in it): "
									+ file.getFullPath().toString()); // Using Log.logInfo for warnings
							continue;
						}

						for (Match match : textResult.getMatches(element)) {
							int offset = match.getOffset();
							int length = match.getLength();

							try {
								int lineNumber = document.getLineOfOffset(offset) + 1; // 1-based
								IRegion lineInfo = document.getLineInformation(lineNumber - 1);
								String lineContent = document.get(lineInfo.getOffset(), lineInfo.getLength());
								String matchedTextActual = document.get(offset, length);

								collectedResults.add(new SearchResultItem(file.getFullPath().toString(), lineNumber,
										lineContent.trim(), // Trim to remove leading/trailing whitespace from the line
										matchedTextActual));
							} catch (BadLocationException e) {
								Log.logError("Error extracting match details from " + file.getFullPath().toString()
										+ " at offset " + offset, e);
							}
						}
					} catch (Exception e) { // From getDocumentAndConnect
						Log.logError("Exception while processing file " + file.getFullPath().toString(), e);
					}
				}
			}
		} finally {
			resourceAccess.disconnectAllDocuments(documentMap);
		}
	}

	public SearchExecutionResult performSearch(String searchText, boolean isRegEx, boolean isCaseSensitive,
			boolean isWholeWord, List<String> fileNamePatterns) {
		TextSearchQueryProvider provider = TextSearchQueryProvider.getPreferred();
		if (provider == null) {
			// Log.logError("No preferred TextSearchQueryProvider found."); // No need to
			// log here if we return it
			return new SearchExecutionResult(false, "No preferred TextSearchQueryProvider found.", null);
		}

		String[] patternsArray = (fileNamePatterns == null || fileNamePatterns.isEmpty()) ? new String[] { "*.*" }
				: fileNamePatterns.toArray(new String[0]);

		TextSearchQueryProvider.TextSearchInput searchInput = new TextSearchQueryProvider.TextSearchInput() {
			@Override
			public String getSearchText() {
				return searchText;
			}

			@Override
			public boolean isRegExSearch() {
				return isRegEx;
			}

			@Override
			public boolean isCaseSensitiveSearch() {
				return isCaseSensitive;
			}

			@Override
			public boolean isWholeWordSearch() {
				return isWholeWord;
			}

			@Override
			public boolean searchInBinaries() {
				return false;
			} // Defaulting to false

			@Override
			public FileTextSearchScope getScope() {
				IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
				return FileTextSearchScope.newSearchScope(new IResource[] { workspaceRoot }, patternsArray, false);
			}
		};

		ISearchQuery query;
		try {
			query = provider.createQuery(searchInput);
		} catch (CoreException e) {
			// Log.logError("Failed to create search query: " + e.getMessage(), e);
			return new SearchExecutionResult(false, "Failed to create search query: " + e.getMessage(), null);
		} catch (IllegalArgumentException e) { // Should be caught by provider.createQuery if args are bad for its impl
			// Log.logError("Invalid arguments for search query: " + e.getMessage(), e);
			return new SearchExecutionResult(false, "Invalid arguments for search query: " + e.getMessage(), null);
		}

		List<SearchResultItem> collectedResults = new ArrayList<>();
		IStatus status = Display.getDefault().syncCall(() -> {
			Shell shell = new Shell(Display.getCurrent());
			try {
				return NewSearchUI.runQueryInForeground(new ProgressMonitorDialog(shell), query);
			} catch (Exception e) {
				return Status.error("Error while invoking search", e);
			} finally {
				shell.dispose();
				shell = null;
			}
		});

		// Check the status of the search operation
		if (status.isOK() || status.matches(IStatus.INFO) || status.matches(IStatus.WARNING)
				|| status.matches(IStatus.CANCEL)) {
			// Proceed to collect results even on cancel or with info/warnings, as some
			// results might be available.
			collectMatchesFromSearchResult(query.getSearchResult(), collectedResults);

			String message = "Search completed.";
			if (status.matches(IStatus.CANCEL))
				message = "Search was cancelled. " + collectedResults.size() + " results found before cancellation.";
			else if (!status.isOK())
				message = "Search completed with status: " + status.getMessage() + ". " + collectedResults.size()
						+ " results found.";
			else
				message = "Search completed successfully. " + collectedResults.size() + " results found.";

			return new SearchExecutionResult(true, message, collectedResults); // true indicates the operation itself
																				// was attempted/completed
		} else {
			// Error status
			// Log.logError("Search query execution failed: " + status.getMessage(),
			// status.getException());
			return new SearchExecutionResult(false, "Search query execution failed: " + status.getMessage(), null);
		}
	}
}
