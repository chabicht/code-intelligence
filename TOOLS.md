# Function Calling / Tool Use

## Overview

Function calling (also referred to as tool use or function invocation) enables language models (LLMs) to execute predefined actions or functions outside of their internal capabilities. Different AI model vendors may refer to this feature using various terms such as **Function Calling**, **Tool Use**, **Plugins**, or **Function Invocation**.

## Supported Tools

The plugin provides access to a set of functions or tools that the LLM can invoke. Each function has clearly defined parameters and expected behavior, allowing the model to interact programmatically with your workspace.

The following tools are currently supported:

### 1. `apply_patch`
- **Description**: Applies a patch to a code file.
- **Parameters**:
    - `file_name` (string, required): The name of the file that is to be changed. If the path is present, the file is identified uniquely. If only a file name is present, a best effort algorithm is applied to guess the correct file. If possible, provide the complete path.
    - `patch_content` (string, required): A patch in unified diff format. Example:
      ```diff
      --- /path/to/File.java
      +++ /path/to/File.java
      @@ -137,5 +137,9 @@
               }
           }

           private static class UpperCaseNode extends CustomNode {
      +        @Override
      +        public void test() {
      +            // Implementation not needed for this test
      +        }
           }

           private static class UpperCaseNodeRendererFactory implements HtmlNodeRendererFactory {
      ```

### 2. `read_file_content`
- **Description**: Reads the content of a specified file, or a specific line range within the file. Returns the content with line numbers prefixed.
- **Parameters**:
    - `file_name` (string, required): The name or path of the file to read. If only a file name is present, a best effort algorithm is applied to guess the correct file. If possible, provide the complete path to the file.
    - `start_line` (integer, optional): The 1-based starting line number of the range to read. If omitted or null, and `end_line` is also omitted or null, the entire file is read. If provided, `end_line` must also be provided or it defaults to `start_line`.
    - `end_line` (integer, optional): The 1-based ending line number of the range to read. If omitted or null, and `start_line` is provided, it defaults to `start_line` (reads a single line). If both `start_line` and `end_line` are omitted or null, the entire file is read.

### 3. `apply_change`
- **Description**: Applies a change to a code file.
The `apply_change` tool allows the model to propose and apply modifications to code files. It includes the following parameters:

- **`file_name`** *(string, required)*: The name of the file to be changed.
- **`location_in_file`** *(string, required)*: The location of the part of the file the change refers to in the format `l[start line]:[end line]`. Line numbers are 1-based, Example: `l123:130` refers to line 123 to 130. Hint: If you're provided with code snippets, they usually come with a line range in this format. This information is evaluated by code, so a textual description or 80% adherence WILL NOT WORK! It is IMPORTANT to adhere to the EXACT FORMAT!
- **`original_text`** *(string, required)*: The original text to replace, including all whitespaces etc. It is IMPORTANT that the original text is IDENTICAL to the text in the code file. It is also important that enough context information is provided that the tool can locate the change within the file.
- **`replacement_text`** *(string, required)*: The new text to replace the original content.
- **Note**: This function queues changes. Applying them typically opens a preview dialog for review.

### 4. `perform_text_search`
- **Description**: Performs a text search in the workspace and returns the matches. The search is performed synchronously and results are collected directly.
- **Parameters**:
    - `search_text` (string, required): The text or pattern to search for. Note that this is NOT capable of regex patterns! Instead of `.*`, you must use `*`! Valid placeholders for a pattern are:
        - `*`: Any character sequence
        - `?`: Any character
        - `\`: Escape for literals '\*, ?, or \'.
    - `file_name_patterns` (array of strings, optional): List of file name patterns (e.g., "*.java", "data*.xml"). If omitted or empty, searches all files in the workspace. Make sure to use this parameter for your calls if at all possible since it can greatly reduce execution time of the tool.
    - `is_case_sensitive` (boolean, optional): True for a case-sensitive search. Defaults to false.
    - `is_whole_word` (boolean, optional): True to match whole words only. Defaults to false.

### 5. `perform_regex_search`
- **Description**: Performs a RegEx search in the workspace and returns the matches. The search is performed synchronously and results are collected directly.
- **Parameters**:
    - `search_pattern` (string, required): The pattern in `java.util.regex.Pattern` syntax to search for.
    - `file_name_patterns` (array of strings, optional): List of file name patterns (e.g., "*.java", "data*.xml"). If omitted or empty, searches all files in the workspace. Make sure to use this parameter for your calls if at all possible since it can greatly reduce execution time of the tool.
    - `is_case_sensitive` (boolean, optional): True for a case-sensitive search. Defaults to false.

### 6. `create_file`
- **Description**: Creates a new file with the specified content. Fails if the file already exists.
- **Parameters**:
    - `file_path` (string, required): The complete path, including the file name, where the new file should be created (e.g., '/project/src/com/example/NewFile.java'). This path should be relative to the workspace or project root.
    - `content` (string, required): The content to be written into the new file. Can be an empty string if an empty file is desired.

### 7. `find_files`
- **Description**: Finds files within the workspace by matching their full, workspace-relative path against a regular expression. This is useful for locating files when the exact name or path is unknown.
- **Parameters**:
    - `file_path_pattern` (string, required): A regular expression (in `java.util.regex.Pattern` syntax) to match against the full workspace-relative path of each file (e.g., `\.xml$`, `[^/]*Service\.java`, `/MyProject/.*Service\.java`, `/MyProject/src/main/java/com/example/.*Service\.java`).
    - `project_names` (array of strings, optional): A list of project names to search within. If omitted or empty, all projects in the workspace will be searched.
    - `is_case_sensitive` (boolean, optional): True for a case-sensitive search. Defaults to false if not provided.

### 8. `list_projects`
- **Description**: Lists all projects in the current workspace, showing their name and whether they are open or closed.
- **Parameters**: None.

---

## Model Integration and Configuration

The plugin supports tool use with various AI model providers, including Gemini, OpenAI, Anthropic, Ollama, and other compatible APIs. The plugin automatically formats the tool definitions for the selected AI model provider.

### Enabling and Managing Tools

To use these tools with your AI model:

1.  Navigate to **Preferences → Code Intelligence**.
2.  **Global Toggle**:
    *   Ensure the "**Enable Tools globally in Chat**" option is checked to allow the use of any tools.
3.  **Manage Specific Tools**:
    *   Click the "**Manage Specific Tools...**" button.
    *   In the dialog that appears, you can check or uncheck individual tools to enable or disable them.
    *   Click **OK** to save your tool preferences.

Once enabled, the plugin will declare the selected tools to the AI model, allowing it to request their execution when appropriate. There is no need to manually add JSON snippets for these standard tools to your model's custom parameters, as the plugin handles their registration.
