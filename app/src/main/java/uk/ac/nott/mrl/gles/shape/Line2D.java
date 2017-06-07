package uk.ac.nott.mrl.gles.shape;

public class Line2D extends Shape2D
{
	private final float LINE_COORDS[] = {
			-0.5f, -0.002f,   // 0 bottom left
			0.5f, -0.002f,   // 1 bottom right
	};
	private static final float LINE_TEX_COORDS[] = {
			0.0f, 0.0f,     // 0 bottom left
			1.0f, 1.0f,     // 1 bottom right
	};

	public Line2D()
	{
		super(2);
		setVertexArray(LINE_COORDS);
		setTextureArray(LINE_TEX_COORDS);
	}
}
