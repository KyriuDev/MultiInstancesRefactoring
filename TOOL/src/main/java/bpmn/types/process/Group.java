package bpmn.types.process;

public class Group extends BpmnProcessObject
{
    private final String categoryValueRef;

    public Group(final String id,
                 final String categoryValueRef)
    {
        super(BpmnProcessType.GROUP, id);
        this.categoryValueRef = categoryValueRef;
    }

    @Override
    public BpmnProcessObject copy()
    {
        final Group duplicate = new Group(BpmnProcessFactory.generateID(this), this.categoryValueRef);
        duplicate.setName(this.name());

        return duplicate;
    }

    public String categoryValueRef()
    {
        return this.categoryValueRef;
    }
}
