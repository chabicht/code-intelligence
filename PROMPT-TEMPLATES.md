# Prompt Templates

There are basically two use cases for custom prompts:
- Customized completion instructions, e.g. to optimize the prompt for a specific model or prompting technique like Fill-in-the-Middle (FIM).
- Custom system prompts for the chat.

Prompts are parsed using the [jmustache](https://github.com/samskivert/jmustache) library, so they use [Mustache](http://mustache.github.io/mustache.5.html) syntax.

## Instruction Prompts (Completions)

Instruction prompts are used to generate code completions. The template is currently given the following parameters:

-   `prefix`: The code in the document *before* the cursor position, up to a certain context window limit.
-   `suffix`: The code in the document *after* the end of the current selection (or after the cursor position if there is no selection), up to a certain context window limit.
-   `selection`: The text currently selected by the user. This will be an empty string if no text is selected.
-   `contextWithTags`: A larger snippet of code around the cursor position. Selection and cursor position are marked by special tags `<<<cursor>>>`, `<<<selection_start>>>`, and `<<<selection_end>>>`. This provides broader context than just `prefix` and `suffix`.
-   `recentEdits`: A list of the most recent changes the user made in the workspace, formatted as code blocks.

You can use these parameters to structure the prompt according to the target model's requirements.

### Example 1: Fill-in-the-Middle (FIM) Prompt

Many modern code generation models are trained with a specific Fill-in-the-Middle (FIM) format. This usually involves special tokens to denote the prefix, suffix, and the point where the model should insert the completion. The exact tokens (`<fim_prefix>`, `<fim_suffix>`, `<fim_middle>` or `<PRE>`, `<SUF>`, `<MID>`, etc.) depend on the model being used.

A template for a model expecting `<fim_prefix>`, `<fim_suffix>`, `<fim_middle>` tokens might look like this:

``````mustache
<fim_prefix>{{prefix}}<fim_suffix>{{suffix}}<fim_middle>
``````

*Note: If there is a selection, the `prefix` ends at the cursor, and the `suffix` starts after the selection. The model is expected to generate code that effectively replaces the selection.*

### Example 2: Instruction-Based Prompt with Context

Alternatively, you can use a more traditional instruction-based prompt, providing the context and explicitly asking the model to complete the code. The `contextWithTags` variable is useful here.

``````mustache
Complete the code starting at the <<<cursor>>> position within the following context.
If text is selected (between <<<selection_start>>> and <<<selection_end>>>), replace the selection.
Only provide the code completion itself, without any explanation or surrounding text.

Context:
```
{{contextWithTags}}
```

The user recently made these edits:
{{#recentEdits}}
```
{{.}}
```
{{/recentEdits}}

Completion:
``````

### Example 3: Combining FIM with Recent Edits

You might want to provide recent edits as additional context even when using FIM format, if the model supports it.

``````
Here are recent changes made by the user:
{{#recentEdits}}
```
{{.}}
```
{{/recentEdits}}

Complete the code based on the following prefix and suffix:
<PRE>{{prefix}}<SUF>{{suffix}}<MID>
``````

Choose the prompt structure that works best for the specific AI model you are interacting with via the API. The FIM style (`prefix`/`suffix`) is often preferred for models specifically trained for code completion, while the instruction style (`contextWithTags`) might be more suitable for general-purpose instruction-following models.
