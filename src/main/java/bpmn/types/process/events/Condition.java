package bpmn.types.process.events;

public class Condition
{
    private final String type;

    public Condition(String type)
    {
        this.type = type;
    }

    public String type()
    {
        return this.type;
    }
}
