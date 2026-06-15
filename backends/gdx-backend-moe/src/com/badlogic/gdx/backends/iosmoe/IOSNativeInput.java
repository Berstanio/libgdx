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

package com.badlogic.gdx.backends.iosmoe;

import apple.NSObject;
import apple.corefoundation.struct.CGPoint;
import apple.corefoundation.struct.CGRect;
import apple.corefoundation.struct.CGSize;
import apple.foundation.NSArray;
import apple.foundation.NSAttributedString;
import apple.foundation.NSData;
import apple.foundation.NSDictionary;
import apple.foundation.NSIndexPath;
import apple.foundation.struct.NSRange;
import apple.uikit.UIBarButtonItem;
import apple.uikit.UIButton;
import apple.uikit.UIColor;
import apple.uikit.UIDevice;
import apple.uikit.UIFont;
import apple.uikit.UIImage;
import apple.uikit.UILabel;
import apple.uikit.UIListContentConfiguration;
import apple.uikit.UIScreen;
import apple.uikit.UITableView;
import apple.uikit.UITableViewCell;
import apple.uikit.UITextField;
import apple.uikit.UITextPosition;
import apple.uikit.UITextView;
import apple.uikit.UIToolbar;
import apple.uikit.UIView;
import apple.uikit.c.UIKit;
import apple.uikit.enums.UIBarButtonSystemItem;
import apple.uikit.enums.UIButtonType;
import apple.uikit.enums.UIControlEvents;
import apple.uikit.enums.UIControlState;
import apple.uikit.enums.UIKeyboardType;
import apple.uikit.enums.UIReturnKeyType;
import apple.uikit.enums.UITableViewCellStyle;
import apple.uikit.enums.UITextAutocapitalizationType;
import apple.uikit.enums.UITextAutocorrectionType;
import apple.uikit.enums.UITextFieldViewMode;
import apple.uikit.enums.UITextSpellCheckingType;
import apple.uikit.enums.UIUserInterfaceIdiom;
import apple.uikit.enums.UIViewAutoresizing;
import apple.uikit.protocol.UITableViewDataSource;
import apple.uikit.protocol.UITableViewDelegate;
import apple.uikit.protocol.UITextFieldDelegate;
import apple.uikit.protocol.UITextInput;
import apple.uikit.protocol.UITextViewDelegate;
import apple.uikit.struct.UIEdgeInsets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.InputStringValidator;
import com.badlogic.gdx.Input.OnscreenKeyboardType;
import com.badlogic.gdx.input.NativeInputConfiguration;
import com.badlogic.gdx.input.NativeInputConfiguration.NativeInputCloseCallback;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Null;

import org.moe.natj.general.NatJ;
import org.moe.natj.general.Pointer;
import org.moe.natj.general.ptr.impl.PtrFactory;
import org.moe.natj.objc.SEL;
import org.moe.natj.objc.ann.Selector;

import static apple.coregraphics.c.CoreGraphics.*;

/** Bundles the state and machinery of one {@link com.badlogic.gdx.Input#openTextInputField(NativeInputConfiguration)} session:
 * the native text field/view, its delegates and accessories, and its layout relative to the keyboard. Created by
 * {@link DefaultIOSInput#openTextInputField(NativeInputConfiguration)} and discarded once
 * {@link DefaultIOSInput#closeTextInputField(boolean, NativeInputCloseCallback)} processes. Keyboard events are handled and
 * dispatched by {@link DefaultIOSInput}, which instructs this session to re-layout or close. */
public class IOSNativeInput extends NSObject {

	static {
		NatJ.register();
	}

	@Selector("alloc")
	public static native IOSNativeInput alloc ();

	@Selector("init")
	public native IOSNativeInput init ();

	protected IOSNativeInput (Pointer peer) {
		super(peer);
	}

	protected IOSApplication app;
	protected NativeInputConfiguration configuration;

	protected UIView textfield = null;
	protected UILabel textViewPlaceholderLabel;
	protected UITableView suggestionTable;
	protected Array<String> autoCompleteAvailable;

	protected final UITextViewDelegate textViewDelegate = new UITextViewDelegate() {

		@Override
		public void textViewDidChange (UITextView textView) {
			if (textViewPlaceholderLabel != null) textViewPlaceholderLabel.setHidden(!textView.text().isEmpty());
			notifyNativeInputChanged(false);
		}

		@Override
		public void textViewDidChangeSelection (UITextView textView) {
			notifyNativeInputChanged(true);
		}

		@Override
		public boolean textViewShouldChangeTextInRangeReplacementText (UITextView textView, NSRange range, String text) {
			if (configuration.getMaxLength() != -1
				&& textView.text().length() + (text.length() - range.length()) > configuration.getMaxLength()) {
				return false;
			}

			// Check if type was promoted
			if (textView.keyboardType() == UIKeyboardType.NumbersAndPunctuation) {
				if (configuration.getType() == OnscreenKeyboardType.NumberPad)
					if (!InputStringValidator.DIGITAL_VALIDATOR.validate(text)) return false;
				if (configuration.getType() == OnscreenKeyboardType.PhonePad)
					if (!InputStringValidator.PHONE_VALIDATOR.validate(text)) return false;
			}

			if (configuration.getValidator() == null) return true;
			return configuration.getValidator().validate(text);
		}
	};

	protected final UITextFieldDelegate textDelegate = new UITextFieldDelegate() {

		@Override
		public boolean textFieldShouldChangeCharactersInRangeReplacementString (UITextField textField, NSRange range, String text) {
			if (configuration.getMaxLength() != -1
				&& textField.text().length() + (text.length() - range.length()) > configuration.getMaxLength()) {
				return false;
			}

			// Check if type was promoted
			if (textField.keyboardType() == UIKeyboardType.NumbersAndPunctuation) {
				if (configuration.getType() == OnscreenKeyboardType.NumberPad)
					if (!InputStringValidator.DIGITAL_VALIDATOR.validate(text)) return false;
				if (configuration.getType() == OnscreenKeyboardType.PhonePad)
					if (!InputStringValidator.PHONE_VALIDATOR.validate(text)) return false;
			}

			if (configuration.getValidator() == null) return true;
			return configuration.getValidator().validate(text);
		}

		@Override
		public boolean textFieldShouldReturn (UITextField textField) {
			Gdx.input.closeTextInputField(true);
			Gdx.graphics.requestRendering();
			return false;
		}

		@Override
		public void textFieldDidChangeSelection (UITextField textField) {
			// iOS 13+ only
			notifyNativeInputChanged(true);
		}
	};

	protected final UITableViewDataSource suggestionDataSource = new UITableViewDataSource() {

		@Override
		public UITableViewCell tableViewCellForRowAtIndexPath (UITableView tableView, NSIndexPath indexPath) {
			UITableViewCell cell = tableView.dequeueReusableCellWithIdentifier("suggestion");
			if (cell == null)
				cell = UITableViewCell.alloc().initWithStyleReuseIdentifier(UITableViewCellStyle.Default, "suggestion");
			if (Foundation.getMajorSystemVersion() >= 14) {
				UIListContentConfiguration contentConfiguration = cell.defaultContentConfiguration();
				NSAttributedString coloredText = NSAttributedString.alloc()
					.initWithStringAttributes(autoCompleteAvailable.get((int)indexPath.row()), (NSDictionary<String, ?>)NSDictionary
						.dictionaryWithObjectForKey(toUIColor(configuration.getTextColor()), UIKit.NSForegroundColorAttributeName()));

				contentConfiguration.setAttributedText(coloredText);
				cell.setContentConfiguration(contentConfiguration);
			} else {
				cell.textLabel().setText(autoCompleteAvailable.get((int)indexPath.row()));
				cell.textLabel().setTextColor(toUIColor(configuration.getTextColor()));
			}
			cell.setBackgroundColor(toUIColor(configuration.getBackgroundColor()));
			return cell;
		}

		@Override
		public long tableViewNumberOfRowsInSection (UITableView tableView, long section) {
			return autoCompleteAvailable.size;
		}
	};

	protected final UITableViewDelegate suggestionDelegate = new UITableViewDelegate() {

		@Override
		public void tableViewDidSelectRowAtIndexPath (UITableView tableView, NSIndexPath indexPath) {
			tableView.deselectRowAtIndexPathAnimated(indexPath, true);
			((UITextField)textfield).setText(autoCompleteAvailable.get((int)indexPath.row()));
			Gdx.input.closeTextInputField(false);
		}
	};

	public IOSNativeInput init (IOSApplication app, NativeInputConfiguration configuration) {
		init();
		this.app = app;
		this.configuration = configuration;
		this.configuration.validate();
		return this;
	}

	protected void layoutTextFieldAboveKeyboard (double keyboardHeight) {
		UIView textField = textfield;
		float insetFraction = configuration.getHorizontalInsetFraction();
		UIView rootView = app.getUIViewController().view();
		double fallbackInset = rootView.bounds().size().width() * insetFraction;

		UIView accessoryView = textField.inputAccessoryView();
		CGRect newFrame = textField.frame();
		double specialRightInset = 0;
		if (Foundation.getMajorSystemVersion() >= 26 && accessoryView != null) {
			keyboardHeight -= accessoryView.bounds().size().height();
			specialRightInset = accessoryView.bounds().size().height() + 3;
		}

		double leftInset = Math.max(rootView.safeAreaInsets().left(), fallbackInset);
		double rightInset = Math.max(rootView.safeAreaInsets().right(), fallbackInset) + specialRightInset;
		newFrame.setOrigin(new CGPoint(leftInset, rootView.bounds().size().height() - keyboardHeight - newFrame.size().height()));
		newFrame.setSize(new CGSize(rootView.bounds().size().width() - leftInset - rightInset, newFrame.size().height()));
		textField.setFrame(newFrame);

		// The iOS 26+ toolbar (PinnedFrameToolbar) derives its real frame from the field on any geometry write, so a
		// dummy setFrame re-anchors it to the field's new position (and animates along inside an animation block). For
		// the plain pre-26 toolbar this is a no-op.
		if (accessoryView != null) accessoryView.setFrame(accessoryView.frame());

		layoutSuggestionTable();
	}

	protected void moveTextFieldToBottom () {
		UIView textField = textfield;
		if (textField == null) return;

		// Synthetic keyboard height: the bottom safe area, plus the accessory view that UIKit keeps showing at the
		// bottom of the screen while no on-screen keyboard is up (e.g. with a hardware keyboard)
		double height = app.getUIViewController().view().safeAreaInsets().bottom();
		UIView accessoryView = textField.inputAccessoryView();
		if (accessoryView != null) height += accessoryView.bounds().size().height();
		layoutTextFieldAboveKeyboard(height);
	}

	protected void layoutSuggestionTable () {
		if (suggestionTable == null) return;
		CGRect textFrame = textfield.frame();

		double available = textFrame.origin().y();
		suggestionTable
			.setFrame(new CGRect(new CGPoint(textFrame.origin().x(), 0), new CGSize(textFrame.size().width(), available)));
		suggestionTable.layoutIfNeeded();

		double height = Math.min(suggestionTable.contentSize().height(), available);
		suggestionTable.setFrame(new CGRect(new CGPoint(textFrame.origin().x(), textFrame.origin().y() - height),
			new CGSize(textFrame.size().width(), height)));
	}

	protected int getFieldOccupiedHeight () {
		UIView rootView = app.getUIViewController().view();
		double heightScale = Gdx.graphics.getHeight() / UIScreen.mainScreen().bounds().size().height();
		return (int)((rootView.bounds().size().height() - CGRectGetMinY(textfield.frame())) * heightScale);
	}

	@Selector("textChanged")
	public void textChanged (UITextField textField) {
		notifyNativeInputChanged(false);
	}

	@Selector("updateAutoComplete")
	public void updateAutoComplete (UITextField textField) {
		autoCompleteAvailable.clear();
		if (textField.text().length() >= configuration.getAutoCompleteThreshold()) {
			for (String s : configuration.getAutoComplete()) {
				if (s.startsWith(textField.text())) {
					autoCompleteAvailable.add(s);
				}
			}
		}
		suggestionTable.reloadData();
		layoutSuggestionTable();
	}

	@Selector("doneClicked")
	public void doneClicked () {
		Gdx.input.closeTextInputField(true);
	}

	@Selector("togglePasswordView")
	public void togglePasswordView (UIButton sender) {
		UITextField textField = (UITextField)textfield;
		textField.setSecureTextEntry(!textField.isSecureTextEntry());
		String fileName = textField.isSecureTextEntry() ? "ic_password_visible.png" : "ic_password_invisible.png";
		byte[] data = Gdx.files.classpath(fileName).readBytes();
		NSData nsData = NSData.dataWithBytesLength(PtrFactory.newByteArray(data), data.length);
		sender.setImageForState(UIImage.imageWithData(nsData), UIControlState.Normal);
	}

	public void open () {
		if (textfield != null) throw new IllegalStateException("Double open of IOSNativeInput detected");
		long keyboardType = DefaultIOSInput.getIosInputType(configuration.getType());
		// IPad has a very weird small numberpad input. Promote to full keyboard and filter out non-digits
		if (UIDevice.currentDevice().userInterfaceIdiom() == UIUserInterfaceIdiom.Pad) {
			if (keyboardType == UIKeyboardType.NumberPad || keyboardType == UIKeyboardType.PhonePad)
				keyboardType = UIKeyboardType.NumbersAndPunctuation;
		}

		createDefaultTextField(configuration.isMultiLine(),
			configuration.isMultiLine() || keyboardType == UIKeyboardType.NumberPad || keyboardType == UIKeyboardType.PhonePad);

		UITextInput uiTextInput = (UITextInput)textfield;
		uiTextInput.setKeyboardType(keyboardType);

		if (configuration.isPreventCorrection()) {
			uiTextInput.setAutocorrectionType(UITextAutocorrectionType.No);
			uiTextInput.setSpellCheckingType(UITextSpellCheckingType.No);
		} else {
			uiTextInput.setAutocorrectionType(UITextAutocorrectionType.Yes);
			uiTextInput.setSpellCheckingType(UITextSpellCheckingType.Yes);
		}

		uiTextInput.setAutocapitalizationType(getIosAutocapitalizationType(configuration.getAutocapitalization()));

		if (configuration.getContentType() != null) {
			String contentType = getIosContentType(configuration.getContentType());
			if (contentType != null) uiTextInput.setTextContentType(contentType);
		}

		textfield.setBackgroundColor(toUIColor(configuration.getBackgroundColor()));
		textfield.layer().setCornerRadius(configuration.getCornerRadius());

		if (textfield instanceof UITextView) {
			UITextView textView = (UITextView)textfield;
			textView.setText(configuration.getTextInputWrapper().getText());
			textView.setTextColor(toUIColor(configuration.getTextColor()));
			textView.textContainer().setLineFragmentPadding(0);
			textView.setTextContainerInset(new UIEdgeInsets(8, configuration.getTextMargin(), 8, configuration.getTextMargin()));
			createTextViewPlaceholder(textView, configuration);
			textView.setDelegate(textViewDelegate);
		} else {
			final UITextField asTextField = (UITextField)textfield;
			if (configuration.getAutoComplete() != null) {
				suggestionTable = UITableView.alloc()
					.initWithFrame(new CGRect(new CGPoint(app.graphics.screenBounds.width, app.graphics.screenBounds.height),
						new CGSize(app.graphics.screenBounds.width, 50)));
				suggestionTable.setScrollEnabled(true);
				suggestionTable.setBackgroundColor(toUIColor(configuration.getBackgroundColor()));
				suggestionTable.layer().setCornerRadius(configuration.getCornerRadius());
				suggestionTable.setRowHeight(UIKit.UITableViewAutomaticDimension());
				suggestionTable.setEstimatedRowHeight(44);
				autoCompleteAvailable = new Array<>(configuration.getAutoComplete());
				suggestionTable.setDataSource(suggestionDataSource);
				suggestionTable.setDelegate(suggestionDelegate);

				asTextField.addTargetActionForControlEvents(this, new SEL("updateAutoComplete"), UIControlEvents.EditingChanged);
				app.getUIViewController().view().addSubview(suggestionTable);
			}
			asTextField.setText(configuration.getTextInputWrapper().getText());
			asTextField.setDelegate(textDelegate);
			NativeInputConfiguration.WriteMode writeMode = configuration.getWriteMode();
			if (writeMode != NativeInputConfiguration.WriteMode.ONLY_FINAL) {
				asTextField.addTargetActionForControlEvents(this, new SEL("textChanged"), UIControlEvents.EditingChanged);
			}

			asTextField.setTextColor(toUIColor(configuration.getTextColor()));
			asTextField.setReturnKeyType(getIosReturnKeyType(configuration.getReturnKeyType()));

			asTextField.setLeftView(
				UIView.alloc().initWithFrame(new CGRect(new CGPoint(0, 0), new CGSize(configuration.getTextMargin(), 1))));
			asTextField.setLeftViewMode(UITextFieldViewMode.Always);
			asTextField.setRightView(
				UIView.alloc().initWithFrame(new CGRect(new CGPoint(0, 0), new CGSize(configuration.getTextMargin(), 1))));
			asTextField.setRightViewMode(UITextFieldViewMode.Always);

			// Because apple seems to have unreadable placeholder color by default
			NSAttributedString placeholderString = NSAttributedString.alloc().initWithStringAttributes(
				configuration.getPlaceholder(),
				(NSDictionary<String, ?>)NSDictionary.dictionaryWithObjectForKey(toUIColor(configuration.getPlaceholderColor()),
					UIKit.NSForegroundColorAttributeName()));
			asTextField.setAttributedPlaceholder(placeholderString);

			// Needs to happen after setText
			if (configuration.getAutoComplete() != null) updateAutoComplete(asTextField);

			if (configuration.isMaskInput()) {
				if (configuration.isShowUnmaskButton()) {
					UIButton button = UIButton.buttonWithType(UIButtonType.Custom);
					togglePasswordView(button);
					button.setImageEdgeInsets(new UIEdgeInsets(0, -16, 0, 0));
					button.setFrame(new CGRect(new CGPoint(textfield.frame().size().width() - 25, 5), new CGSize(25, 25)));
					button.addTargetActionForControlEvents(this, new SEL("togglePasswordView"), UIControlEvents.TouchUpInside);
					asTextField.setRightView(button);
					asTextField.setRightViewMode(UITextFieldViewMode.Always);
				} else {
					asTextField.setSecureTextEntry(true);
				}
			}
		}
		if (configuration.getFieldCustomizer() != null) configuration.getFieldCustomizer().customize(this);

		// Start at the bottom of the screen: keyboardWillShow moves the field above a docked keyboard, but with a hardware
		// keyboard or an already floating keyboard no notification fires at all and the field stays here
		moveTextFieldToBottom();

		textfield.reloadInputViews();
		textfield.becomeFirstResponder();

		UITextPosition start = uiTextInput.positionFromPositionOffset(uiTextInput.beginningOfDocument(),
			configuration.getTextInputWrapper().getSelectionStart());
		UITextPosition end = uiTextInput.positionFromPositionOffset(uiTextInput.beginningOfDocument(),
			configuration.getTextInputWrapper().getSelectionEnd());

		uiTextInput.setSelectedTextRange(uiTextInput.textRangeFromPositionToPosition(start, end));
	}

	protected void notifyNativeInputChanged (boolean selectionOnly) {
		UITextInput uiTextInput = (UITextInput)textfield;
		if (uiTextInput == null) return;
		NativeInputConfiguration.WriteMode writeMode = configuration.getWriteMode();
		if (writeMode == NativeInputConfiguration.WriteMode.ONLY_FINAL) return;
		if (selectionOnly && writeMode != NativeInputConfiguration.WriteMode.ALL_UPDATES) return;

		String text;
		if (uiTextInput instanceof UITextView) {
			text = ((UITextView)uiTextInput).text();
		} else {
			text = ((UITextField)uiTextInput).text();
		}
		long selectionStart = uiTextInput.offsetFromPositionToPosition(uiTextInput.beginningOfDocument(),
			uiTextInput.selectedTextRange().start());
		long selectionEnd = uiTextInput.offsetFromPositionToPosition(uiTextInput.beginningOfDocument(),
			uiTextInput.selectedTextRange().end());

		configuration.getTextInputWrapper().writeResults(text, (int)selectionStart, (int)selectionEnd);
	}

	/** Closes the native text field and writes back the results. Call exactly once; the session must not be reused afterwards. */
	public void close (boolean isConfirmative, @Null NativeInputCloseCallback callback) {
		if (textfield == null) throw new IllegalStateException("Double close of IOSNativeInput detected");
		UITextInput uiTextInput = (UITextInput)textfield;
		String text;
		if (textfield instanceof UITextView) {
			text = ((UITextView)textfield).text();
		} else {
			text = ((UITextField)textfield).text();
		}
		long selectionStart = uiTextInput.offsetFromPositionToPosition(uiTextInput.beginningOfDocument(),
			uiTextInput.selectedTextRange().start());
		long selectionEnd = uiTextInput.offsetFromPositionToPosition(uiTextInput.beginningOfDocument(),
			uiTextInput.selectedTextRange().end());

		Gdx.app.postRunnable( () -> {
			configuration.getTextInputWrapper().writeResults(text, (int)selectionStart, (int)selectionEnd);

			// We actually don't care about whether the keyboard should be closed or not, cause iOS is not buggy in that regard
			boolean keepOpen = configuration.getCloseCallback().onClose(isConfirmative);
			if (callback != null) keepOpen |= callback.onClose(isConfirmative);
		});

		if (suggestionTable != null) {
			((UITextField)textfield).removeTargetActionForControlEvents(this, new SEL("updateAutoComplete"),
				UIControlEvents.EditingChanged);
			suggestionTable.removeFromSuperview();
			suggestionTable = null;
			autoCompleteAvailable = null;
		}

		if (textfield.inputAccessoryView() != null) textfield.inputAccessoryView().setHidden(true);

		textfield.resignFirstResponder();
		// We could first move the text field animated down and than delete, but I think it doesn't matter
		textfield.removeFromSuperview();
		textfield = null;
		textViewPlaceholderLabel = null;
	}

	private static UIColor toUIColor (com.badlogic.gdx.graphics.Color color) {
		return UIColor.colorWithRedGreenBlueAlpha(color.r, color.g, color.b, color.a);
	}

	protected long getIosReturnKeyType (NativeInputConfiguration.ReturnKeyType returnKeyType) {
		switch (returnKeyType) {
		case GO:
			return UIReturnKeyType.Go;
		case SEARCH:
			return UIReturnKeyType.Search;
		case SEND:
			return UIReturnKeyType.Send;
		case NEXT:
			return UIReturnKeyType.Next;
		case DONE:
			return UIReturnKeyType.Done;
		default:
			throw new IllegalArgumentException(returnKeyType.name());
		}
	}

	protected long getIosAutocapitalizationType (NativeInputConfiguration.Autocapitalization autocapitalization) {
		switch (autocapitalization) {
		case WORDS:
			return UITextAutocapitalizationType.Words;
		case SENTENCES:
			return UITextAutocapitalizationType.Sentences;
		case CHARACTERS:
			return UITextAutocapitalizationType.AllCharacters;
		case NONE:
			return UITextAutocapitalizationType.None;
		default:
			throw new IllegalArgumentException(autocapitalization.name());
		}
	}

	@Null
	protected String getIosContentType (NativeInputConfiguration.ContentType contentType) {
		switch (contentType) {
		case USERNAME:
			return UIKit.UITextContentTypeUsername();
		case PASSWORD:
			return UIKit.UITextContentTypePassword();
		case NEW_PASSWORD:
			return Foundation.getMajorSystemVersion() >= 12 ? UIKit.UITextContentTypeNewPassword() : null;
		case ONE_TIME_CODE:
			return Foundation.getMajorSystemVersion() >= 12 ? UIKit.UITextContentTypeOneTimeCode() : null;
		case EMAIL:
			return UIKit.UITextContentTypeEmailAddress();
		case PHONE:
			return UIKit.UITextContentTypeTelephoneNumber();
		default:
			throw new IllegalArgumentException(contentType.name());
		}
	}

	/** UITextView has no native placeholder support, so we layer a UILabel over where the first line of text renders. */
	protected void createTextViewPlaceholder (UITextView textView, NativeInputConfiguration configuration) {
		UILabel placeholderLabel = UILabel.alloc().init();
		placeholderLabel.setText(configuration.getPlaceholder());
		placeholderLabel.setTextColor(toUIColor(configuration.getPlaceholderColor()));
		UIFont font = textView.font() != null ? textView.font() : UIFont.systemFontOfSize(UIFont.systemFontSize());
		placeholderLabel.setFont(font);
		placeholderLabel.setUserInteractionEnabled(false);

		UIEdgeInsets containerInset = textView.textContainerInset();
		double lineFragmentPadding = textView.textContainer().lineFragmentPadding();
		double x = containerInset.left() + lineFragmentPadding;
		placeholderLabel.setFrame(new CGRect(new CGPoint(x, containerInset.top()),
			new CGSize(textView.bounds().size().width() - x - containerInset.right() - lineFragmentPadding, font.lineHeight())));
		// The text view is re-framed when the keyboard shows
		placeholderLabel.setAutoresizingMask(UIViewAutoresizing.FlexibleWidth);

		textView.addSubview(placeholderLabel);
		textViewPlaceholderLabel = placeholderLabel;
	}

	public UIView getTextField () {
		return textfield;
	}

	public NativeInputConfiguration getConfiguration () {
		return configuration;
	}

	public UILabel getTextViewPlaceholderLabel () {
		return textViewPlaceholderLabel;
	}

	public UITableView getSuggestionTable () {
		return suggestionTable;
	}

	protected void createDefaultTextField (boolean isMultiLine, boolean needsDoneToolbar) {
		CGRect rect = new CGRect();
		rect.setOrigin(new CGPoint(app.graphics.screenBounds.width, app.graphics.screenBounds.height));
		rect.setSize(new CGSize(app.graphics.screenBounds.width, 50));

		if (isMultiLine) {
			UITextView textView = UITextView.alloc().initWithFrame(rect);
			textView.setTextColor(UIColor.blackColor());
			textView.setReturnKeyType(UIReturnKeyType.Default);
			textfield = textView;
		} else {
			UITextField uiTextField = UITextField.alloc().initWithFrame(rect);
			uiTextField.setTextColor(UIColor.blackColor());
			uiTextField.setReturnKeyType(UIReturnKeyType.Done);
			textfield = uiTextField;
		}

		if (needsDoneToolbar) {
			int height = Foundation.getMajorSystemVersion() >= 26 ? 44 : 35;
			CGRect toolbarFrame = new CGRect(new CGPoint(0, 0), new CGSize(UIScreen.mainScreen().bounds().size().width(), height));
			UIToolbar uiToolbar = Foundation.getMajorSystemVersion() >= 26
				? PinnedFrameToolbar.alloc().initWithFrameTextField(toolbarFrame, textfield)
				: UIToolbar.alloc().initWithFrame(toolbarFrame);

			UIBarButtonItem space = UIBarButtonItem.alloc()
				.initWithBarButtonSystemItemTargetAction(UIBarButtonSystemItem.FlexibleSpace, null, null);

			UIBarButtonItem doneButton = UIBarButtonItem.alloc().initWithBarButtonSystemItemTargetAction(UIBarButtonSystemItem.Done,
				this, new SEL("doneClicked"));

			if (Foundation.getMajorSystemVersion() >= 26) {
				uiToolbar.setItems((NSArray<? extends UIBarButtonItem>)NSArray.arrayWithObject(doneButton));
				uiToolbar.setTranslatesAutoresizingMaskIntoConstraints(true);
				uiToolbar.setAutoresizingMask(UIViewAutoresizing.FlexibleWidth);
			} else {
				uiToolbar.setItems((NSArray<? extends UIBarButtonItem>)NSArray.arrayWithObjects(space, doneButton, null));
				uiToolbar.updateConstraintsIfNeeded();
			}

			if (isMultiLine) {
				((UITextView)textfield).setInputAccessoryView(uiToolbar);
			} else {
				((UITextField)textfield).setInputAccessoryView(uiToolbar);
			}
		}

		app.getUIViewController().view().addSubview(textfield);
	}

	/** Accessory toolbar that derives its own frame from the active text field. UIKit's input hosting system owns the accessory
	 * view's geometry and rewrites it at unpredictable times during keyboard transitions (re-hosting, dismiss animations,
	 * dock/float changes), and it hosts the view in containers whose own position differs between docked, floating and
	 * keyboard-less modes — so neither an externally set frame nor host-relative coordinates stick. `frame` is computed from
	 * `center` and `bounds`, and UIKit (autolayout in particular) positions views by writing those two directly without ever
	 * calling `setFrame:` — so all three setters are intercepted. Every geometry write — ours or UIKit's — re-resolves against the
	 * text field's and the host's current position, placing the toolbar so its centered liquid glass pill sits right of the field,
	 * vertically centered on its row. */
	protected static class PinnedFrameToolbar extends UIToolbar {

		static {
			NatJ.register();
		}

		@Selector("alloc")
		public static native PinnedFrameToolbar alloc ();

		protected PinnedFrameToolbar (Pointer peer) {
			super(peer);
		}

		private UIView textField;

		public PinnedFrameToolbar initWithFrameTextField (CGRect frame, UIView textField) {
			initWithFrame(frame);
			this.textField = textField;
			return this;
		}

		private CGRect resolvePinnedFrame () {
			// textField is still null while the super constructor applies the initial frame
			if (textField == null || textField.superview() == null || superview() == null) return null;
			CGRect fieldFrame = textField.superview().convertRectToView(textField.frame(), null);
			double pillSize = bounds().size().height();
			double pillCenterX = CGRectGetMaxX(fieldFrame) + 3 + pillSize / 2;
			CGRect windowFrame = new CGRect(new CGPoint(pillCenterX - pillSize / 2, CGRectGetMidY(fieldFrame) - pillSize / 2),
				new CGSize(pillSize, pillSize));
			return superview().convertRectFromView(windowFrame, null);
		}

		@Override
		public void setFrame (CGRect frame) {
			CGRect pinned = resolvePinnedFrame();
			super.setFrame(pinned != null ? pinned : frame);
		}

		@Override
		public void setCenter (CGPoint center) {
			CGRect pinned = resolvePinnedFrame();
			super.setCenter(pinned != null ? new CGPoint(CGRectGetMidX(pinned), CGRectGetMidY(pinned)) : center);
		}

		@Override
		public void setBounds (CGRect bounds) {
			CGRect pinned = resolvePinnedFrame();
			super.setBounds(pinned != null ? new CGRect(bounds.origin(), pinned.size()) : bounds);
		}

		// UIKit may frame the view before hosting it (where the pin can't resolve yet) and never write again — e.g. when
		// the field opens under an already floating keyboard, which fires no keyboard notifications either. Re-anchor at
		// the attachment moments themselves
		@Override
		public void didMoveToSuperview () {
			super.didMoveToSuperview();
			CGRect pinned = resolvePinnedFrame();
			if (pinned != null) super.setFrame(pinned);
		}

		@Override
		public void didMoveToWindow () {
			super.didMoveToWindow();
			CGRect pinned = resolvePinnedFrame();
			if (pinned != null) super.setFrame(pinned);
		}
	}

}
