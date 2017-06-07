package uk.ac.nott.mrl.gles.shape;

public class Line2D extends Shape2D
{
	private final float LINE_COORDS[] = {
			0f, 0f,   // 0 bottom left
			2f, 1f,   // 1 bottom right
	};

	public Line2D()
	{
		super(2);
		setVertexArray(LINE_COORDS);
	}
}
