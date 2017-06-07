package uk.ac.nott.mrl.gles.shape;

public class Rectangle2D extends Shape2D
{
	/**
	 * Simple square, specified as a triangle strip.  The square is centered on (0,0) and has
	 * a size of 1x1.
	 * <p>
	 * Triangles are 0-1-2 and 2-1-3 (counter-clockwise winding).
	 */
	private static final float RECTANGLE_COORDS[] = {
			-0.5f, -0.5f,   // 0 bottom left
			0.5f, -0.5f,   // 1 bottom right
			-0.5f, 0.5f,   // 2 top left
			0.5f, 0.5f,   // 3 top right
	};
	private static final float RECTANGLE_TEX_COORDS[] = {
			0.0f, 1.0f,     // 0 bottom left
			1.0f, 1.0f,     // 1 bottom right
			0.0f, 0.0f,     // 2 top left
			1.0f, 0.0f      // 3 top right
	};

	public Rectangle2D()
	{

		super(RECTANGLE_COORDS, RECTANGLE_TEX_COORDS, 4);
	}
}
