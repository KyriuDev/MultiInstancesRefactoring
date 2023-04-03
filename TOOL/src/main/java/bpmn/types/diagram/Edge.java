package bpmn.types.diagram;

import bpmn.types.process.BpmnProcessObject;

import java.util.ArrayList;

public class Edge extends BpmnDiagramObject
{
    private final ArrayList<Waypoint> waypoints;

    public Edge(String id,
                BpmnProcessObject bpmnElement)
    {
        super(BpmnDiagramType.EDGE, id, bpmnElement);
        this.waypoints = new ArrayList<>();
    }

    public ArrayList<Waypoint> waypoints()
    {
        return this.waypoints;
    }

    public void addWaypoint(Waypoint waypoint)
    {
        this.waypoints.add(waypoint);
    }
}
