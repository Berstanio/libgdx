
package com.badlogic.gdx.backends.iosmoe;

import apple.corefoundation.struct.CGPoint;
import apple.corefoundation.struct.CGRect;
import apple.corefoundation.struct.CGSize;
import apple.foundation.NSDictionary;
import apple.foundation.NSNotification;
import apple.foundation.NSNotificationCenter;
import apple.foundation.NSNumber;
import apple.foundation.NSValue;
import apple.uikit.UITextField;
import apple.uikit.UITextView;
import apple.uikit.UIView;
import apple.uikit.c.UIKit;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.bindings.metalangle.MGLKViewController;
import org.moe.natj.general.NatJ;
import org.moe.natj.general.Pointer;
import org.moe.natj.objc.SEL;
import org.moe.natj.objc.ann.Selector;
import apple.uikit.UIScreen;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import apple.foundation.NSSet;
import apple.uikit.UIDevice;
import apple.uikit.enums.UIInterfaceOrientation;
import apple.uikit.UIPress;
import apple.uikit.UIPressesEvent;
import apple.uikit.enums.UIUserInterfaceIdiom;

public class IOSUIViewController extends MGLKViewController {
	private IOSApplication app;
	private IOSGraphics graphics;

	static {
		NatJ.register();
	}

	@Selector("alloc")
	public static native IOSUIViewController alloc ();

	@Selector("init")
	public native IOSUIViewController init ();

	protected IOSUIViewController (Pointer peer) {
		super(peer);
	}

	public IOSUIViewController init (IOSApplication app, IOSGraphics graphics) {
		init();
		this.app = app;
		this.graphics = graphics;
		return this;
	}

	@Override
	public void viewWillAppear (boolean animated) {
		super.viewWillAppear(animated);
		// start GLKViewController even though we may only draw a single frame
		// (we may be in non-continuous mode)
		setPaused(false);
		injectKeyboardNotification();
	}

	protected Input.KeyboardHeightObserver observer;

	@Selector("keyboardWillHide")
	public void keyboardWillHide (NSNotification notification) {
		if (observer != null) observer.onKeyboardHeightChanged(0);
	}

	@Selector("keyboardWillShow")
	public void keyboardWillShow (NSNotification notification) {
		CGRect screenRect = UIScreen.mainScreen().bounds();
		double screenHeight = screenRect.size().height();
		double heightScale = Gdx.graphics.getHeight() / screenHeight;

		NSDictionary<String, ?> userInfo = (NSDictionary<String, ?>)notification.userInfo();
		CGRect keyboardEndFrame;
		keyboardEndFrame = ((NSValue)userInfo.objectForKey(UIKit.UIKeyboardFrameEndUserInfoKey())).CGRectValue();

		UIView textField = null;
		// No lambdas :(
		// Also, we might need to verify the UITextField to, that it matches the one defined in DefaultIOSInput
		for (UIView e : view().subviews()) {
			if (e instanceof UITextView || e instanceof UITextField) {
				if (e.isFirstResponder() && !e.isHidden()) {
					textField = e;
					break;
				} else {
					if (observer != null) observer.onKeyboardHeightChanged((int)(keyboardEndFrame.size().height() * heightScale));
					return;
				}
			}
		}

		if (textField == null) {
			if (observer != null) observer.onKeyboardHeightChanged((int)(keyboardEndFrame.size().height() * heightScale));
			return;
		}

		double duration;
		long curve;
		curve = ((NSNumber)userInfo.objectForKey(UIKit.UIKeyboardAnimationCurveUserInfoKey())).longValue();
		duration = ((NSNumber)userInfo.objectForKey(UIKit.UIKeyboardAnimationDurationUserInfoKey())).doubleValue();

		UIView.beginAnimationsContext(null, null);
		UIView.setAnimationDuration_static(duration);
		UIView.setAnimationCurve(curve);

		CGRect newFrame = textField.frame();
		if (observer != null)
			observer.onKeyboardHeightChanged((int)((keyboardEndFrame.size().height() + newFrame.size().height()) * heightScale));

		keyboardEndFrame = textField.convertRectToView(keyboardEndFrame, null);
		newFrame.setOrigin(new CGPoint(view().safeAreaInsets().left(),
			view().bounds().size().height() - keyboardEndFrame.size().height() - newFrame.size().height()));
		newFrame
			.setSize(new CGSize(view().bounds().size().width() - view().safeAreaInsets().left() - view().safeAreaInsets().right(),
				newFrame.size().height()));
		textField.setFrame(newFrame);

		UIView.commitAnimations();

		// If we want to do it with constraints
		// ((DefaultIOSInput)((IOSApplication) Gdx.app).input).textfield.setTranslatesAutoresizingMaskIntoConstraints(false);
		// ((DefaultIOSInput)((IOSApplication)
		// Gdx.app).input).textfield.bottomAnchor().constraintEqualToAnchorConstant(view().bottomAnchor(),
		// -keyboardEndFrame.size().height()).setActive(true);
		// ((DefaultIOSInput)((IOSApplication)
		// Gdx.app).input).textfield.leftAnchor().constraintEqualToAnchor(view().leftAnchor()).setActive(true);
		// ((DefaultIOSInput)((IOSApplication)
		// Gdx.app).input).textfield.rightAnchor().constraintEqualToAnchor(view().rightAnchor()).setActive(true);

	}

	public void injectKeyboardNotification () {
		NSNotificationCenter.defaultCenter().addObserverSelectorNameObject(this, new SEL("keyboardWillShow"),
			UIKit.UIKeyboardWillShowNotification(), null);
		NSNotificationCenter.defaultCenter().addObserverSelectorNameObject(this, new SEL("keyboardWillHide"),
			UIKit.UIKeyboardWillHideNotification(), null);

	}

	@Override
	public void viewDidAppear (boolean animated) {
		super.viewDidAppear(animated);
		view().setContentScaleFactor(UIScreen.mainScreen().nativeScale());
		if (app.viewControllerListener != null) app.viewControllerListener.viewDidAppear(animated);
	}

	@Override
	public long supportedInterfaceOrientations () {
		long mask = 0;
		if (app.config.orientationLandscape) {
			mask |= (1L << UIInterfaceOrientation.LandscapeLeft) | (1L << UIInterfaceOrientation.LandscapeRight);
		}
		if (app.config.orientationPortrait) {
			mask |= 1L << UIInterfaceOrientation.Portrait;
			if (UIDevice.currentDevice().userInterfaceIdiom() == UIUserInterfaceIdiom.Pad) {
				mask |= 1L << UIInterfaceOrientation.PortraitUpsideDown;
			}
		}
		return mask;
	}

	@Override
	public boolean shouldAutorotate () {
		return true;
	}

	@Override
	public long preferredScreenEdgesDeferringSystemGestures () {
		return app.config.screenEdgesDeferringSystemGestures;
	}

	@Override
	public void viewDidLayoutSubviews () {
		super.viewDidLayoutSubviews();
		// get the view size and update graphics
		final IOSScreenBounds oldBounds = graphics.screenBounds;
		final IOSScreenBounds newBounds = app.computeBounds();
		graphics.screenBounds = newBounds;
		// Layout may happen without bounds changing, don't trigger resize in that case
		if (newBounds.width != oldBounds.width || newBounds.height != oldBounds.height) {
			graphics.makeCurrent();
			graphics.updateSafeInsets();
			graphics.gl20.glViewport(0, 0, newBounds.backBufferWidth, newBounds.backBufferHeight);
			if (graphics.config.hdpiMode == HdpiMode.Pixels) {
				app.listener.resize(newBounds.backBufferWidth, newBounds.backBufferHeight);
			} else {
				app.listener.resize(newBounds.width, newBounds.height);
			}
		}

	}

	@Override
	public boolean prefersStatusBarHidden () {
		return !app.config.statusBarVisible;
	}

	@Override
	public boolean prefersHomeIndicatorAutoHidden () {
		return app.config.hideHomeIndicator;
	}

	@Override
	public void pressesBeganWithEvent (NSSet<? extends UIPress> presses, UIPressesEvent event) {
		if (presses == null || presses.count() == 0 || !app.input.onKey(presses.objectEnumerator().nextObject().key(), true)) {
			super.pressesBeganWithEvent(presses, event);
		}
	}

	@Override
	public void pressesEndedWithEvent (NSSet<? extends UIPress> presses, UIPressesEvent event) {
		if (presses == null || presses.count() == 0 || !app.input.onKey(presses.objectEnumerator().nextObject().key(), false)) {
			super.pressesEndedWithEvent(presses, event);
		}
	}
}
