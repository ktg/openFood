package uk.ac.nott.mrl.gles.shape;

import com.android.grafika.gles.GlUtil;

import java.nio.FloatBuffer;

public class FullRectangle2D extends Shape2D
{
	/**
	 * A "full" square, extending from -1 to +1 in both dimensions.  When the model/view/projection
	 * matrix is identity, this will exactly cover the viewport.
	 * <p>
	 * The texture coordinates are Y-inverted relative to RECTANGLE.  (This seems to work out
	 * right with external textures from SurfaceTexture.)
	 */
	private static final float FULL_RECTANGLE_COORDS[] = {
			-1.0f, -1.0f,   // 0 bottom left
			1.0f, -1.0f,   // 1 bottom right
			-1.0f, 1.0f,   // 2 top left
			1.0f, 1.0f,   // 3 top right
	};
	private static final float FULL_RECTANGLE_TEX_COORDS[] = {
			0.0f, 0.0f,     // 0 bottom left
			1.0f, 0.0f,     // 1 bottom right
			0.0f, 1.0f,     // 2 top left
			1.0f, 1.0f      // 3 top right
	};

	private FloatBuffer texCoordArray;

	public FullRectangle2D()
	{
		super(FULL_RECTANGLE_COORDS, 4);
		this.texCoordArray = GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);
	}

	/**
	 * Returns the array of texture coordinates.
	 * <p>
	 * To avoid allocations, this returns internal state.  The caller must not modify it.
	 */
	public FloatBuffer getTexCoordArray()
	{
		return texCoordArray;
	}

	public int getTexCoordStride()
	{
		return getVertexStride();
	}
}
