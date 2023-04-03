package bpmn.graph;

import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.SequenceFlow;

import java.util.*;

public class ListToGraph
{
    private final Set<BpmnProcessObject> objects;
    private final Set<SequenceFlow> sequenceFlows;

    public ListToGraph(final ArrayList<BpmnProcessObject> objects)
    {
        this.objects = new HashSet<>(objects);
        this.sequenceFlows = new HashSet<>();
    }

    public Graph convert()
    {
        final HashSet<Node> encounteredObjects = new HashSet<>();
        final BpmnProcessObject startEvent = this.getStartEvent();
        final Graph graph = new Graph(new Node(startEvent));
        this.loadSequenceFlows();
        this.convertRec(startEvent, encounteredObjects, graph.initialNode());

        //TODO Rajouter les probas des choix et boucles si pas indiquées de base

        return graph;
    }

    public void convertRec(final BpmnProcessObject currentObject,
                           final HashSet<Node> encounteredNodes,
                           final Node currentNode)
    {
        if (encounteredNodes.contains(currentNode))
        {
            return;
        }

        encounteredNodes.add(currentNode);

        for (SequenceFlow outFlow : currentObject.outgoingFlows())
        {
            Node outFlowNode = new Node(outFlow);
            Node outNode = this.getExistingOutNode(outFlow, encounteredNodes);
            final Node finalOutNode;

            if (outNode == null)
            {
                BpmnProcessObject bpmnObject = this.getObjectFromID(outFlow.targetRef());
                finalOutNode = new Node(bpmnObject);
            }
            else
            {
                finalOutNode = outNode;
            }

            //Add the outgoing flow as child
            currentNode.addChild(outFlowNode);
            outFlowNode.addParent(currentNode);

            //Add the next node as child of the outgoing flow
            outFlowNode.addChild(finalOutNode);
            finalOutNode.addParent(outFlowNode);

            this.convertRec(finalOutNode.bpmnObject(), encounteredNodes, finalOutNode);
        }
    }

    //Private methods

    private Node getExistingOutNode(SequenceFlow sequenceFlow,
                                    Set<Node> nodes)
    {
        for (Node node : nodes)
        {
            if (node.bpmnObject().id().equals(sequenceFlow.targetRef()))
            {
                return node;
            }
        }

        return null;
    }

    private BpmnProcessObject getStartEvent()
    {
        BpmnProcessObject startEvent = null;

        for (BpmnProcessObject object : this.objects)
        {
            if (object.type() == BpmnProcessType.START_EVENT
                    || object.type() == BpmnProcessType.START_CONDITIONAL_EVENT
                    || object.type() == BpmnProcessType.START_MESSAGE_EVENT
                    || object.type() == BpmnProcessType.START_SIGNAL_EVENT
                    || object.type() == BpmnProcessType.START_TIMER_EVENT)
            {
                if (startEvent == null)
                {
                    startEvent = object;
                }
                else
                {
                    throw new IllegalStateException("A BPMN file should have exactly 1 start event.");
                }
            }
        }

        if (startEvent == null)
        {
            //return this.objects.get(0);
            throw new IllegalStateException("No start event found in the objects list.");
        }

        return startEvent;
    }

    private void loadSequenceFlows()
    {
        for (BpmnProcessObject object : this.objects)
        {
            if (object.type() == BpmnProcessType.SEQUENCE_FLOW)
            {
                this.sequenceFlows.add((SequenceFlow) object);
            }
        }
    }

    private BpmnProcessObject getObjectFromID(final String objectID)
    {
        for (BpmnProcessObject object : this.objects)
        {
            if (object.id().equals(objectID))
            {
                return object;
            }
        }

        throw new IllegalStateException("BPMN Object |" + objectID + "| was not found in the objects list.");
    }
}
