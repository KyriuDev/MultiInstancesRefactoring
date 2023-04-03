package bpmn.types.process.events;

import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.BpmnProcessType;

public class Event extends BpmnProcessObject
{
    public Event(BpmnProcessType type,
                 String id)
    {
        super(type, id);
    }

    @Override
    public BpmnProcessObject copy()
    {
        final Event duplicate = new Event(this.type, BpmnProcessFactory.generateID(this));
        duplicate.setName(this.name());

        return duplicate;
    }
}
