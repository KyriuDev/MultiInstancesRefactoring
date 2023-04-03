package bpmn;

import bpmn.types.process.*;
import bpmn.types.process.events.Event;

import java.util.ArrayList;

public class BpmnProcess
{
    private ArrayList<BpmnProcessObject> objects;
    private final String id;
    private final boolean isExecutable;

    public BpmnProcess(final String id,
                       final boolean isExecutable)
    {
        this.id = id;
        this.isExecutable = isExecutable;
        this.objects = new ArrayList<>();
    }

    public BpmnProcessObject getObjectFromName(final String name)
    {
        for (final BpmnProcessObject object : this.objects())
        {
            if (object.id().equals(name))
            {
                return object;
            }
        }

        throw new IllegalStateException(String.format("Object of ID |%s| not found in the objects list.", name));
    }

    public BpmnProcessObject getObjectFromName(final String name,
                                               final BpmnProcessType objectType)
    {
        for (final BpmnProcessObject object : this.objects())
        {
            if (object.type() == objectType)
            {
                if (object.id().equals(name))
                {
                    return object;
                }
            }
        }

        return null;
    }

    public void setProperObjectsReferences()
    {
        for (BpmnProcessObject object : this.objects())
        {
            if (object instanceof Task)
            {
                Task task = (Task) object;
                task.setProperFlows(this.objects());
            }
            else if (object instanceof Event)
            {
                Event startEvent = (Event) object;
                startEvent.setProperFlows(this.objects());
            }
            else if (object.type() == BpmnProcessType.EXCLUSIVE_GATEWAY
                    || object.type() == BpmnProcessType.PARALLEL_GATEWAY
                    || object.type() == BpmnProcessType.INCLUSIVE_GATEWAY
                    || object.type() == BpmnProcessType.COMPLEX_GATEWAY)
            {
                Gateway gateway = (Gateway) object;
                gateway.setProperFlows(this.objects());
                //logger.debug("Setting gateway flows.");
                gateway.setJoinOrSplitType();
            }
            else if (object.type() == BpmnProcessType.ASSOCIATION)
            {
                Association association = (Association) object;
                association.setProperSourceAndTarget(this.objects());
            }
        }
    }
    public void addObject(BpmnProcessObject object)
    {
        this.objects.add(object);
    }

    public ArrayList<BpmnProcessObject> objects()
    {
        return this.objects;
    }

    public String id()
    {
        return this.id;
    }

    public boolean isExecutable()
    {
        return this.isExecutable;
    }

    public void setObjects(ArrayList<BpmnProcessObject> objects)
    {
        this.objects = objects;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("Bpmn Process \"")
                .append(id)
                .append("\" ")
                .append(isExecutable ? "is executable" : "is not executable. It contains the following objects:\n\n");

        for (BpmnProcessObject o : this.objects)
        {
            builder.append(o.toString());
        }

        return builder.toString();
    }
}
