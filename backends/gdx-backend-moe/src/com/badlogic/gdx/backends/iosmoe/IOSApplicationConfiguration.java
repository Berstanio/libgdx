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

import com.badlogic.gdx.backends.bindings.metalangle.enums.MGLDrawableColorFormat;
import com.badlogic.gdx.backends.bindings.metalangle.enums.MGLDrawableDepthFormat;
import com.badlogic.gdx.backends.bindings.metalangle.enums.MGLDrawableMultisample;
import com.badlogic.gdx.backends.bindings.metalangle.enums.MGLDrawableStencilFormat;
import com.badlogic.gdx.utils.ObjectMap;


public class IOSApplicationConfiguration {
	/** whether to enable screen dimming. */
	public boolean preventScreenDimming = true;
	/** whether or not portrait orientation is supported. */
	public boolean orientationPortrait = true;
	/** whether or not landscape orientation is supported. */
	public boolean orientationLandscape = true;

	/** the color format, RGB565 is the default **/
	public int colorFormat = MGLDrawableColorFormat.RGB565;

	/** the depth buffer format, Format16 is default **/
	public int depthFormat = MGLDrawableDepthFormat.Format16;

	/** the stencil buffer format, None is default **/
	public int stencilFormat = MGLDrawableStencilFormat.FormatNone;

	/** the multisample format, None is default **/
	public int multisample = MGLDrawableMultisample.MultisampleNone;

	/** number of frames per second, 60 is default **/
	public int preferredFramesPerSecond = 60;

	/** Scale factor to use on large screens with retina display, i.e. iPad 3+ (has no effect on non-retina screens).
	 * <ul>
	 * <li>1.0 = no scaling (everything is in pixels)
	 * <li>0.5 = LibGDX will behave as you would only have half the pixels. I.e. instead of 2048x1536 you will work in 1024x768.
	 * This looks pixel perfect and will save you the trouble to create bigger graphics for the retina display.
	 * <li>any other value: scales the screens according to your scale factor. A scale factor oof 0.75, 0.8, 1.2, 1.5 etc. works
	 * very well without any artifacts!
	 * </ul> */
	public float displayScaleLargeScreenIfRetina = 1.0f;
	/** Scale factor to use on small screens with retina display, i.e. iPhone 4+, iPod 4+ (has no effect on non-retina screens).
	 * <ul>
	 * <li>1.0 = no scaling (everything is in pixels)
	 * <li>0.5 = LibGDX will behave as you would only have half the pixels. I.e. instead of 960x640 you will work in 480x320. This
	 * looks pixel perfect and will save you the trouble to create bigger graphics for the retina display.
	 * <li>any other value: scales the screens according to your scale factor. A scale factor of 0.75, 0.8, 1.2, 1.5 etc. works
	 * very well without any artifacts!
	 * </ul> */
	public float displayScaleSmallScreenIfRetina = 1.0f;
	/** Scale factor to use on large screens without retina display, i.e. iPad 1+2 (has no effect on retina screens).
	 * <ul>
	 * <li>1.0 = no scaling (everything is in pixels)
	 * <li>any other value: scales the screens according to your scale factor. A scale factor of 0.75, 0.8, 1.2, 1.5 etc. works
	 * very well without any artifacts!
	 * </ul> */
	public float displayScaleLargeScreenIfNonRetina = 1.0f;
	/** Scale factor to use on small screens without retina display, i.e. iPhone 1-3, iPod 1-3 (has no effect on retina screens).
	 * <ul>
	 * <li>1.0 = no scaling (everything is in pixels)
	 * <li>any other value: scales the screens according to your scale factor. A scale factor of 0.75, 0.8, 1.2, 1.5 etc. works
	 * very well without any artifacts!
	 * </ul> */
	public float displayScaleSmallScreenIfNonRetina = 1.0f;

	/** whether to use the accelerometer, default true **/
	public boolean useAccelerometer = true;
	/** the update interval to poll the accelerometer with, in seconds **/
	public float accelerometerUpdate = 0.05f;
	/** the update interval to poll the magnetometer with, in seconds **/
	public float magnetometerUpdate = 0.05f;

	/** whether to use the compass, default true **/
	public boolean useCompass = true;

	/** whether or not to allow background music from iPod **/
	public boolean allowIpod = true;
	
	/** whether or not the onScreenKeyboard should be closed on return key **/
	public boolean keyboardCloseOnReturn = true;

	/** Experimental, whether to enable OpenGL ES 3 if supported. If not supported it will fall-back to OpenGL ES 2.0.
	 *  When GL ES 3 is enabled, {@link com.badlogic.gdx.Gdx#gl30} can be used to access it's functionality.
	 * @deprecated this option is currently experimental and not yet fully supported, expect issues. */
	@Deprecated public boolean useGL30 = false;

	/** whether the status bar should be visible or not **/
	public boolean statusBarVisible = false;
	
	/** whether the home indicator should be hidden or not **/
	public boolean hideHomeIndicator = true;
	
	/** Whether to override the ringer/mute switch, see https://github.com/libgdx/libgdx/issues/4430 */
	public boolean overrideRingerSwitch = false;

	/** The maximum number of threads to use for network requests. Default is {@link Integer#MAX_VALUE}. */
	public int maxNetThreads = Integer.MAX_VALUE;

	ObjectMap<String, IOSDevice> knownDevices = IOSDevice.populateWithKnownDevices();

	/**
	 * adds device information for newer iOS devices, or overrides information for given ones
	 * @param classifier human readable device classifier
	 * @param machineString machine string returned by iOS
	 * @param ppi device's pixel per inch value
	 */
	public void addIosDevice(String classifier, String machineString, int ppi) {
		IOSDevice.addDeviceToMap(knownDevices, classifier, machineString, ppi);
	}
}
