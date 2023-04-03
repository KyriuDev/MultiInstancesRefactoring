package bpmn.types.process;

public class TextAnnotation extends BpmnProcessObject
{
    private String text;

    public TextAnnotation(final String id)
    {
        super(BpmnProcessType.TEXT_ANNOTATION, id);
    }

    @Override
    public BpmnProcessObject copy()
    {
        final TextAnnotation duplicate = new TextAnnotation(BpmnProcessFactory.generateID(this));
        duplicate.setText(this.text);
        duplicate.setName(this.name());

        return duplicate;
    }

    public void setText(final String text)
    {
        this.text = text;
    }

    public String text()
    {
        return this.text;
    }
}
