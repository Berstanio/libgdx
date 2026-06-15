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

import apple.foundation.NSString;
import apple.foundation.c.Foundation;
import com.badlogic.gdx.ApplicationLogger;

/** Default implementation of {@link ApplicationLogger} for ios */
public class IOSApplicationLogger implements ApplicationLogger {

	@Override
	public void log (String tag, String message) {
		apple.foundation.c.Foundation.NSLog("%@", NSString.alloc().initWithString("[info] " + tag + ": " + message));
	}

	@Override
	public void log (String tag, String message, Throwable exception) {
		apple.foundation.c.Foundation.NSLog("%@", NSString.alloc().initWithString("[info] " + tag + ": " + message));
		exception.printStackTrace();
	}

	@Override
	public void error (String tag, String message) {
		apple.foundation.c.Foundation.NSLog("%@", NSString.alloc().initWithString("[error] " + tag + ": " + message));
	}

	@Override
	public void error (String tag, String message, Throwable exception) {
		apple.foundation.c.Foundation.NSLog("%@", NSString.alloc().initWithString("[error] " + tag + ": " + message));
		exception.printStackTrace();
	}

	@Override
	public void debug (String tag, String message) {
		apple.foundation.c.Foundation.NSLog("%@", NSString.alloc().initWithString("[debug] " + tag + ": " + message));
	}

	@Override
	public void debug (String tag, String message, Throwable exception) {
		Foundation.NSLog("%@", NSString.alloc().initWithString("[debug] " + tag + ": " + message));
		exception.printStackTrace();
	}
}
