package com.chabicht.code_intelligence.model;

public interface DefaultPrompts {
	static final String INSTRUCT_PROMPT = """
			## General Instructions:
			Complete the given code.    
			The completion task is formatted in a "Fill in the Middle" (FIM) format separated by `<|fim_prefix|>`, `<|fim_suffix|>`, and `<|fim_middle|>`:
			- The *prefix* is the text between `<|fim_prefix|>` and `<|fim_suffix|>`.
			- The *suffix* is the text between `<|fim_suffix|>` and `<|fim_middle|>`.
			Your task is to provide a completion that fills in the code missing in the *middle*.

			### Example 1:
			**Code:**
			```
			<|fim_prefix|>public class Main {
			  public static void main(String[] args) {
			    String name = "John";
			    System.ou<|fim_suffix|>
			  }
			}<|fim_middle|>
			```
			**Completion:**
			```
			t.println(name);
			```

			### Example 2:
			**Code:**
			```
			<|fim_prefix|>var model = configuration.getSelectedModel().orElseThrow();

			HttpClient client = HttpClient.newBuilder()
			                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
			                                  .build();

			String requestBody = getRequestBody(prompt, model);
			HttpRequest request = HttpRequest.newBuil<|fim_suffix|>
			logger.info("Sending request to ChatGPT. " + requestBody);

			try
			{
			        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

			        if (response.statusCode() != 200)
			<|fim_middle|>
			```
			**Completion:**
			```
			der().uri(URI.create(model.apiUrl()));
			```

			### Example 3:
			**Code:**
			```
			<|fim_prefix|>var model = configuration.getSelectedModel().orElseThrow();

			HttpClient client = HttpClient.newBuilder()
			                                  .connectTimeout( Duration.ofSeconds(configuration.getConnectionTimoutSeconds()) )
			                                  .build();

			String requestBody = getRequestBody(prompt, model);
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(model.apiUrl()))
			<|fim_suffix|>
			logger.info("Sending request to ChatGPT.

			" + requestBody);

			try
			{
			        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

			        if (response.statusCode() != 200)
			<|fim_middle|>
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
			- Focus on relevant variables and methods from the context provided.
			- If the context before the current line ends with a comment, implement what the comment intends to do.
			- Use the provided last edits by the user to guess what might be an appropriate completion here.
			- Output only the completion snippet (no extra explanations, no markdown, not the whole program again).
			- If the code can be completed logically in 1-5 lines, do so; otherwise, finalize the snippet where it makes sense.
			- It is important to create short completions.

			## Now do this for this code:
			**Code:**
			```
			<|fim_prefix|>{{prefix}}<|fim_suffix|>{{suffix}}<|fim_middle|>
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
