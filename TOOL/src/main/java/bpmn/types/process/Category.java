package bpmn.types.process;

public class Category
{
    //TODO VOIR SI LAISSER ICI

    private final String id;
    private final String categoryValueID;
    private final String value;

    public Category(final String id,
                    final String categoryValueID,
                    final String value)
    {
        this.id = id;
        this.categoryValueID = categoryValueID;
        this.value = value;
    }

    public String categoryValueID()
    {
        return this.categoryValueID;
    }

    public String value()
    {
        return this.value;
    }

    public String id()
    {
        return this.id;
    }
}
