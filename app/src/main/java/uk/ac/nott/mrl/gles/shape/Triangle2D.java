package uk.ac.nott.mrl.gles.shape;

public class Triangle2D extends Shape2D
{
	/**
	 * Simple equilateral triangle (1.0 per side).  Centered on (0,0).
	 */
	private static final float TRIANGLE_COORDS[] = {
			0.0f, 0.577350269f,   // 0 top
			-0.5f, -0.288675135f,   // 1 bottom left
			0.5f, -0.288675135f    // 2 bottom right
	};
	private static final float TRIANGLE_TEX_COORDS[] = {
			0.5f, 0.0f,     // 0 top center
			0.0f, 1.0f,     // 1 bottom left
			1.0f, 1.0f,     // 2 bottom right
	};

	public Triangle2D()
	{
		super(TRIANGLE_COORDS, TRIANGLE_TEX_COORDS, 3);
	}
}
