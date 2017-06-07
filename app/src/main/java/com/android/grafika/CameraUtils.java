/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika;

import android.hardware.Camera;
import android.util.Log;

import java.util.List;

/**
 * Camera-related utility functions.
 */
public class CameraUtils
{
	private static final String TAG = "CameraUtils";

	/**
	 * Attempts to find a preview size that matches the provided width and height (which
	 * specify the dimensions of the encoded video).  If it fails to find a match it just
	 * uses the default preview size for video.
	 * <p>
	 * TODO: should do a best-fit match, e.g.
	 * https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
	 */
	public static void choosePreviewSize(Camera.Parameters parms, int width, int height)
	{
		// We should make sure that the requested MPEG size is less than the preferred
		// size, and has the same aspect ratio.
		Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
		if (ppsfv != null)
		{
			Log.d(TAG, "Camera preferred preview size for video is " +
					ppsfv.width + "x" + ppsfv.height);
		}

		//for (Camera.Size size : parms.getSupportedPreviewSizes()) {
		//    Log.d(TAG, "supported: " + size.width + "x" + size.height);
		//}

		for (Camera.Size size : parms.getSupportedPreviewSizes())
		{
			if (size.width == width && size.height == height)
			{
				parms.setPreviewSize(width, height);
				return;
			}
		}

		Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
		if (ppsfv != null)
		{
			parms.setPreviewSize(ppsfv.width, ppsfv.height);
		}
		// else use whatever the default size is
	}
}
