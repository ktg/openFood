package uk.ac.nott.mrl.foodhacking;

import android.opengl.GLES20;

import com.android.grafika.gles.GlUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Line
{

	private static final String VertexShaderCode =
			// This matrix member variable provides a hook to manipulate
			// the coordinates of the objects that use this vertex shader
			"uniform mat4 uMVPMatrix;" +

					"attribute vec4 vPosition;" +
					"void main() {" +
					// the matrix must be included as a modifier of gl_Position
					"  gl_Position = uMVPMatrix * vPosition;" +
					"}";

	private static final String FragmentShaderCode =
			"precision mediump float;" +
					"uniform vec4 vColor;" +
					"void main() {" +
					"  gl_FragColor = vColor;" +
					"}";

	private int GlProgram;

	// number of coordinates per vertex in this array
	private static final int COORDS_PER_VERTEX = 2;
	private List<Float> points = new ArrayList<>();

	private static final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

	// Set color with red, green, blue and alpha (opacity) values
	private float color[] = {1.0f, 1.0f, 1.0f, 1.0f};

	public Line()
	{
		//int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
		//int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

		//GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
		//GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
		//GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
		//GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
	}

	public static float[] convertFloats(List<Float> points)
	{
		float[] ret = new float[points.size()];
		Iterator<Float> iterator = points.iterator();
		for (int i = 0; i < ret.length; i++)
		{
			ret[i] = iterator.next();
		}
		return ret;
	}

	private static int loadShader(int type, String shaderCode)
	{
		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = GLES20.glCreateShader(type);
		// add the source code to the shader and compile it
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);
		return shader;
	}

	public void addPoint(float x, float y)
	{
		points.add(x);
		points.add(y);
	}

	public void addPoint(float x)
	{
		int size = points.size() / 2;
		points.add(x);
		points.add(Float.valueOf(size));
	}

	public void SetColor(float red, float green, float blue, float alpha)
	{
		color[0] = red;
		color[1] = green;
		color[2] = blue;
		color[3] = alpha;
	}

	public void draw(float[] mvpMatrix)
	{
		float[] LineCoords = convertFloats(points);

		// initialize vertex byte buffer for shape coordinates
		ByteBuffer bb = ByteBuffer.allocateDirect(
				// (number of coordinate values * 4 bytes per float)
				LineCoords.length * 4);
		// use the device hardware's native byte order
		bb.order(ByteOrder.nativeOrder());

		// create a floating point buffer from the ByteBuffer
		FloatBuffer vertexBuffer = bb.asFloatBuffer();
		// add the coordinates to the FloatBuffer
		vertexBuffer.put(LineCoords);
		// set the buffer to read the first coordinate
		vertexBuffer.position(0);
		int VertexCount = points.size() / COORDS_PER_VERTEX;


		// Add program to OpenGL ES environment
		//GLES20.glUseProgram(GlProgram);
		//GlUtil.checkGlError("draw start");

		// get handle to vertex shader's vPosition member
		//int positionHandle = GLES20.glGetAttribLocation(GlProgram, "vPosition");

		// Enable a handle to the triangle vertices
		//GLES20.glEnableVertexAttribArray(positionHandle);
		//GlUtil.checkGlError("draw start");

		// Prepare the triangle coordinate data
		//GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
		//		GLES20.GL_FLOAT, false,
		//		VertexStride, vertexBuffer);
		//GlUtil.checkGlError("draw start");

		// get handle to fragment shader's vColor member
		//int colorHandle = GLES20.glGetUniformLocation(GlProgram, "vColor");

		// Set color for drawing the triangle
		//GLES20.glUniform4fv(colorHandle, 1, color, 0);
		//GlUtil.checkGlError("draw start");

		// get handle to shape's transformation matrix
		//int MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");
		//ArRenderer.checkGlError("glGetUniformLocation");

		// Apply the projection and view transformation
		//GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);
		///ArRenderer.checkGlError("glUniformMatrix4fv");

		// Draw the triangle
		GLES20.glDrawArrays(GLES20.GL_LINES, 0, VertexCount);
		GlUtil.checkGlError("draw start");

		// Disable vertex array
		//GLES20.glDisableVertexAttribArray(positionHandle);
		//GlUtil.checkGlError("draw start");
	}
}