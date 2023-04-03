package bpmn.types.diagram;

import bpmn.types.process.BpmnProcessObject;

public class Shape extends BpmnDiagramObject
{
    private Bounds bounds;
    private final Boolean isMarkerVisible;

    public Shape(final String id,
                 final BpmnProcessObject bpmnElement,
                 final String isMarkerVisible)
    {
        super(BpmnDiagramType.SHAPE, id, bpmnElement);
        this.isMarkerVisible = isMarkerVisible.isEmpty() ? null : isMarkerVisible.equals("true");
    }

    public void setBounds(final Bounds bounds)
    {
        this.bounds = bounds;
    }

    public Bounds bounds()
    {
        return this.bounds;
    }

    public boolean hasBpmnLabelTag()
    {
        return this.id.contains("Activity");
    }

    public Boolean isMarkerVisible()
    {
        return this.isMarkerVisible;
    }

    public boolean hasMarkerVisibleTag()
    {
        return this.isMarkerVisible != null;
    }
}
