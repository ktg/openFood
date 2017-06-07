/*
 * Copyright 2013 Google Inc. All rights reserved.
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

package uk.ac.nott.mrl.foodhacking;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.grafika.AspectFrameLayout;
import com.android.grafika.CameraUtils;
import com.android.grafika.TextureVideoEncoder;
import com.android.grafika.gles.FullFrameRect;
import uk.ac.nott.mrl.gles.program.LineProgram;
import uk.ac.nott.mrl.gles.program.TexturedShape2DProgram;
import uk.ac.nott.mrl.gles.shape.Line2D;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraCaptureActivity extends Activity
		implements SurfaceTexture.OnFrameAvailableListener
{
	/**
	 * Handles camera operation requests from other threads.  Necessary because the Camera
	 * must only be accessed from one thread.
	 * <p>
	 * The object is created on the UI thread, and all handlers run there.  Messages are
	 * sent from other threads, using sendMessage().
	 */
	static class CameraHandler extends Handler
	{
		static final int MSG_SET_SURFACE_TEXTURE = 0;

		// Weak reference to the Activity; only access this from the UI thread.
		private final WeakReference<CameraCaptureActivity> activityWeakReference;

		CameraHandler(CameraCaptureActivity activity)
		{
			activityWeakReference = new WeakReference<>(activity);
		}

		@Override  // runs on UI thread
		public void handleMessage(Message inputMessage)
		{
			int what = inputMessage.what;
			Log.d(TAG, "CameraHandler [" + this + "]: what=" + what);

			CameraCaptureActivity activity = activityWeakReference.get();
			if (activity == null)
			{
				Log.w(TAG, "CameraHandler.handleMessage: activity is null");
				return;
			}

			switch (what)
			{
				case MSG_SET_SURFACE_TEXTURE:
					activity.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
					break;
				default:
					throw new RuntimeException("unknown msg " + what);
			}
		}

		/**
		 * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
		 * attempts to access a stale Activity through a handler are caught.
		 */
		void invalidateHandler()
		{
			activityWeakReference.clear();
		}
	}

	/**
	 * Renderer object for our GLSurfaceView.
	 * <p>
	 * Do not call any methods here directly from another thread -- use the
	 * GLSurfaceView#queueEvent() call.
	 */
	private static class CameraSurfaceRenderer implements GLSurfaceView.Renderer
	{
		private static final String TAG = "SurfaceRenderer";
		private static final boolean VERBOSE = false;

		private final float[] surfaceTextureMatrix = new float[16];
		private final Line2D tri = new Line2D();
		private CameraCaptureActivity.CameraHandler cameraHandler;
		private TextureVideoEncoder movieEncoder;
		private File outputFile;
		private FullFrameRect fullScreen;
		private int textureID;
		private SurfaceTexture surfaceTexture;
		private boolean recordingEnabled;
		private RecordingStatus recordingStatus;
		private int frameCount;
		private LineProgram program;
		// width/height of the incoming camera preview frames
		private boolean incomingSizeUpdated;
		private int incomingWidth;
		private int incomingHeight;

		/**
		 * Constructs CameraSurfaceRenderer.
		 * <p>
		 *
		 * @param cameraHandler Handler for communicating with UI thread
		 * @param movieEncoder  video encoder object
		 * @param outputFile    output file for encoded video; forwarded to movieEncoder
		 */
		CameraSurfaceRenderer(CameraCaptureActivity.CameraHandler cameraHandler,
		                      TextureVideoEncoder movieEncoder, File outputFile)
		{
			this.cameraHandler = cameraHandler;
			this.movieEncoder = movieEncoder;
			this.outputFile = outputFile;

			textureID = -1;

			recordingStatus = RecordingStatus.off;
			recordingEnabled = false;
			frameCount = -1;

			incomingSizeUpdated = false;
			incomingWidth = incomingHeight = -1;
		}

		@Override
		public void onSurfaceCreated(GL10 unused, EGLConfig config)
		{
			Log.d(TAG, "onSurfaceCreated");

			// We're starting up or coming back.  Either way we've got a new EGLContext that will
			// need to be shared with the video encoder, so figure out if a recording is already
			// in progress.
			recordingEnabled = movieEncoder.isRecording();
			if (recordingEnabled)
			{
				recordingStatus = RecordingStatus.resumed;
			}
			else
			{
				recordingStatus = RecordingStatus.off;
			}

			// Set up the texture blitter that will be used for on-screen display.  This
			// is *not* applied to the recording, because that uses a separate shader.
			fullScreen = new FullFrameRect(new TexturedShape2DProgram(TexturedShape2DProgram.ProgramType.TEXTURE_EXT));

			textureID = fullScreen.createTextureObject();

			// Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
			// have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
			// available messages will arrive on the main thread.
			surfaceTexture = new SurfaceTexture(textureID);

			program = new LineProgram();

			// Tell the UI thread to enable the camera preview.
			cameraHandler.sendMessage(cameraHandler.obtainMessage(
					CameraCaptureActivity.CameraHandler.MSG_SET_SURFACE_TEXTURE, surfaceTexture));
		}

		@Override
		public void onSurfaceChanged(GL10 unused, int width, int height)
		{
			Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
		}

		@Override
		public void onDrawFrame(GL10 unused)
		{
			if (VERBOSE) { Log.d(TAG, "onDrawFrame tex=" + textureID); }
			boolean showBox;

			// Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
			// was there before.
			surfaceTexture.updateTexImage();

			// If the recording state is changing, take care of it here.  Ideally we wouldn't
			// be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
			// makes it hard to do elsewhere.
			if (recordingEnabled)
			{
				switch (recordingStatus)
				{
					case off:
						Log.d(TAG, "START recording");
						// start recording
						movieEncoder.startRecording(new TextureVideoEncoder.EncoderConfig(
								outputFile, 640, 480, 1000000, EGL14.eglGetCurrentContext()));
						recordingStatus = RecordingStatus.on;
						break;
					case resumed:
						Log.d(TAG, "RESUME recording");
						movieEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
						recordingStatus = RecordingStatus.on;
						break;
					case on:
						// yay
						break;
					default:
						throw new RuntimeException("unknown status " + recordingStatus);
				}
			}
			else
			{
				switch (recordingStatus)
				{
					case on:
					case resumed:
						// stop recording
						Log.d(TAG, "STOP recording");
						movieEncoder.stopRecording();
						recordingStatus = RecordingStatus.off;
						break;
					case off:
						// yay
						break;
					default:
						throw new RuntimeException("unknown status " + recordingStatus);
				}
			}

			// Set the video encoder's texture name.  We only need to do this once, but in the
			// current implementation it has to happen after the video encoder is started, so
			// we just do it here.
			//
			// TODO: be less lame.
			movieEncoder.setTextureId(textureID);

			// Tell the video encoder thread that a new frame is available.
			// This will be ignored if we're not actually recording.
			movieEncoder.frameAvailable(surfaceTexture);

			if (incomingWidth <= 0 || incomingHeight <= 0)
			{
				// Texture size isn't set yet.  This is only used for the filters, but to be
				// safe we can just skip drawing while we wait for the various races to resolve.
				// (This seems to happen if you toggle the screen off/on with power button.)
				Log.i(TAG, "Drawing before incoming texture size set; skipping");
				return;
			}

			if (incomingSizeUpdated)
			{
				fullScreen.getProgram().setTexSize(incomingWidth, incomingHeight);
				incomingSizeUpdated = false;
			}

			// Draw the video frame.
			surfaceTexture.getTransformMatrix(surfaceTextureMatrix);
			fullScreen.drawFrame(textureID, surfaceTextureMatrix);

			program.draw(surfaceTextureMatrix, tri);

			// Draw a flashing box if we're recording.  This only appears on screen.
			if (recordingStatus == RecordingStatus.on && (++frameCount & 0x04) == 0)
			{
				drawBox();
			}
		}

		/**
		 * Notifies the renderer thread that the activity is pausing.
		 * <p>
		 * For best results, call this *after* disabling Camera preview.
		 */
		void notifyPausing()
		{
			if (surfaceTexture != null)
			{
				Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
				surfaceTexture.release();
				surfaceTexture = null;
				program.release();
				program = null;
			}
			if (fullScreen != null)
			{
				fullScreen.release(false);     // assume the GLSurfaceView EGL context is about
				fullScreen = null;             //  to be destroyed
			}
			incomingWidth = incomingHeight = -1;
		}

		/**
		 * Notifies the renderer that we want to stop or start recording.
		 */
		void changeRecordingState(boolean isRecording)
		{
			Log.d(TAG, "changeRecordingState: was " + recordingEnabled + " now " + isRecording);
			recordingEnabled = isRecording;
		}

		/**
		 * Records the size of the incoming camera preview frames.
		 * <p>
		 * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
		 * so we assume it could go either way.  (Fortunately they both run on the same thread,
		 * so we at least know that they won't execute concurrently.)
		 */
		void setCameraPreviewSize(int width, int height)
		{
			Log.d(TAG, "setCameraPreviewSize");
			incomingWidth = width;
			incomingHeight = height;
			incomingSizeUpdated = true;
		}

		/**
		 * Draws a red box in the corner.
		 */
		private void drawBox()
		{
			GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
			GLES20.glScissor(0, 0, 100, 100);
			GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
		}
	}

	private enum RecordingStatus
	{
		off, on, resumed
	}

	private static final int CAMERA_PERMISSIONS = 93467;
	private static final String TAG = "CameraCapture";
	private static final boolean VERBOSE = false;
	private GLSurfaceView glSurfaceView;
	private CameraSurfaceRenderer cameraSurfaceRenderer;
	private Camera camera;
	private CameraHandler cameraHandler;
	private boolean recordingEnabled;      // controls button state
	private int cameraPreviewWidth;
	private int cameraPreviewHeight;
	// this is static so it survives activity restarts
	private static TextureVideoEncoder movieEncoder = new TextureVideoEncoder();

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
	{
		switch (requestCode)
		{
			case CAMERA_PERMISSIONS:
			{
			}
		}
	}

	/**
	 * onClick handler for "record" button.
	 */
	public void clickToggleRecording(@SuppressWarnings("unused") View unused)
	{
		recordingEnabled = !recordingEnabled;
		glSurfaceView.queueEvent(new Runnable()
		{
			@Override
			public void run()
			{
				// notify the renderer that we want to change the encoder's state
				cameraSurfaceRenderer.changeRecordingState(recordingEnabled);
			}
		});
		updateControls();
	}

	@Override
	public void onFrameAvailable(SurfaceTexture st)
	{
		// The SurfaceTexture uses this to signal the availability of a new frame.  The
		// thread that "owns" the external texture associated with the SurfaceTexture (which,
		// by virtue of the context being shared, *should* be either one) needs to call
		// updateTexImage() to latch the buffer.
		//
		// Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
		// This feels backward -- we want recording to be prioritized over rendering -- but
		// since recording is only enabled some of the time it's easier to do it this way.
		//
		// Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
		// the main UI thread.  Fortunately, requestRender() can be called from any thread,
		// so it doesn't really matter.
		if (VERBOSE) Log.d(TAG, "ST onFrameAvailable");
		glSurfaceView.requestRender();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_capture);

		File outputFile = new File(getFilesDir(), "camera-test.mp4");
		TextView fileText = (TextView) findViewById(R.id.cameraFileLabel);
		fileText.setText(outputFile.toString());

		// Define a handler that receives camera-control messages from other threads.  All calls
		// to Camera must be made on the same thread.  Note we create this before the renderer
		// thread, so we know the fully-constructed object will be visible.
		cameraHandler = new CameraHandler(this);

		recordingEnabled = movieEncoder.isRecording();

		// Configure the GLSurfaceView.  This will start the Renderer thread, with an
		// appropriate EGL context.
		glSurfaceView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
		glSurfaceView.setEGLContextClientVersion(2);     // select GLES 2.0
		cameraSurfaceRenderer = new CameraSurfaceRenderer(cameraHandler, movieEncoder, outputFile);
		glSurfaceView.setRenderer(cameraSurfaceRenderer);
		glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

		Log.d(TAG, "onCreate complete: " + this);
	}

	@Override
	protected void onResume()
	{
		Log.d(TAG, "onResume -- acquiring camera");
		super.onResume();

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSIONS);
		}

		updateControls();
		openCamera(1280, 720);      // updates cameraPreviewWidth/Height

		// Set the preview aspect ratio.
		AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
		layout.setAspectRatio((double) cameraPreviewWidth / cameraPreviewHeight);

		glSurfaceView.onResume();
		glSurfaceView.queueEvent(new Runnable()
		{
			@Override
			public void run()
			{
				cameraSurfaceRenderer.setCameraPreviewSize(cameraPreviewWidth, cameraPreviewHeight);
			}
		});
		Log.d(TAG, "onResume complete: " + this);
	}

	@Override
	protected void onPause()
	{
		Log.d(TAG, "onPause -- releasing camera");
		super.onPause();
		releaseCamera();
		glSurfaceView.queueEvent(new Runnable()
		{
			@Override
			public void run()
			{
				// Tell the renderer that it's about to be paused so it can clean up.
				cameraSurfaceRenderer.notifyPausing();
			}
		});
		glSurfaceView.onPause();
		Log.d(TAG, "onPause complete");
	}

	@Override
	protected void onDestroy()
	{
		Log.d(TAG, "onDestroy");
		super.onDestroy();
		cameraHandler.invalidateHandler();     // paranoia
	}

	/**
	 * Opens a camera, and attempts to establish preview mode at the specified width and height.
	 * <p>
	 * Sets cameraPreviewWidth and cameraPreviewHeight to the actual width/height of the preview.
	 */
	@SuppressWarnings("deprecation")
	private void openCamera(int desiredWidth, int desiredHeight)
	{
		if (camera != null)
		{
			throw new RuntimeException("camera already initialized");
		}

		Camera.CameraInfo info = new Camera.CameraInfo();

		// Try to find a front-facing camera (e.g. for videoconferencing).
		int numCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numCameras; i++)
		{
			Camera.getCameraInfo(i, info);
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
			{
				camera = Camera.open(i);
				break;
			}
		}
		if (camera == null)
		{
			Log.d(TAG, "No front-facing camera found; opening default");
			camera = Camera.open();    // opens first back-facing camera
		}
		if (camera == null)
		{
			throw new RuntimeException("Unable to open camera");
		}

		Camera.Parameters parms = camera.getParameters();

		CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

		// Give the camera a hint that we're recording video.  This can have a big
		// impact on frame rate.
		parms.setRecordingHint(true);

		// leave the frame rate set to default
		camera.setParameters(parms);

		int[] fpsRange = new int[2];
		Camera.Size mCameraPreviewSize = parms.getPreviewSize();
		parms.getPreviewFpsRange(fpsRange);
		String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
		if (fpsRange[0] == fpsRange[1])
		{
			previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
		}
		else
		{
			previewFacts += " @[" + (fpsRange[0] / 1000.0) +
					" - " + (fpsRange[1] / 1000.0) + "] fps";
		}
		TextView text = (TextView) findViewById(R.id.cameraParamsLabel);
		text.setText(previewFacts);

		cameraPreviewWidth = mCameraPreviewSize.width;
		cameraPreviewHeight = mCameraPreviewSize.height;
	}

	/**
	 * Stops camera preview, and releases the camera to the system.
	 */
	private void releaseCamera()
	{
		if (camera != null)
		{
			camera.stopPreview();
			camera.release();
			camera = null;
			Log.d(TAG, "releaseCamera -- done");
		}
	}

	/**
	 * Updates the on-screen controls to reflect the current state of the app.
	 */
	private void updateControls()
	{
		ImageButton toggleButton = (ImageButton) findViewById(R.id.recordButton);
		if (recordingEnabled)
		{
			toggleButton.setImageResource(R.drawable.ic_stop_48dp);
			toggleButton.setColorFilter(ContextCompat.getColor(this, android.R.color.black));
			toggleButton.setContentDescription(getString(R.string.toggleRecordingOff));
		}
		else
		{
			toggleButton.setImageResource(R.drawable.ic_record_48dp);
			toggleButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
			toggleButton.setContentDescription(getString(R.string.toggleRecordingOn));
		}
	}

	/**
	 * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
	 */
	private void handleSetSurfaceTexture(SurfaceTexture surfaceTexture)
	{
		surfaceTexture.setOnFrameAvailableListener(this);
		try
		{
			camera.setPreviewTexture(surfaceTexture);
		}
		catch (IOException ioe)
		{
			throw new RuntimeException(ioe);
		}
		camera.startPreview();
	}
}