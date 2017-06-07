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

package uk.ac.nott.mrl.gles.shape;

import com.android.grafika.gles.GlUtil;

import java.nio.FloatBuffer;

/**
 * Base class for stuff we like to draw.
 */
public abstract class Shape2D
{
	private static final int SIZEOF_FLOAT = 4;
	private static final int VERTEX_STRIDE = SIZEOF_FLOAT * 2;

	private final float colour[] = {1.0f, 1.0f, 1.0f, 1.0f};
	private FloatBuffer vertexArray;
	private final int vertexCount;

	Shape2D(float[] vertexArray, int vertexCount)
	{
		this.vertexArray = GlUtil.createFloatBuffer(vertexArray);
		this.vertexCount = vertexCount;
	}

	Shape2D(int vertexCount)
	{
		this.vertexCount = vertexCount;
	}

	/**
	 * Sets color to use for flat-shaded rendering.  Has no effect on textured rendering.
	 */
	public void setColor(float red, float green, float blue)
	{
		colour[0] = red;
		colour[1] = green;
		colour[2] = blue;
	}

	public float[] getColour()
	{
		return colour;
	}

	void setVertexArray(float[] coords)
	{
		if(coords.length != vertexCount * getCoordsPerVertex())
		{
			throw new IllegalArgumentException("Wrong number of coords");
		}
		vertexArray = GlUtil.createFloatBuffer(coords);
	}

	/**
	 * Returns the array of vertices.
	 * <p>
	 * To avoid allocations, this returns internal state.  The caller must not modify it.
	 */
	public FloatBuffer getVertexArray()
	{
		return vertexArray;
	}

	/**
	 * Returns the number of vertices stored in the vertex array.
	 */
	public int getVertexCount()
	{
		return vertexCount;
	}

	/**
	 * Returns the width, in bytes, of the data for each vertex.
	 */
	public int getVertexStride()
	{
		return VERTEX_STRIDE;
	}

	public int getCoordsPerVertex()
	{
		return 2;
	}
}