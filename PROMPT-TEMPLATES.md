# Prompt Templates
There are basically two use cases for custom prompts:
- Customized completion instructions, e.g. to optimize the prompt for a certain model.
- Custom system prompts for the chat.

Prompts are parsed using the [jmustache](https://github.com/samskivert/jmustache) library, so they use [Mustache](http://mustache.github.io/mustache.5.html) syntax.

## Instructions Prompts
Instruction prompts are used to generate code completions. The template is currently given two parameters:
- `code`: The code snippet around the cursor position where a completion is invoked. Selection and cursor position are marked by special tags `<<<cursor>>>`, `<<<selection_start>>>`, and `<<<selection_end>>>`.
- `recentEdits`: A list of the most recent changes the user made in the workspace.  

A minimal template could look like this:
````
Complete the code beginning at the <<<cursor>>> position.
A selection may be present, indicated by <<<selection_start>>> and <<<selection_end>>> markers.
A completion always starts at the <<<cursor>>> marker, but it may span more than one line.

The most recent edits the user made were:
{{#recentEdits}}
{{.}}

{{/recentEdits}}

Code:
```
{{code}}
```

Completion:
````