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

package com.badlogic.gdx.backends.iosmoe.keyboard;

import apple.NSObject;
import apple.corefoundation.struct.CGRect;
import apple.foundation.NSDictionary;
import apple.foundation.NSNotification;
import apple.foundation.NSNotificationCenter;
import apple.foundation.NSNumber;
import apple.foundation.NSString;
import apple.foundation.NSValue;
import apple.uikit.UIApplication;
import apple.uikit.UIView;
import apple.uikit.c.UIKit;
import apple.uikit.enums.UIInterfaceOrientation;
import com.badlogic.gdx.Gdx;
import org.moe.natj.general.NatJ;
import org.moe.natj.general.Pointer;
import org.moe.natj.objc.SEL;
import org.moe.natj.objc.ann.Selector;

/** Pushes keyboard show/hide events to an {@link IOSKeyboardObserver}. Owns the notification registration, the notification
 * parsing and the keyboard-matched animation: the observer is invoked one frame after the notification (via postRunnable) inside
 * a UIView animation block using the keyboard's own duration and curve, so any UIView property writes the observer performs
 * animate alongside the keyboard. */
public class IOSKeyboardHeightProvider extends NSObject implements KeyboardHeightProvider {

	static {
		NatJ.register();
	}

	@Selector("alloc")
	public static native IOSKeyboardHeightProvider alloc ();

	@Selector("init")
	public native IOSKeyboardHeightProvider init ();

	protected IOSKeyboardHeightProvider (Pointer peer) {
		super(peer);
	}

	private IOSKeyboardObserver observer;
	private boolean started;

	/** The cached height (screen points) the keyboard last had in landscape orientation */
	private double keyboardLandscapeHeight;
	/** The cached height (screen points) the keyboard last had in portrait orientation */
	private double keyboardPortraitHeight;

	/** The cached opened state of the keyboard */
	private boolean cachedOpened;
	/** The cached height of the keyboard */
	private double cachedHeight;
	/** The cached interface orientation of the app */
	private long cachedOrientation;

	/** Starts observing keyboard notifications. Idempotent. */
	public void start () {
		if (started) return;
		started = true;
		// We do this, to not dispatch changes that are not changes on first run
		cachedOrientation = getInterfaceOrientation();
		NSNotificationCenter.defaultCenter().addObserverSelectorNameObject(this, new SEL("keyboardWillShow"),
			UIKit.UIKeyboardWillShowNotification(), null);
		NSNotificationCenter.defaultCenter().addObserverSelectorNameObject(this, new SEL("keyboardWillHide"),
			UIKit.UIKeyboardWillHideNotification(), null);
	}

	/** Stops observing keyboard notifications. The default application never calls this; it exists for API symmetry with the
	 * Android backend and custom lifecycles. */
	public void close () {
		if (!started) return;
		started = false;
		NSNotificationCenter.defaultCenter().removeObserver(this);
	}

	public void setKeyboardHeightObserver (IOSKeyboardObserver observer) {
		this.observer = observer;
	}

	@Selector("keyboardWillShow")
	public void keyboardWillShow (NSNotification notification) {
		NSDictionary<NSString, ?> userInfo = (NSDictionary<NSString, ?>)notification.userInfo();
		CGRect keyboardFrameEnd = ((NSValue)userInfo.get(UIKit.UIKeyboardFrameEndUserInfoKey())).CGRectValue();
		double height = keyboardFrameEnd.size().height();

		long orientation = getInterfaceOrientation();
		if (orientation == UIInterfaceOrientation.LandscapeLeft || orientation == UIInterfaceOrientation.LandscapeRight) {
			keyboardLandscapeHeight = height;
		} else {
			keyboardPortraitHeight = height;
		}

		dispatch(notification, true, height);
	}

	@Selector("keyboardWillHide")
	public void keyboardWillHide (NSNotification notification) {
		dispatch(notification, false, 0);
	}

	/** @return the cached height (screen points) the keyboard last had in landscape orientation */
	public double getKeyboardLandscapeHeight () {
		return keyboardLandscapeHeight;
	}

	/** @return the cached height (screen points) the keyboard last had in portrait orientation */
	public double getKeyboardPortraitHeight () {
		return keyboardPortraitHeight;
	}

	private long getInterfaceOrientation () {
		return UIApplication.sharedApplication().statusBarOrientation();
	}

	protected void dispatch (NSNotification notification, boolean opened, double height) {
		// Don't dispatch what isn't a change. iOS re-fires keyboard notifications liberally (focus churn, repeated
		// hides); orientation is part of the key so a rotation with an unchanged keyboard height still goes through
		// (the field layout depends on it)
		long orientation = getInterfaceOrientation();
		if (opened == cachedOpened && height == cachedHeight && orientation == cachedOrientation) return;
		cachedOpened = opened;
		cachedHeight = height;
		cachedOrientation = orientation;

		NSDictionary<NSString, ?> userInfo = (NSDictionary<NSString, ?>)notification.userInfo();
		double duration = ((NSNumber)userInfo.get(UIKit.UIKeyboardAnimationDurationUserInfoKey())).doubleValue();
		long curve = ((NSNumber)userInfo.get(UIKit.UIKeyboardAnimationCurveUserInfoKey())).longLongValue();

		Gdx.app.postRunnable( () -> {
			if (observer == null) return;
			// The keyboard reports a private animation curve (7) that UIViewAnimationCurve can't represent: shifting the
			// raw value into the curve bits of UIViewAnimationOptions passes it through to UIKit unchanged
			UIView.animateWithDurationDelayOptionsAnimationsCompletion(duration, 0, curve << 16,
				() -> observer.onKeyboardHeightChanged(opened, height), null);
		});
	}
}
