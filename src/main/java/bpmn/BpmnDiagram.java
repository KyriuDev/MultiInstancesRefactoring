package bpmn;

import bpmn.types.diagram.BpmnDiagramObject;

import java.util.ArrayList;

public class BpmnDiagram
{
    private final String diagramID;
    private final String planeID;
    private final String bpmnElement;
    private final ArrayList<BpmnDiagramObject> objects;

    public BpmnDiagram(final String diagramID,
                       final String planeID,
                       final String bpmnElement)
    {
        this.diagramID = diagramID;
        this.planeID = planeID;
        this.bpmnElement = bpmnElement;
        this.objects = new ArrayList<>();
    }

    public String diagramID()
    {
        return this.diagramID;
    }

    public String planeID()
    {
        return this.planeID;
    }

    public String bpmnElement()
    {
        return this.bpmnElement;
    }

    public ArrayList<BpmnDiagramObject> objects()
    {
        return this.objects;
    }

    public void addDiagramObject(BpmnDiagramObject diagramObject)
    {
        this.objects.add(diagramObject);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder
                .append("Bpmn Diagram \"")
                .append(diagramID)
                .append("\" has Plane \"")
                .append(planeID)
                .append("\" linked to BPMN Process \"")
                .append(bpmnElement)
                .append("\". It contains the following objects:\n\n");

        for (BpmnDiagramObject object : this.objects)
        {
            builder.append("- ")
                    .append(object.type().toString())
                    .append(" : ")
                    .append(object);
        }

        return builder.toString();
    }
}
