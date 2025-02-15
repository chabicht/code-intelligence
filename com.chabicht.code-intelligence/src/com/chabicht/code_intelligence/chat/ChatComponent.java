package com.chabicht.code_intelligence.chat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

public class ChatComponent extends Composite {
	private Browser bChat;
	private final Set<UUID> knownMessages = new HashSet<>();
	private boolean ready = false;

	public ChatComponent(Composite parent, int style) {
		super(parent, style);
		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		setLayout(gridLayout);

		bChat = new Browser(this, SWT.WEBKIT | SWT.EDGE);
		bChat.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		bChat.setText(CHAT_TEMPLATE);
		bChat.addProgressListener(ProgressListener.completedAdapter(e -> {
			ready = true;
		}));
	}

	public boolean isReady() {
		return ready;
	}

	public void addLocationListener(LocationListener listener) {
		bChat.addLocationListener(listener);
	}

	public void addProgressListener(ProgressListener listener) {
		bChat.addProgressListener(listener);
	}

	public void removeLocationListener(LocationListener listener) {
		bChat.removeLocationListener(listener);
	}

	public void removeProgressListener(ProgressListener listener) {
		bChat.removeProgressListener(listener);
	}

	public void addMessage(UUID messageId, String role, String html) {
		knownMessages.add(messageId);
		bChat.execute(String.format("addMessage('%s', '%s', '%s');", messageId, role, escapeForJavaScript(html)));
	}

	public void updateMessage(UUID messageId, String html) {
		bChat.execute(String.format("updateMessage('%s', '%s');", messageId, escapeForJavaScript(html)));
	}

	public void updateMessage(UUID messageId, String role, String html) {
		bChat.execute(String.format("updateMessage('%s', '%s', '%s');", messageId, role, escapeForJavaScript(html)));
	}

	public boolean isMessageKnown(UUID messageId) {
		return knownMessages.contains(messageId);
	}

	public void reset() {
		bChat.setText(CHAT_TEMPLATE);
		knownMessages.clear();
	}

	public boolean setFocus() {
		return bChat.setFocus();
	}

	public Browser getBrowser() {
		return bChat;
	}

	/**
	 * Escapes a string for safe use in a JavaScript literal.
	 *
	 * @param input the original string to be escaped
	 * @return a string where characters that might break a JS literal are escaped
	 */
	private static String escapeForJavaScript(String input) {
		if (input == null) {
			return "";
		}
		StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			switch (c) {
			case '\\':
				escaped.append("\\\\");
				break;
			case '"':
				escaped.append("\\\"");
				break;
			case '\'':
				escaped.append("\\'");
				break;
			case '`':
				escaped.append("\\`");
				break;
			case '\n':
				escaped.append("\\n");
				break;
			case '\r':
				escaped.append("\\r");
				break;
			case '\t':
				escaped.append("\\t");
				break;
			default:
				escaped.append(c);
			}
		}
		return escaped.toString();
	}

	private String CHAT_TEMPLATE = """
			<!DOCTYPE html>
			<meta http-equiv="X-UA-Compatible" content="IE=edge" />
			<html>
			<head>
			  <style>
				html, body {
				  margin: 0;
				  padding: 0;
				  font-family: "Helvetica", Sans-Serif;
				}

			    /* The main container uses flex layout in column direction */
			    .chat-container {
			      display: flex;
			      flex-direction: column;
			      width: 100%;
			      height: 100%;
			      margin: 0;
			      padding: 10px;
			      box-sizing: border-box;
			      background-color: #ffffff;
				  overflow-y: hidden;
				  overflow-x: auto;
			    }

			    /* General message bubble styling */
			    .message {
			      width: 80vw;       /* The message bubble will be 80% of the viewport width */
			      border: 1px solid #ccc;
			      border-radius: 10px;
			      padding: 10px;
			      margin: 5px 0;
			      box-sizing: border-box;
				  overflow: auto;
			    }

			    /* Message from "system": align bubbles center*/
			    .from-system {
			      width: calc(100vw - 20px);
			      align-self: center;
			      background-color: #ffffff;
			    }

			    /* Message from "me": align bubbles to the right */
			    .from-me {
			      max-width: 800px;  /* Optionally set a max width for larger screens */
			      align-self: flex-start;
			      background-color: #ededed;
			    }

			    /* Message from "them": align bubbles to the left */
			    .from-them {
			      max-width: 800px;  /* Optionally set a max width for larger screens */
			      align-self: flex-end;
			      background-color: #ededed;
			    }

				/* Style for the details element */
				.details {
				  margin: 5px 0;
				}

				/* Style the summary element to look like a clickable label */
				summary {
				  cursor: pointer;
				  font-weight: bold;
				  color: #007acc;
				}

				/* Optional: Style the blockquote to better match your chat theme */
				blockquote {
				  border-left: 2px solid #ccc;
				  margin: 10px 0;
				  padding-left: 10px;
				  background: #ededed;
				}

				/* Container for the attachment icon and tooltip */
				.attachment-container {
				  position: relative; /* Allows the tooltip to be positioned absolutely relative to this container */
				  display: inline-block; /* Keeps the container only as large as its contents */
				  margin-left: 5px; /* Optional spacing from other text */
				}

				/* The attachment icon styling */
				.attachment-icon {
				  font-size: 16px; /* Adjust the size as needed */
				  color: #555;
				  cursor: default; /* No pointer so it just indicates information */
				}

				/* Tooltip styling */
				.attachment-container .tooltip {
				  visibility: hidden;
				  width: 400px; /* Adjust width as needed */
				  background-color: #000;
				  color: #fff;
				  text-align: center;
				  padding: 5px;
				  border-radius: 4px;

				  /* Positioning */
				  position: absolute;
				  z-index: 1;
				  bottom: 125%; /* Position above the icon */
				  left: 50%;
				  margin-left: -20px; /* Half the tooltip width to center it */

				  /* IE 11 supports opacity transition */
				  opacity: 0;
				  filter: alpha(opacity=0); /* For older IE versions if needed */

				  /* Optional arrow at the bottom */
				  /* For IE 11, using border triangle works fine */
				}

				/* Tooltip arrow (using a pseudo-element) */
				.attachment-container .tooltip::after {
				  content: "";
				  position: absolute;
				  top: 100%; /* At the bottom of the tooltip */
				  left: 20px;
				  margin-left: -5px;
				  border-width: 5px;
				  border-style: solid;
				  border-color: #000 transparent transparent transparent;
				}

				/* Show the tooltip when hovering over the container */
				.attachment-container:hover .tooltip {
				  visibility: visible;
				  opacity: 1;
				  filter: alpha(opacity=100);
				}

			  </style>
			</head>
			<body>

			<div id="chat-container" class="chat-container">
			</div>
			<div id='bottom' style='visibility: hidden;'></div>

			<script>
			/**
			 * Adds a new message to the chat container.
			 * @param {String} uuid - Unique identifier for the message.
			 * @param {String} role - The role of the sender ("assistant" or "user").
			 * @param {String} content - The HTML content of the message.
			 */
			function addMessage(uuid, role, content) {
			  var container = document.getElementById("chat-container");
			  if (!container) {
			    return;
			  }
			  var div = document.createElement("div");

			  // Choose the correct CSS class based on the role
			  if (role === "assistant") {
			    div.className = "message from-them";
			  } else if (role === "user") {
			    div.className = "message from-me";
			  } else if (role === "system") {
			    div.className = "message from-system";
			  } else {
			    div.className = "message";
			  }

			  // Set the uuid as the element's id for future reference
			  div.id = uuid;
			  div.innerHTML = content;

			  // If the message is from the user, add the edit icon
			  if (role === "user") {
			    var editSpan = document.createElement("span");
			    editSpan.id = "edit:" + uuid;
			    editSpan.innerHTML = "\uD83D\uDD8A"; // 🖊️ emoji
			    editSpan.style.position = "absolute";
			    editSpan.style.bottom = "5px";
			    editSpan.style.right = "5px";
			    editSpan.style.cursor = "pointer";
			    editSpan.style.fontSize = "26px";
			    editSpan.style.fontWeight = "800";

			    div.style.position = "relative";
			    div.appendChild(editSpan);
			  }

			  container.appendChild(div);

			  var bottom = document.getElementById("bottom");
			  bottom.scrollIntoView(true);
			}

			/**
			 * Updates an existing message's content.
			 * @param {String} uuid - Unique identifier for the message to update.
			 * @param {String} updatedContent - The new HTML content for the message.
			 */
			function updateMessage(uuid, updatedContent) {
			  var message = document.getElementById(uuid);
			  if (message) {
			    message.innerHTML = updatedContent;
			  }

			  var bottom = document.getElementById("bottom");
			  bottom.scrollIntoView(true);
			}
			</script>

			</body>
			</html>
			""";

}
