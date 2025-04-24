# Function Calling / Tool Use

## Overview

Function calling (also referred to as tool use or function invocation) enables language models (LLMs) to execute predefined actions or functions outside of their internal capabilities. Different AI model vendors may refer to this feature using various terms such as **Function Calling**, **Tool Use**, **Plugins**, or **Function Invocation**.

## Plugin Capabilities

The plugin provides access to a set of functions or tools that the LLM can invoke. Each function has clearly defined parameters and expected behavior, allowing the model to interact programmatically with external systems.

Currently, the plugin supports the following function:

### `apply_change`

The `apply_change` tool allows the model to propose and apply modifications to code files. It includes the following parameters:

- **`file_name`** *(string, required)*: The name of the file to be changed.
- **`location_in_file`** *(string, required)*: Specifies the location in the file using the format `l[start line]:[end line]`. Lines are 1-based. For example, `l123:130` refers to lines 123 to 130. Typically, provided code snippets come with this line range format.
- **`original_text`** *(string, required)*: The exact text from the file to be replaced. It must exactly match the text in the file, including whitespaces, ensuring sufficient context for accurate identification.
- **`replacement_text`** *(string, required)*: The new text to replace the original content.

This function queues the changes, and applying them typically opens a preview dialog for review.

---

## Gemini Model Integration

Currently, function calling via this plugin is only supported for **Gemini** models.

To enable this functionality, users must add the following JSON snippet to their Gemini chat model settings under **Preferences → Code Intelligence → Custom params**:

```json
{
    "tools": [
      {
        "functionDeclarations": [
          {
            "name": "apply_change",
            "description": "Applies a change to a code file.",
            "parameters": {
              "type": "object",
              "properties": {
                "file_name": {
                  "type": "string",
                  "description": "The name of the file that is to be changed."
                },
                "location_in_file": {
                  "type": "string",
                  "description": "The location of the part of the file the change refers to in the format `l[start line]:[end line]`. Line numbers are 1-based, Example: `l123:130` refers to line 123 to 130. Hint: If you're provided with code snippets, they usually come with a line range in this format."
                },
                "original_text": {
                  "type": "string",
                  "description": "The original text to replace, including all whitespaces etc. It is IMPORTANT that the original text is IDENTICAL to the text in the code file. It is also important that enough context information is provided that the tool can locate the change within the file."
                },
                "replacement_text": {
                  "type": "string",
                  "description": "The changed text replacing the original text."
                }
              },
              "required": ["file_name", "location_in_file", "original_text", "replacement_text"]
            }
          }
        ]
      }
    ]
}
```

### Steps to Add the JSON Snippet:

1. Open the Gemini Chat model in your IDE.
2. Navigate to **Preferences → Code Intelligence**.
3. Locate the **Custom params** section.
4. Select the API connection for the Gemini models, type `CHAT`, and then paste the provided JSON snippet into the Custom params input.
5. Save your changes.

After performing these steps, the `apply_change` tool will become available for use with Gemini models.