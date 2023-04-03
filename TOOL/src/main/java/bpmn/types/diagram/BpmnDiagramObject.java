package bpmn.types.diagram;

import bpmn.types.process.BpmnProcessObject;

public abstract class BpmnDiagramObject
{
    protected final String id;
    protected final BpmnProcessObject bpmnElement;
    protected final BpmnDiagramType type;

    public BpmnDiagramObject(final BpmnDiagramType type,
                             final String id,
                             final BpmnProcessObject bpmnElement)
    {
        this.id = id;
        this.bpmnElement = bpmnElement;
        this.type = type;
    }

    public String id()
    {
        return this.id;
    }

    public BpmnProcessObject bpmnElement()
    {
        return this.bpmnElement;
    }

    public BpmnDiagramType type()
    {
        return this.type;
    }
}
