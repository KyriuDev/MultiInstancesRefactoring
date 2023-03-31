package bpmn.types.process.events;

import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.BpmnProcessType;

public class SpecialEvent extends Event
{
    private final String subID;

    public SpecialEvent(BpmnProcessType type,
                        String id,
                        String subID)
    {
        super(type, id);
        this.subID = subID;
    }

    public String subID()
    {
        return this.subID;
    }

    @Override
    public BpmnProcessObject copy()
    {
        final SpecialEvent duplicate = new SpecialEvent(this.type, BpmnProcessFactory.generateID(this), this.subID + "_2");
        duplicate.setName(this.name());

        return duplicate;
    }
}
