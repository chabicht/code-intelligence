package com.chabicht.code_intelligence.chat.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.internal.ui.util.PatternConstructor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.core.text.TextSearchMatchAccess;
import org.eclipse.search.core.text.TextSearchRequestor;
import org.eclipse.search.core.text.TextSearchScope;
import org.eclipse.search.ui.text.TextSearchQueryProvider;

import com.chabicht.code_intelligence.Activator;
import com.chabicht.code_intelligence.util.Log; // Assuming you have a Log utility like in ApplyPatchTool
import com.chabicht.codeintelligence.preferences.PreferenceConstants;

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
    	List<SearchResultItem> items = new ArrayList<>();
        // Use the new createPattern method with regex=false
        Pattern searchPattern = TextSearchEngine.createPattern(
            searchText, 
            isCaseSensitive, 
            isRegEx  // isRegex = false (use wildcards)
        );
        
        // Create scope for all .java and .txt files in workspace
        List<String> filtered = filter(fileNamePatterns);
        String[] filePatternArray= filtered.toArray(new String[0]);
        Pattern compile = PatternConstructor.createPattern(filePatternArray, true, false);
        IResource[] resources = new IResource[] { ResourcesPlugin.getWorkspace().getRoot() };
        TextSearchScope scope = TextSearchScope.newSearchScope(resources, compile, true);

		Map<IFile, IDocument> documentMap = new HashMap<>();
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		int maxFiles = prefs.getInt(PreferenceConstants.MAX_FILES_SEARCH_RESULTS);
		java.util.concurrent.atomic.AtomicBoolean limitReached = new java.util.concurrent.atomic.AtomicBoolean(false);
        TextSearchRequestor requestor = new TextSearchRequestor() {
			@Override
			public boolean acceptFile(IFile file) throws CoreException {
				if (maxFiles >= 0 && items.size() >= maxFiles) {
					limitReached.set(true);
					return false;
				}
				return true;
			}

            @Override
            public boolean acceptPatternMatch(TextSearchMatchAccess m) 
                    throws CoreException {
//                System.out.println("Found in: " + m.getFile().getFullPath() + 
//                    " offset=" + m.getMatchOffset() + " len=" + m.getMatchLength());
				IFile file = m.getFile();
				try {

					int offset = m.getMatchOffset();
					int length = m.getMatchLength();

					IDocument	document = resourceAccess.getDocumentAndConnect(file, documentMap);
					if (document == null) {
						Log.logInfo("Could not get document for file (skipping matches in it): "
								+ file.getFullPath().toString()); // Using Log.logInfo for warnings
		                return false;
					} else {
						try {
							int lineNumber = document.getLineOfOffset(offset) + 1; // 1-based
							IRegion lineInfo = document.getLineInformation(lineNumber - 1);
							String lineContent = document.get(lineInfo.getOffset(), lineInfo.getLength());
							String matchedTextActual = document.get(offset, length);

							items.add(new SearchResultItem(file.getFullPath().toString(), lineNumber,
									lineContent.trim(), // Trim to remove leading/trailing whitespace from the line
									matchedTextActual));
						} catch (BadLocationException e) {
							Log.logError("Error extracting match details from " + file.getFullPath().toString()
									+ " at offset " + offset, e);
						}
					}
				} catch (Exception e) { // From getDocumentAndConnect
					Log.logError("Exception while processing file " + file.getFullPath().toString(), e);
	                return false;
				}
                return true;
            }
        };
        
        TextSearchEngine engine = TextSearchEngine.create();
        SearchExecutionResult result;
        try {
			IStatus search = engine.search(scope, requestor, searchPattern, new NullProgressMonitor());
			if (search.getException() != null) {
				Activator.logError("Error searching", search.getException());
				result = new SearchExecutionResult(false, "Search completed with errors: " + search.getException().getMessage(), items);
			} else {
				String message = "Search completed successfully. " + items.size() + " results found.";
				if (limitReached.get()) {
					message += " (Limit reached, some results may be omitted)";
				}
		        result = new SearchExecutionResult(true, message, items);
			}
		} finally {
			resourceAccess.disconnectAllDocuments(documentMap);
		}
        return result;
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
