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

package uk.ac.nott.mrl.gles.program;

import android.opengl.GLES20;
import android.util.Log;

import com.android.grafika.gles.GlUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GraphProgram
{
	private static final int SIZEOF_FLOAT = 4;
	private static final int VERTEX_STRIDE = SIZEOF_FLOAT * 2;
	private static final String TAG = GlUtil.TAG;

	private static final String VERTEX_SHADER =
			"uniform mat4 uMVPMatrix;" +
					"attribute vec4 aPosition;" +
					"void main() {" +
					"    gl_Position = uMVPMatrix * aPosition;" +
					"}";

	private static final String FRAGMENT_SHADER =
			"precision mediump float;" +
					"uniform vec4 uColor;" +
					"void main() {" +
					"    gl_FragColor = uColor;" +
					"}";

	private final int MAX_SIZE = 200;

	// Handles to the GL program and various components of it.
	private int programHandle = -1;
	private int colorLocation = -1;
	private int matrixLocation = -1;
	private int positionLocation = -1;
	private final float[] colour = {1f, 1f, 1f, 1f};
	private final FloatBuffer points;
	private final float[] values = new float[MAX_SIZE];
	private int size = 0;
	private int offset = 0;
	private boolean bufferValid = false;
	private float min = Float.MAX_VALUE;
	private float max = Float.MIN_VALUE;

	private static final float left = 1.8f;
	private static final float right = 0.2f;
	private static final float top = 0.8f;
	private static final float bottom = -0.8f;

	/**
	 * Prepares the program in the current EGL context.
	 */
	public GraphProgram()
	{
		programHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
		if (programHandle == 0)
		{
			throw new RuntimeException("Unable to create program");
		}
		Log.d(TAG, "Created program " + programHandle);

		// get locations of attributes and uniforms

		ByteBuffer bb = ByteBuffer.allocateDirect(MAX_SIZE * VERTEX_STRIDE);
		bb.order(ByteOrder.nativeOrder());
		points = bb.asFloatBuffer();

		positionLocation = GLES20.glGetAttribLocation(programHandle, "aPosition");
		GlUtil.checkLocation(positionLocation, "aPosition");
		matrixLocation = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
		GlUtil.checkLocation(matrixLocation, "uMVPMatrix");
		colorLocation = GLES20.glGetUniformLocation(programHandle, "uColor");
		GlUtil.checkLocation(colorLocation, "uColor");
	}

	/**
	 * Releases the program.
	 */
	public void release()
	{
		GLES20.glDeleteProgram(programHandle);
		programHandle = -1;
	}

	public synchronized void add(float value)
	{
		values[offset] = value;
		min = Math.min(value, min);
		max = Math.max(value, max);
		size = Math.min(size + 1, MAX_SIZE);
		offset = (offset + 1) % MAX_SIZE;
		bufferValid = false;
	}

	public void setColour(final float r, final float g, final float b)
	{
		colour[0] = r;
		colour[1] = g;
		colour[2] = b;
	}

	private synchronized FloatBuffer getValidBuffer()
	{
		if (!bufferValid)
		{
			points.position(0);
			for(int index = 0; index < size; index++)
			{
				float value = values[(offset + index) % size];
				float scaledValue = ((value - min) / (max - min) * (top - bottom)) + bottom;

				//Log.i(TAG, "x=" + ((index * (right - left) / size) + left) + ", y=" + scaledValue);
				points.put((index * (right - left) / (size - 1)) + left);
				points.put(scaledValue);
			}
			points.position(0);
			bufferValid = true;
		}
		return points;
	}

	public void draw(float[] matrix)
	{
		GlUtil.checkGlError("draw start");

		// Select the program.
		GLES20.glUseProgram(programHandle);
		GlUtil.checkGlError("glUseProgram");

		// Copy the model / view / projection matrix over.
		GLES20.glUniformMatrix4fv(matrixLocation, 1, false, matrix, 0);
		GlUtil.checkGlError("glUniformMatrix4fv");

		// Copy the color vector in.
		GLES20.glUniform4fv(colorLocation, 1, colour, 0);
		GlUtil.checkGlError("glUniform4fv ");

		// Enable the "aPosition" vertex attribute.
		GLES20.glEnableVertexAttribArray(positionLocation);
		GlUtil.checkGlError("glEnableVertexAttribArray");

		// Connect vertexBuffer to "aPosition".
		GLES20.glVertexAttribPointer(positionLocation, 2,
				GLES20.GL_FLOAT, false, VERTEX_STRIDE, getValidBuffer());
		GlUtil.checkGlError("glVertexAttribPointer");

		// Draw the rect.
		GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, size);
		GlUtil.checkGlError("glDrawArrays");

		// Done -- disable vertex array and program.
		GLES20.glDisableVertexAttribArray(positionLocation);
		GLES20.glUseProgram(0);
	}
}
