# Code Intelligence

This Eclipse plugin provides AI code completion.

## Usage

The completion feature is integrated in the JDT completion proposal mechanism.  
So when you invoke it by e.g. pressing *Ctrl + Space*, an AI completion is triggered by default.

![Screenshot: invocation of the plugin](images/example-vanilla.png)

What also works really well is adding a `// TODO` comment above where you want an ai completion.

![Screenshot: invocation of the plugin with a TODO comment](images/example-todo-prompt.png)

## Installation

The update site is hosted on [https://chabicht.github.io/code-intelligence/com.chabicht.code-intelligence.updatesite/](https://chabicht.github.io/code-intelligence/com.chabicht.code-intelligence.updatesite/).

In Eclipse, open *Help* -> *Install New Software...*.  
In the dialog, click on *Add...* and enter the new repository information. For *Location*, use the URL above.  

![Screenshot: Add Repository](images/image.png)

## Configuration

Open the preferences: *Window* -> *Preferences* -> *Code Intelligence*.

![Screenshot: Plugin configuration page](images/image-1.png)

Here you can add connections to different API providers and select the model you want to use for code completion.

### Ollama connection settings

![Screenshot: Ollama configuration](images/ollama.png)

- Use *Type* Ollama.
- For the *Base URI*, use the host and port your Ollama instance is running.  
  Default is `http://localhost:11434`.
- Usually you don't need an API key.

### OpenAI connection settings

![Screenshot: OpenAI configuration](images/openai.png)

- Use *Type* OpenAI.
- For the *Base URI*, use `https://api.openai.com/v1`.
- You have to create an API key in the [OpenAI Platform settings](https://platform.openai.com/settings/organization/api-keys).  
  Note: This doesn't work with a ChatGPT subscription. You need to set up API access.  
  For a tutorial, cf. e.g. [this video on YouTube](https://www.youtube.com/watch?v=OB99E7Y1cMA).

### Groq.com connection settings

![Screenshot: Groq configuration](images/groq.png)

- Use *Type* OpenAI.
- For the *Base URI*, use `https://api.groq.com/openai/v1`.
- You have to create an API key in the [Groq playground](https://console.groq.com/keys).

### Model selection

The model is identified by `[Connection Name]/[Model ID]`, so assuming your OpenAI connection was named *OpenAI*, to use GPT-4o-Mini, you would enter `OpenAI/gpt-4o-mini`.

Clicking on *Change...* opens a dialog where you can select a model from the enabled connections.

### Customizing code completion behaviour

By default the an AI request happens when a code completion is triggered.

You can change that behaviour in the settings under *Java* -> *Editor* -> *Content Assist* -> *Advanced*.

![Screenshot: content assist configuration](images/content-assist.png)

- If *Code Intelligence* is selected in the 'default' section, it is called every time a completion is triggered.
- If *Code Intelligence* is selected in the cycling section, it is triggered when you cycle through the different proposal kinds.
