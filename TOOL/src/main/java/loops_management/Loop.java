package loops_management;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;

public class Loop extends Graph
{
    //TODO Voir si ça a un quelconque intérêt
    private Node entryPoint;
    private Node exitPoint;

    public Loop(Node initialNode)
    {
        super(initialNode);
        this.entryPoint = null;
        this.exitPoint = null;
    }

    public void setEntryPoint(final Node entryPoint)
    {
        if (entryPoint.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY
            || ((Gateway) entryPoint.bpmnObject()).isSplitGateway())
        {
            throw new IllegalStateException("Node |" + entryPoint.bpmnObject().id() + "| is not an exclusive merge gateway.");
        }

        this.entryPoint = entryPoint;
    }

    public Node entryPoint()
    {
        return this.entryPoint;
    }

    public void setExitPoint(final Node exitPoint)
    {
        if (exitPoint.bpmnObject().type() != BpmnProcessType.EXCLUSIVE_GATEWAY
                || ((Gateway) exitPoint.bpmnObject()).isMergeGateway())
        {
            throw new IllegalStateException("Node |" + exitPoint.bpmnObject().id() + "| is not an exclusive split gateway.");
        }

        this.exitPoint = exitPoint;
    }

    public Node exitPoint()
    {
        return this.exitPoint;
    }

    public Loop copy()
    {
        final Graph copy = this.weakCopy();
        final Loop loop = new Loop(copy.initialNode());
        loop.setEntryPoint(copy.getNodeFromID(this.entryPoint.bpmnObject().id()));
        loop.setExitPoint(copy.getNodeFromID(this.exitPoint.bpmnObject().id()));
        return loop;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Loop))
        {
            return false;
        }

        return this.id.equals(((Loop) o).id);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;

        for (int i = 0; i < this.id.length(); i++)
        {
            hash = hash * 31 + this.id.charAt(i);
        }

        return hash;
    }
}
