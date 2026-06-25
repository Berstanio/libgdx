/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.backends.gwt;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.input.NativeInputConfiguration;
import com.badlogic.gdx.input.NativeInputConfiguration.Autocapitalization;
import com.badlogic.gdx.input.NativeInputConfiguration.ContentType;
import com.badlogic.gdx.input.NativeInputConfiguration.NativeInputCloseCallback;
import com.badlogic.gdx.input.NativeInputConfiguration.ReturnKeyType;
import com.badlogic.gdx.input.NativeInputConfiguration.WriteMode;
import com.badlogic.gdx.input.TextInputWrapper;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/** Bundles the machinery backing {@link com.badlogic.gdx.Input#openTextInputField(NativeInputConfiguration)} for the GWT/HTML
 * backend. Unlike the native backends, a "native input field" here is a real HTML {@code <input>}/{@code <textarea>} element
 * overlaid on the WebGL canvas. The browser provides the soft keyboard, IME, autofill, selection and styling; this class maps a
 * {@link NativeInputConfiguration} onto the element, mirrors the in-progress text back via {@link TextInputWrapper} according to
 * the configured {@link WriteMode}, and keeps the field positioned above the on-screen keyboard.
 * <p>
 * Created lazily and reused across sessions by {@link DefaultGwtInput}: the DOM element is built per {@link #open} and removed on
 * {@link #close}. GWT is single threaded, so everything runs on the main (JS) thread - the
 * {@link TextInputWrapper#writeResults(String, int, int)} contract of "always on the main thread" holds trivially.
 * <p>
 * The object passed to {@link NativeInputConfiguration.NativeInputFieldCustomizer} is this {@code GwtNativeInput}; cast to it
 * from a GWT launcher to tweak {@link #getElement()} beyond what the configuration exposes.
 * <p>
 * Keyboard-height tracking and repositioning the field above the keyboard are driven externally by
 * {@link GwtKeyboardHeightProvider} via {@link DefaultGwtInput#onKeyboardMetricsChanged(boolean, int)}, which calls
 * {@link #reposition()} - this class only owns the field itself. */
public class GwtNativeInput {
	private static int idCounter = 0;

	private final CanvasElement canvas;

	private NativeInputConfiguration configuration;
	private boolean open;

	private Element container;
	private Element element;
	private Element styleElement;
	private Element unmaskButton;

	private boolean masked;

	public GwtNativeInput (CanvasElement canvas) {
		this.canvas = canvas;
		attachSelectionListener();
	}

	public boolean isOpen () {
		return open;
	}

	/** The current HTML {@code <input>}/{@code <textarea>} element, or null while closed. */
	public Element getElement () {
		return element;
	}

	public NativeInputConfiguration getConfiguration () {
		return configuration;
	}

	/** Builds, configures, shows and focuses the field for the given configuration. */
	public void open (NativeInputConfiguration configuration) {
		if (isOpen()) return;
		this.configuration = configuration;
		this.masked = configuration.isMaskInput() || configuration.getType() == Input.OnscreenKeyboardType.Password;

		buildField(configuration);

		TextInputWrapper wrapper = configuration.getTextInputWrapper();
		String initial = wrapper.getText();
		setElementValue(element, initial);
		setSelectionRange(element, wrapper.getSelectionStart(), wrapper.getSelectionEnd());

		// Customizations win over the defaults, applied after libGDX configured the field (mirrors the other backends).
		if (configuration.getFieldCustomizer() != null) configuration.getFieldCustomizer().customize(this);

		open = true;
		reposition();
		// Focus must happen synchronously inside the user gesture that triggered openTextInputField, otherwise mobile browsers
		// won't raise the soft keyboard. DefaultGwtInput dispatches input events synchronously, so we are in that context.
		focusElement(element);
	}

	/** Writes back the final results, runs the close callbacks and tears down the field. */
	public void close (boolean isConfirmative, NativeInputCloseCallback callback) {
		if (!isOpen()) return;

		String text = getElementValue(element);
		int selectionStart = getSelectionStart(element);
		int selectionEnd = getSelectionEnd(element);
		TextInputWrapper wrapper = configuration.getTextInputWrapper();
		NativeInputConfiguration config = configuration;

		// Mark closed and detach references first, so a close callback may immediately reopen (e.g. ReturnKeyType.NEXT chaining
		// through multiple fields) without tripping the "already open" guard. The old DOM is removed afterwards.
		Element oldContainer = container;
		Element oldStyle = styleElement;
		open = false;
		container = null;
		element = null;
		styleElement = null;
		unmaskButton = null;
		configuration = null;

		wrapper.writeResults(text, selectionStart, selectionEnd);

		// The callbacks may reopen synchronously (e.g. advancing fields). Unlike the native backends we can't hold a soft
		// keyboard open without a focused field, so the keepOpen return value is moot here: a reopen re-focuses, otherwise the
		// teardown below blurs and the keyboard hides.
		config.getCloseCallback().onClose(isConfirmative);
		if (callback != null) callback.onClose(isConfirmative);

		// Removing the old element blurs it (hiding the keyboard) when nothing reopened. If a callback reopened, focus is
		// already on the new element, so removing the old one leaves the keyboard up.
		if (oldContainer != null) oldContainer.removeFromParent();
		if (oldStyle != null) oldStyle.removeFromParent();
	}

	private void buildField (NativeInputConfiguration config) {
		Document doc = Document.get();
		String id = "gdx-native-input-" + (idCounter++);

		container = doc.createElement("div");
		setStyle(container, "position", "fixed");
		setStyle(container, "box-sizing", "border-box");
		setStyle(container, "display", "flex");
		setStyle(container, "gap", "6px");
		setStyle(container, "align-items", "stretch");
		setStyle(container, "z-index", "2147483000");

		boolean multiLine = config.isMultiLine();
		element = doc.createElement(multiLine ? "textarea" : "input");
		element.setId(id);
		if (!multiLine) element.setAttribute("type", masked ? "password" : "text");

		applyKeyboardType(element, config);
		element.setAttribute("placeholder", config.getPlaceholder());
		if (config.getMaxLength() != -1) element.setAttribute("maxlength", String.valueOf(config.getMaxLength()));
		element.setAttribute("autocapitalize", autocapitalizeFor(config.getAutocapitalization()));
		element.setAttribute("autocorrect", config.isPreventCorrection() ? "off" : "on");
		element.setAttribute("spellcheck", config.isPreventCorrection() ? "false" : "true");
		if (config.getContentType() != null)
			element.setAttribute("autocomplete", autocompleteFor(config.getContentType()));
		else if (config.isPreventCorrection()) element.setAttribute("autocomplete", "off");
		if (!multiLine) element.setAttribute("enterkeyhint", enterKeyHintFor(config.getReturnKeyType()));

		setStyle(element, "box-sizing", "border-box");
		setStyle(element, "font-size", "16px"); // >=16px avoids the focus-zoom on iOS Safari
		setStyle(element, "padding", "8px " + px(config.getTextMargin()));
		setStyle(element, "border", "1px solid rgba(0,0,0,0.2)");
		setStyle(element, "outline", "none");
		setStyle(element, "border-radius", px(config.getCornerRadius()));
		setStyle(element, "background-color", cssColor(config.getBackgroundColor()));
		setStyle(element, "color", cssColor(config.getTextColor()));
		setStyle(element, "flex", "1 1 auto");
		setStyle(element, "min-width", "0");
		if (multiLine) {
			element.setAttribute("rows", "3");
			setStyle(element, "resize", "none");
		}

		// Placeholder color can only be set through the ::placeholder pseudo-element, which inline styles can't target.
		styleElement = doc.createElement("style");
		styleElement.setInnerText("#" + id + "::placeholder{color:" + cssColor(config.getPlaceholderColor()) + ";}");

		attachInputListeners(element);
		container.appendChild(element);

		// Autocomplete suggestions map to a <datalist>. validate() already forbids combining this with multiline.
		// autoCompleteThreshold has no native <datalist> equivalent and is ignored (best-effort).
		if (config.getAutoComplete() != null && !multiLine) {
			Element dataList = doc.createElement("datalist");
			String listId = id + "-list";
			dataList.setId(listId);
			for (String option : config.getAutoComplete()) {
				Element optionElement = doc.createElement("option");
				optionElement.setAttribute("value", option);
				dataList.appendChild(optionElement);
			}
			container.appendChild(dataList);
			element.setAttribute("list", listId);
		}

		if (masked && config.isShowUnmaskButton() && !multiLine) {
			unmaskButton = doc.createElement("button");
			unmaskButton.setAttribute("type", "button");
			unmaskButton.setInnerText("Show");
			setStyle(unmaskButton, "flex", "0 0 auto");
			setStyle(unmaskButton, "box-sizing", "border-box");
			setStyle(unmaskButton, "border", "1px solid rgba(0,0,0,0.2)");
			setStyle(unmaskButton, "border-radius", px(config.getCornerRadius()));
			setStyle(unmaskButton, "background-color", cssColor(config.getBackgroundColor()));
			setStyle(unmaskButton, "color", cssColor(config.getTextColor()));
			attachButtonListeners(unmaskButton);
			container.appendChild(unmaskButton);
		}

		doc.getBody().appendChild(styleElement);
		doc.getBody().appendChild(container);
	}

	/** Lays the field over the canvas, above the on-screen keyboard. Called on open and then driven by
	 * {@link GwtKeyboardHeightProvider} (via {@link DefaultGwtInput}) whenever the keyboard reshapes. */
	void reposition () {
		if (!open || container == null) return;
		positionContainer(container, canvas, configuration.getHorizontalInsetFraction());
	}

	/** Current on-screen height of the field in CSS px, or 0 while closed. Reported by {@link DefaultGwtInput} as part of the
	 * observed keyboard height while a field is open (matching the native backends). */
	int getContainerHeight () {
		return container == null ? 0 : elementHeight(container);
	}

	/** Mirrors the in-progress text/caret back to the wrapper, gated by the configured {@link WriteMode}. */
	private void notifyChanged (boolean selectionOnly) {
		if (!open || element == null) return;
		WriteMode writeMode = configuration.getWriteMode();
		if (writeMode == WriteMode.ONLY_FINAL) return;
		if (selectionOnly && writeMode != WriteMode.ALL_UPDATES) return;
		configuration.getTextInputWrapper().writeResults(getElementValue(element), getSelectionStart(element),
			getSelectionEnd(element));
	}

	// --- called from JSNI listeners ---

	/** Validates the text about to be inserted (the delta), mirroring the native backends which feed the validator only the newly
	 * inserted characters - never the whole field, so deletions are never blocked. Returns true to cancel the edit.
	 * @param data the text being inserted, or null for deletions/formatting/etc. (which are always allowed) */
	private boolean onBeforeInput (String data) {
		if (!open || element == null || data == null) return false;
		Input.InputStringValidator validator = configuration.getValidator();
		return validator != null && !validator.validate(data);
	}

	private void onInput () {
		if (!open || element == null) return;
		notifyChanged(false);
	}

	private boolean onKeyDown (String key) {
		if (key == null) return false;
		if (key.equals("Enter")) {
			if (configuration.isMultiLine()) return false; // let the newline through
			close(true, null);
			return true;
		}
		if (key.equals("Escape")) {
			close(false, null);
			return true;
		}
		return false;
	}

	private void onBlur (Element blurred) {
		// Ignore blur events from a stale element (e.g. the old field being torn down after a reopen).
		if (!open || blurred != element) return;
		close(false, null);
	}

	private void onSelectionChange () {
		if (!open || element == null || !isActiveElement(element)) return;
		notifyChanged(true);
	}

	private void onUnmaskToggle () {
		if (!open || element == null) return;
		masked = !masked;
		element.setAttribute("type", masked ? "password" : "text");
		if (unmaskButton != null) unmaskButton.setInnerText(masked ? "Show" : "Hide");
		focusElement(element);
	}

	// --- configuration mapping helpers ---

	private void applyKeyboardType (Element element, NativeInputConfiguration config) {
		String inputMode = inputModeFor(config.getType());
		if (inputMode != null) element.setAttribute("inputmode", inputMode);
	}

	private static String inputModeFor (Input.OnscreenKeyboardType type) {
		switch (type) {
		case NumberPad:
			return "numeric";
		case PhonePad:
			return "tel";
		case Email:
			return "email";
		case URI:
			return "url";
		case Password:
		case Default:
		default:
			return null;
		}
	}

	private static String enterKeyHintFor (ReturnKeyType type) {
		switch (type) {
		case GO:
			return "go";
		case SEARCH:
			return "search";
		case SEND:
			return "send";
		case NEXT:
			return "next";
		case DONE:
		default:
			return "done";
		}
	}

	private static String autocapitalizeFor (Autocapitalization autocapitalization) {
		switch (autocapitalization) {
		case WORDS:
			return "words";
		case SENTENCES:
			return "sentences";
		case CHARACTERS:
			return "characters";
		case NONE:
		default:
			return "none";
		}
	}

	private static String autocompleteFor (ContentType contentType) {
		switch (contentType) {
		case USERNAME:
			return "username";
		case PASSWORD:
			return "current-password";
		case NEW_PASSWORD:
			return "new-password";
		case ONE_TIME_CODE:
			return "one-time-code";
		case EMAIL:
			return "email";
		case PHONE:
			return "tel";
		default:
			return "on";
		}
	}

	private static String px (float value) {
		return Math.round(value) + "px";
	}

	private static String cssColor (Color color) {
		int r = (int)(color.r * 255 + 0.5f);
		int g = (int)(color.g * 255 + 0.5f);
		int b = (int)(color.b * 255 + 0.5f);
		return "rgba(" + r + "," + g + "," + b + "," + color.a + ")";
	}

	// --- JSNI: DOM access not exposed by gwt-dom (no elemental2 on the classpath) ---

	private native void setStyle (Element el, String property, String value) /*-{
		el.style.setProperty(property, value);
	}-*/;

	private native void focusElement (Element el) /*-{
		el.focus();
	}-*/;

	private native String getElementValue (Element el) /*-{
		return el.value == null ? "" : el.value;
	}-*/;

	private native void setElementValue (Element el, String value) /*-{
		el.value = value;
	}-*/;

	private native int getSelectionStart (Element el) /*-{
		return el.selectionStart == null ? el.value.length : el.selectionStart;
	}-*/;

	private native int getSelectionEnd (Element el) /*-{
		return el.selectionEnd == null ? el.value.length : el.selectionEnd;
	}-*/;

	private native void setSelectionRange (Element el, int start, int end) /*-{
		try {
			el.setSelectionRange(start, end);
		} catch (ignored) {
		}
	}-*/;

	private native boolean isActiveElement (Element el) /*-{
		return $doc.activeElement === el;
	}-*/;

	private native void attachInputListeners (Element el) /*-{
		var self = this;
		// Validate the delta before it is applied (mirrors Android/iOS). preventDefault rejects the insertion; deletions
		// (ev.data == null) are never validated, so existing text is always deletable. beforeinput for IME composition is not
		// cancelable in all browsers, so composition validation is best-effort.
		el.addEventListener("beforeinput", function(ev) {
			var prevent = self.@com.badlogic.gdx.backends.gwt.GwtNativeInput::onBeforeInput(Ljava/lang/String;)(ev.data);
			if (prevent) ev.preventDefault();
		});
		el.addEventListener("input", function(ev) {
			self.@com.badlogic.gdx.backends.gwt.GwtNativeInput::onInput()();
		});
		el.addEventListener("keydown", function(ev) {
			// Don't treat keys committing an IME composition as field actions.
			if (ev.isComposing || ev.keyCode === 229) return;
			var handled = self.@com.badlogic.gdx.backends.gwt.GwtNativeInput::onKeyDown(Ljava/lang/String;)(ev.key);
			if (handled) ev.preventDefault();
		});
		el.addEventListener("blur", function(ev) {
			self.@com.badlogic.gdx.backends.gwt.GwtNativeInput::onBlur(Lcom/google/gwt/dom/client/Element;)(el);
		});
	}-*/;

	private native void attachButtonListeners (Element btn) /*-{
		var self = this;
		// Keep focus on the input so the keyboard doesn't dismiss when toggling.
		btn.addEventListener("mousedown", function(ev) {
			ev.preventDefault();
		});
		btn.addEventListener("touchstart", function(ev) {
			ev.preventDefault();
		});
		btn.addEventListener("click", function(ev) {
			self.@com.badlogic.gdx.backends.gwt.GwtNativeInput::onUnmaskToggle()();
		});
	}-*/;

	/** The document-level selectionchange listener drives caret-only mirroring (WriteMode.ALL_UPDATES). Keyboard-height tracking
	 * and field repositioning are driven externally by {@link GwtKeyboardHeightProvider}. */
	private native void attachSelectionListener () /*-{
		var self = this;
		$doc.addEventListener("selectionchange", function(ev) {
			self.@com.badlogic.gdx.backends.gwt.GwtNativeInput::onSelectionChange()();
		});
	}-*/;

	/** Lays the container over the canvas (horizontally, inset per side) with its bottom edge above the on-screen keyboard. */
	private native void positionContainer (Element container, Element canvasEl, double insetFraction) /*-{
		var rect = canvasEl.getBoundingClientRect();
		var vv = $wnd.visualViewport;
		var vvLeft = vv ? vv.offsetLeft : 0;
		var vvTop = vv ? vv.offsetTop : 0;
		var vvWidth = vv ? vv.width : $wnd.innerWidth;
		var vvHeight = vv ? vv.height : $wnd.innerHeight;

		var inset = rect.width * insetFraction;
		var left = Math.max(rect.left, vvLeft) + inset;
		var right = Math.min(rect.right, vvLeft + vvWidth) - inset;
		var width = right - left;
		if (width < 0) width = 0;
		container.style.left = left + "px";
		container.style.width = width + "px";

		var fieldHeight = container.offsetHeight;
		var keyboardTop = vvTop + vvHeight;
		var bottomLine = Math.min(keyboardTop, rect.bottom);
		var top = bottomLine - fieldHeight;
		if (top < vvTop) top = vvTop;
		container.style.top = top + "px";
	}-*/;

	private native int elementHeight (Element el) /*-{
		return el.offsetHeight;
	}-*/;
}
