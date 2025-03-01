package com.chabicht.code_intelligence.model;

public interface DefaultPrompts {
	static final String INSTRUCT_PROMPT = """
			## General Instructions:
			Complete the code beginning at the <<<cursor>>> position.
			A selection may be present, indicated by <<<selection_start>>> and <<<selection_end>>> markers.
			A completion always starts at the <<<cursor>>> marker, but it may span more than one line.

			### Example 1:
			**Code:**
			```
			public class Main {
			  public static void main(String[] args) {
			    String name = "John";
			    System.ou<<<cursor>>>
			  }
			}
			```
			**Completion:**
			```
			    System.out.println(name);
			```

			### Example 2:
			**Code:**
			```
			var model = configuration.getSelectedModel().orElseThrow();

			HttpClient client = HttpClient.newBuilder()
			                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
			                                  .build();

			String requestBody = getRequestBody(prompt, model);
			HttpRequest request = HttpRequest.newBuil<<<cursor>>>
			logger.info("Sending request to ChatGPT.

			" + requestBody);

			try
			{
			        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

			        if (response.statusCode() != 200)

			```
			**Completion:**
			```
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(model.apiUrl()))
			```

			### Example 3:
			**Code:**
			```
			var model = configuration.getSelectedModel().orElseThrow();

			HttpClient client = HttpClient.newBuilder()
			                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
			                                  .build();

			String requestBody = getRequestBody(prompt, model);
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(model.apiUrl()))
			<<<cursor>>>
			logger.info("Sending request to ChatGPT.

			" + requestBody);

			try
			{
			        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

			        if (response.statusCode() != 200)

			```
			**Completion:**
			```
			.timeout( Duration.ofSeconds( configuration.getRequestTimoutSeconds() ) )
			```


			## Here is a list of the most recent edits made by the user:
			{{#recentEdits}}
			{{.}}

			{{/recentEdits}}

			## Important details:
			- This is Java 17 code.
			- Do not repeat the context in your answer.
			- Include the current line until the <<<cursor>>> marker in your answer.
			- Focus on relevant variables and methods from the context provided.
			- If the context before the current line ends with a comment, implement what the comment intends to do.
			- Use the provided last edits by the user to guess what might be an appropriate completion here.
			- Output only the completion snippet (no extra explanations, no markdown, not the whole program again).
			- If the code can be completed logically in 1-5 lines, do so; otherwise, finalize the snippet where it makes sense.
			- It is important to create short completions.

			## Now do this for this code:
			**Code:**
			```
			{{code}}
			```
			**Completion:**
			""";

	static final String CHAT_SYSTEM_PROMPT = """
			You're an expert Java programmer who helps the user with tasks regarding Java code and/or general programming tasks.
			""";

	static final String CAPTION_PROMPT = """
			Create a short caption, about 3-6 words, for the content below:
			===
			{{content}}
			===

			Important instructions:
			- If in doubt, the question or instruction in the first paragraph is more important than latter (answer) part.
			- Respond with only the caption.
			- No explanations, alternatives, etc.
			- No formatting, just the words.

			Caption:
			""";
}
