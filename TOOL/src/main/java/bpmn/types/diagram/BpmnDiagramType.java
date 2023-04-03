package bpmn.types.diagram;

public enum BpmnDiagramType
{
    SHAPE("bpmndi:BPMNShape"),
    EDGE("bpmndi:BPMNEdge");

    private final String label;

    BpmnDiagramType(final String s)
    {
        this.label = s;
    }

    @Override
    public String toString()
    {
        return this.label;
    }
}
