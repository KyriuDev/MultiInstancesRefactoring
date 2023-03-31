package bpmn.types.diagram;

public class Bounds
{
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public Bounds(String x,
                  String y,
                  String width,
                  String height)
    {
        this.x = Integer.parseInt(x);
        this.y = Integer.parseInt(y);
        this.width = Integer.parseInt(width);
        this.height = Integer.parseInt(height);
    }

    public int y()
    {
        return this.y;
    }

    public int x()
    {
        return this.x;
    }

    public int width()
    {
        return this.width;
    }

    public int height()
    {
        return this.height;
    }
}
