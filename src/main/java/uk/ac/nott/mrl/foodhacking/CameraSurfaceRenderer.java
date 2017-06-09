package uk.ac.nott.mrl.foodhacking;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.android.grafika.TextureVideoEncoder;
import com.android.grafika.gles.FullFrameRect;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import uk.ac.nott.mrl.gles.program.GraphProgram;
import uk.ac.nott.mrl.gles.program.TexturedShape2DProgram;

/**
 * Renderer object for our GLSurfaceView.
 * <p>
 * Do not call any methods here directly from another thread -- use the
 * GLSurfaceView#queueEvent() call.
 */
class CameraSurfaceRenderer implements GLSurfaceView.Renderer
{
	private enum RecordingStatus
	{
		off, on, resumed
	}

	private static final String TAG = "SurfaceRenderer";
	private static final boolean VERBOSE = false;

	private final float[] surfaceTextureMatrix = new float[16];
	private CameraHandler cameraHandler;
	private TextureVideoEncoder movieEncoder;
	private File outputFile;
	private FullFrameRect fullScreen;
	private int textureID;
	private SurfaceTexture surfaceTexture;
	private boolean recordingEnabled;
	private RecordingStatus recordingStatus;
	private int frameCount;
	private final List<GraphProgram> graphs = new ArrayList<>();
	// width/height of the incoming camera preview frames
	private boolean incomingSizeUpdated;
	private int incomingWidth;
	private int incomingHeight;

	private GraphProgram accelGraph;
	private GraphProgram gyroGraph;

	/**
	 * Constructs CameraSurfaceRenderer.
	 * <p>
	 *
	 * @param cameraHandler Handler for communicating with UI thread
	 * @param movieEncoder  video encoder object
	 */
	CameraSurfaceRenderer(CameraHandler cameraHandler,
	                      TextureVideoEncoder movieEncoder)
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

	public void setOutputFile(File outputFile)
	{
		this.outputFile = outputFile;
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


		accelGraph = new GraphProgram();
		accelGraph.setColour(1, 0, 0);
		gyroGraph = new GraphProgram();
		gyroGraph.setColour(0,1,0);
		graphs.add(gyroGraph);
		graphs.add(accelGraph);

		movieEncoder.setGraphs(graphs);

		// Tell the UI thread to enable the camera preview.
		cameraHandler.sendMessage(cameraHandler.obtainMessage(CameraHandler.MSG_SET_SURFACE_TEXTURE, surfaceTexture));
	}

	void addAccel(float x)
	{
		accelGraph.add(x);
	}

	void addGyro(float x)
	{
		gyroGraph.add(x);
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
							outputFile, 1280, 720, 5242880, EGL14.eglGetCurrentContext()));
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

		for (GraphProgram graph : graphs)
		{
			graph.draw(surfaceTextureMatrix);
		}

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
