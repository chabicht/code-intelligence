package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.search.ui.IQueryListener;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.FileTextSearchScope;
import org.eclipse.search.ui.text.Match;
import org.eclipse.search.ui.text.TextSearchQueryProvider;
import org.eclipse.swt.widgets.Display;

import com.chabicht.code_intelligence.util.Log; // Assuming you have a Log utility like in ApplyPatchTool

public class TextSearchTool {

	private final IResourceAccess resourceAccess;

	public TextSearchTool(IResourceAccess resourceAccess) {
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

		if (fileNamePatterns == null) {
			fileNamePatterns = List.of("*");
		}

		// Hack: Sometimes patterns are supplied that are not supported by Eclipse, e.g.
		// glob patterns.
		List<String> filteredFileNamePatterns = filter(fileNamePatterns);

		String[] patternsArray = (filteredFileNamePatterns == null || filteredFileNamePatterns.isEmpty())
				? new String[] { "*" }
				: filteredFileNamePatterns.toArray(new String[0]);

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

		AtomicBoolean finished = new AtomicBoolean(false);
		AtomicBoolean canceled = new AtomicBoolean(false);
		IQueryListener queryListener = new IQueryListener() {

			@Override
			public void queryStarting(ISearchQuery query) {
			}

			@Override
			public void queryRemoved(ISearchQuery q) {
				if (query.equals(q)) {
					finished.set(true);
					canceled.set(true);
				}
			}

			@Override
			public void queryFinished(ISearchQuery q) {
				if (query.equals(q)) {
					finished.set(true);
				}
			}

			@Override
			public void queryAdded(ISearchQuery query) {
			}
		};
		NewSearchUI.addQueryListener(queryListener);
		List<SearchResultItem> collectedResults = new ArrayList<>();
		Display.getDefault().syncExec(() -> {
			NewSearchUI.runQueryInBackground(query);
		});

		try {
			while (!finished.get()) {
				Thread.sleep(200l);
			}

			// Proceed to collect results even on cancel or with info/warnings, as some
			// results might be available.
			collectMatchesFromSearchResult(query.getSearchResult(), collectedResults);

			String message = "Search completed.";
			if (canceled.get())
				message = "Search was cancelled. " + collectedResults.size() + " results found before cancellation.";
			else
				message = "Search completed successfully. " + collectedResults.size() + " results found.";

			return new SearchExecutionResult(true, message, collectedResults);
		} catch (InterruptedException e) {
			Thread.interrupted();
			return new SearchExecutionResult(false, "Search query execution failed.", null);
		} finally {
			NewSearchUI.removeQueryListener(queryListener);
		}
	}

	private List<String> filter(List<String> fileNamePatterns) {
		List<String> res = fileNamePatterns.stream().map(p -> {
			int lastSlash = p.lastIndexOf("/");
			if (lastSlash >= 0 && lastSlash < p.length() - 1) {
				p = p.substring(lastSlash + 1);
			}
			return p;
		}).collect(Collectors.toList());
		return res;
	}
}

