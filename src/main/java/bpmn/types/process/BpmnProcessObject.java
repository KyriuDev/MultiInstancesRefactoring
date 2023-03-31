package bpmn.types.process;

import constants.FlowDirection;

import java.util.*;


public abstract class BpmnProcessObject
{
    protected final BpmnProcessType type;
    protected final String id;
    protected final Map<SequenceFlow, FlowDirection> flows;
    protected final Map<String, FlowDirection> tempFlows;
    private String name;
    protected double probability;

    public BpmnProcessObject(BpmnProcessType type,
                             String id)
    {
        this.type = type;
        this.id = id;
        this.flows = new HashMap<>();
        this.tempFlows = new HashMap<>();
        this.probability = 1d;
    }

    //Used only when renaming copied nodes in graph

    public String id()
    {
        return this.id;
    }

    public BpmnProcessType type()
    {
        return this.type;
    }

    public String name()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public boolean hasName()
    {
        return this.name != null && !this.name.isEmpty();
    }

    public void setProbability(final double probability)
    {
        this.probability = probability;
    }

    public double probability()
    {
        return this.probability;
    }

    public Map<SequenceFlow, FlowDirection> flows()
    {
        return this.flows;
    }

    public void addFlow(SequenceFlow flow,
                        FlowDirection direction)
    {
        this.flows.put(flow, direction);
    }

    public void addFlow(String flow,
                        FlowDirection direction)
    {
        this.tempFlows.put(flow, direction);
    }

    public void setProperFlows(ArrayList<BpmnProcessObject> objects)
    {
        for (Iterator<String> iterator = this.tempFlows.keySet().iterator(); iterator.hasNext(); )
        {
            String flowName = iterator.next();
            FlowDirection direction = this.tempFlows.get(flowName);
            SequenceFlow flow = getFlowFromName(flowName, objects);
            iterator.remove();
            this.flows.put(flow, direction);
        }

        for (BpmnProcessObject object : objects)
        {
            if (object instanceof SequenceFlow)
            {
                SequenceFlow sequenceFlow = (SequenceFlow) object;

                if (sequenceFlow.sourceRef().equals(this.id()))
                {
                    this.flows.put(sequenceFlow, FlowDirection.OUTGOING);
                }
                else if (sequenceFlow.targetRef().equals(this.id()))
                {
                    this.flows.put(sequenceFlow, FlowDirection.INCOMING);
                }
            }
        }
    }

    public Set<SequenceFlow> incomingFlows()
    {
        Set<SequenceFlow> incomingFlows = new HashSet<>();

        for (SequenceFlow flow : this.flows.keySet())
        {
            FlowDirection direction = this.flows.get(flow);

            if (direction == FlowDirection.INCOMING)
            {
                incomingFlows.add(flow);
            }
        }

        return incomingFlows;
    }

    public Set<SequenceFlow> outgoingFlows()
    {
        Set<SequenceFlow> outgoingFlows = new HashSet<>();

        for (SequenceFlow flow : this.flows.keySet())
        {
            FlowDirection direction = this.flows.get(flow);

            if (direction == FlowDirection.OUTGOING)
            {
                outgoingFlows.add(flow);
            }
        }

        return outgoingFlows;
    }

    //Abstract methods

    public abstract BpmnProcessObject copy();

    //Private methods

    private SequenceFlow getFlowFromName(final String name,
                                         final ArrayList<BpmnProcessObject> objects)
    {
        for (final BpmnProcessObject object : objects)
        {
            if (object.type() == BpmnProcessType.SEQUENCE_FLOW)
            {
                final SequenceFlow flow = (SequenceFlow) object;

                if (flow.id().equals(name))
                {
                    return flow;
                }
            }
        }

        throw new IllegalStateException("Flow |" + name + "| not found in the entire object list");
    }

    //Overrides
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof BpmnProcessObject))
        {
            return false;
        }

        return this.id.equals(((BpmnProcessObject) o).id);
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
