
package com.badlogic.gdx.backends.iosmoe;

import apple.foundation.NSSet;
import apple.uikit.UIKey;
import apple.uikit.UITouch;
import apple.uikit.UIView;
import com.badlogic.gdx.Input;

import com.badlogic.gdx.backends.iosmoe.keyboard.IOSKeyboardHeightProvider;
import com.badlogic.gdx.input.NativeInputConfiguration;

public interface IOSInput extends Input, IOSKeyboardHeightProvider.IOSKeyboardObserver {

	/** Initializes peripherals (such as compass or accelerometer) */
	void setupPeripherals ();

	/** Records touch events */
	void onTouch (NSSet<? extends UITouch> touches);

	/** Process all touch events that have been registered on #onTouch(). */
	void processEvents ();

	boolean onKey (UIKey key, boolean down);

	/** Returns the current active input text field */
	UIView getActiveKeyboardTextField ();

	/** Returns the {@link NativeInputConfiguration} if open, null otherwise. */
	NativeInputConfiguration getNativeInputConfiguration ();

	/** Notifies the input that the screen bounds changed (rotation/resize). */
	void onScreenLayoutChanged ();
}
