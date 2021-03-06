package uk.ac.nott.mrl.foodhacking;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.grafika.AspectFrameLayout;
import com.android.grafika.TextureVideoEncoder;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.data.MagneticField;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.MagnetometerBmm150;

import java.io.File;
import java.io.IOException;

import bolts.Continuation;

public class CameraCaptureActivity extends Activity
		implements SurfaceTexture.OnFrameAvailableListener, ServiceConnection
{
	private static final int CAMERA_PERMISSIONS = 93467;
	private static final String TAG = CameraCaptureActivity.class.getSimpleName();
	private static final boolean VERBOSE = false;
	private static final String MW_MAC_ADDRESS = "C8:5F:91:F8:A8:A6";
	private static final int PREFERRED_WIDTH = 1280;
	private static final int PREFERRED_HEIGHT = 720;

	private GLSurfaceView glSurfaceView;
	private CameraSurfaceRenderer cameraSurfaceRenderer;
	private Camera camera;
	private CameraHandler cameraHandler;
	private boolean recordingEnabled;      // controls button state
	private int cameraPreviewWidth;
	private int cameraPreviewHeight;
	// this is static so it survives activity restarts
	private static TextureVideoEncoder movieEncoder = new TextureVideoEncoder();
	private TextView fileText;

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults)
	{
		switch (requestCode)
		{
			case CAMERA_PERMISSIONS:
			{
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				{
					openCamera();
				}
			}
		}
	}

	/**
	 * onClick handler for "record" button.
	 */
	public void clickToggleRecording(@SuppressWarnings("unused") View unused)
	{
		recordingEnabled = !recordingEnabled;
		if (recordingEnabled)
		{
			int index = 1;
			File outputFile = new File(getFilesDir(), "output_" + index + ".mp4");
			while (outputFile.exists())
			{
				index++;
				outputFile = new File(getFilesDir(), "output_" + index + ".mp4");
			}
			cameraSurfaceRenderer.setOutputFile(outputFile);
			fileText.setText(outputFile.getAbsolutePath());
		}
		glSurfaceView.queueEvent(() -> cameraSurfaceRenderer.changeRecordingState(recordingEnabled));
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

	private void connectToBoard(MetaWearBoard board)
	{
		board.connectAsync(1000).continueWith((Continuation<Void, Void>) task ->
		{
			if (task.isFaulted())
			{
				Log.i(TAG, "Failed to connect to metawear");
				Log.i(TAG, task.getError().getLocalizedMessage());
				connectToBoard(board);
			}
			else
			{
				Log.i(TAG, "Connected");
				final Accelerometer accelerometer = board.getModule(Accelerometer.class);
				accelerometer.acceleration()
						.addRouteAsync(source -> source.stream((Subscriber) (data, env) ->
						{
							cameraSurfaceRenderer.addAccel(data.value(Acceleration.class).x());
						}))
						.continueWith((Continuation<Route, Void>) task1 ->
						{
							Log.i(TAG, "Accel: " + task1.getError());
							accelerometer.acceleration().start();
							accelerometer.start();
							return null;
						});

				final GyroBmi160 gyro = board.getModule(GyroBmi160.class);
				gyro.packedAngularVelocity()
						.addRouteAsync(source -> source.stream((Subscriber) (data, env) ->
						{
							cameraSurfaceRenderer.addGyro(data.value(AngularVelocity.class).x());
						}))
						.continueWith((Continuation<Route, Void>) task1 ->
						{
							Log.i(TAG, "Magnet: " + task1.getError());
							gyro.packedAngularVelocity().start();
							gyro.start();
							return null;
						});
			}
			return null;
		});
	}

	@Override
	public void onServiceConnected(final ComponentName name, final IBinder service)
	{
		Log.d(TAG, "Service Connected");
		// Typecast the binder to the service's LocalBinder class

		final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

		// Create a MetaWear board object for the Bluetooth Device
		MetaWearBoard board = ((BtleService.LocalBinder) service).getMetaWearBoard(remoteDevice);
		connectToBoard(board);
	}

	@Override
	public void onServiceDisconnected(final ComponentName name)
	{

	}

	/**
	 * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
	 */
	void handleSetSurfaceTexture(SurfaceTexture surfaceTexture)
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

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_capture);

		fileText = (TextView) findViewById(R.id.cameraFileLabel);
		//fileText.setText(outputFile.toString());

		// Define a handler that receives camera-control messages from other threads.  All calls
		// to Camera must be made on the same thread.  Note we create this before the renderer
		// thread, so we know the fully-constructed object will be visible.
		cameraHandler = new CameraHandler(this);

		recordingEnabled = movieEncoder.isRecording();

		// Configure the GLSurfaceView.  This will start the Renderer thread, with an
		// appropriate EGL context.
		glSurfaceView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
		glSurfaceView.setEGLContextClientVersion(2);     // select GLES 2.0
		cameraSurfaceRenderer = new CameraSurfaceRenderer(cameraHandler, movieEncoder);
		glSurfaceView.setRenderer(cameraSurfaceRenderer);
		glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

		// Bind the service when the activity is created
		getApplicationContext().bindService(new Intent(this, BtleService.class),
				this, Context.BIND_AUTO_CREATE);

		Log.d(TAG, "onCreate complete: " + this);
	}

	private void openCamera()
	{
		updateControls();
		openCamera(PREFERRED_WIDTH, PREFERRED_HEIGHT);

		// Set the preview aspect ratio.
		AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
		layout.setAspectRatio((double) cameraPreviewWidth / cameraPreviewHeight);

		glSurfaceView.onResume();
		glSurfaceView.queueEvent(() -> cameraSurfaceRenderer.setCameraPreviewSize(cameraPreviewWidth, cameraPreviewHeight));
		Log.d(TAG, "onResume complete: " + this);
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
		else
		{
			openCamera();
		}
	}

	@Override
	protected void onPause()
	{
		Log.d(TAG, "onPause -- releasing camera");
		super.onPause();
		releaseCamera();
		glSurfaceView.queueEvent(() ->
		{
			// Tell the renderer that it's about to be paused so it can clean up.
			cameraSurfaceRenderer.notifyPausing();
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
		// Unbind the service when the activity is destroyed
		getApplicationContext().unbindService(this);
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

		parms.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);

		choosePreviewSize(parms, desiredWidth, desiredHeight);

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

	@SuppressWarnings("deprecation")
	private static void choosePreviewSize(Camera.Parameters parms, int width, int height)
	{
		final Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
		if (ppsfv != null)
		{
			Log.d(TAG, "Camera preferred preview size for video is " +
					ppsfv.width + "x" + ppsfv.height);
		}

		for (final Camera.Size size : parms.getSupportedPreviewSizes())
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
}