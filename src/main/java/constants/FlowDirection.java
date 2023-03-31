package constants;

public enum FlowDirection
{
    INCOMING("incoming"),
    OUTGOING("outgoing");

    private final String label;

    FlowDirection(final String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return this.label;
    }
}
