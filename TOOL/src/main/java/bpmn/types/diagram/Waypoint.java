package bpmn.types.diagram;

public class Waypoint
{
    private final int x;
    private final int y;

    public Waypoint(final String x,
                    final String y)
    {
        this.x = Integer.parseInt(x);
        this.y = Integer.parseInt(y);
    }

    public int x()
    {
        return this.x;
    }

    public int y()
    {
        return this.y;
    }
}
