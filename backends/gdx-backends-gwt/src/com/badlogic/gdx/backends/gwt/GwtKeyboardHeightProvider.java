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

/** Observes the browser's on-screen keyboard and pushes height/visibility changes into {@link DefaultGwtInput}, which then drives
 * the native input field layout and the public {@link com.badlogic.gdx.Input.KeyboardHeightObserver}. This mirrors the role the
 * keyboard height providers play on the Android/iOS backends: it is the central, push-based driver, created once with the input
 * and active regardless of whether {@link com.badlogic.gdx.Input#openTextInputField} is in use.
 * <p>
 * Browsers expose no real "keyboard shown/hidden" event, so presence is inferred from the {@code window.visualViewport}
 * shrinking. This is necessarily best-effort: a height below {@link #KEYBOARD_VISIBLE_THRESHOLD} (e.g. an address bar collapsing)
 * is not treated as a keyboard, and on browsers without the VisualViewport API no keyboard is ever reported. On desktop the
 * visual viewport tracks the window, so the keyboard is reported as permanently hidden. */
public class GwtKeyboardHeightProvider {
	/** Minimum visual-viewport reduction in CSS px that counts as the soft keyboard rather than browser chrome (address bar). */
	private static final int KEYBOARD_VISIBLE_THRESHOLD = 150;

	private final DefaultGwtInput input;

	public GwtKeyboardHeightProvider (DefaultGwtInput input) {
		this.input = input;
		attachListeners();
	}

	/** Called from JSNI whenever the visual viewport (or window) resizes or scrolls. */
	private void onViewportChanged () {
		int height = computeKeyboardHeight();
		boolean visible = height > KEYBOARD_VISIBLE_THRESHOLD;
		input.onKeyboardMetricsChanged(visible, height);
	}

	private native void attachListeners () /*-{
		var self = this;
		var changed = function(ev) {
			self.@com.badlogic.gdx.backends.gwt.GwtKeyboardHeightProvider::onViewportChanged()();
		};
		var vv = $wnd.visualViewport;
		if (vv) {
			vv.addEventListener("resize", changed);
			vv.addEventListener("scroll", changed);
		}
		$wnd.addEventListener("resize", changed);
	}-*/;

	private native int computeKeyboardHeight () /*-{
		var vv = $wnd.visualViewport;
		if (!vv) return 0;
		var height = $wnd.innerHeight - vv.height - vv.offsetTop;
		return height > 0 ? Math.round(height) : 0;
	}-*/;
}
