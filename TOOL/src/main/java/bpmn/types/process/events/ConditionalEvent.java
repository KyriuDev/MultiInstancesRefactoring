package bpmn.types.process.events;

import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.BpmnProcessType;

public class ConditionalEvent extends SpecialEvent
{
    private final Condition condition;

    public ConditionalEvent(BpmnProcessType type,
                            String id,
                            String subID,
                            Condition condition)
    {
        super(type, id, subID);
        this.condition = condition;
    }

    public Condition condition()
    {
        return this.condition;
    }

    @Override
    public BpmnProcessObject copy()
    {
        final ConditionalEvent duplicate = new ConditionalEvent(this.type, BpmnProcessFactory.generateID(this), this.subID() + "_2", this.condition);
        duplicate.setName(this.name());

        return duplicate;
    }
}
