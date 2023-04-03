package bpmn.types.process;

import constants.BpmnType;
import constants.Constants;

public enum BpmnProcessType
{
    SEQUENCE_FLOW(Constants.BPMN_PREFIX + BpmnType.SEQUENCE_FLOW),
    GROUP(Constants.BPMN_PREFIX + BpmnType.GROUP),
    TEXT_ANNOTATION(Constants.BPMN_PREFIX + BpmnType.TEXT_ANNOTATION),
    ASSOCIATION(Constants.BPMN_PREFIX + BpmnType.ASSOCIATION),

    TASK(Constants.BPMN_PREFIX + BpmnType.TASK),
    TASK_SEND(Constants.BPMN_PREFIX + BpmnType.TASK_SEND),
    TASK_RECEIVE(Constants.BPMN_PREFIX + BpmnType.TASK_RECEIVE),
    TASK_USER(Constants.BPMN_PREFIX + BpmnType.TASK_USER),
    TASK_MANUAL(Constants.BPMN_PREFIX + BpmnType.TASK_MANUAL),
    TASK_BUSINESS_RULE(Constants.BPMN_PREFIX + BpmnType.TASK_BUSINESS_RULE),
    TASK_SERVICE(Constants.BPMN_PREFIX + BpmnType.TASK_SERVICE),
    TASK_SCRIPT(Constants.BPMN_PREFIX + BpmnType.TASK_SCRIPT),
    ACTIVITY_CALL(Constants.BPMN_PREFIX + BpmnType.ACTIVITY_CALL),

    EXCLUSIVE_GATEWAY(Constants.BPMN_PREFIX + BpmnType.EXCLUSIVE_GATEWAY),
    PARALLEL_GATEWAY(Constants.BPMN_PREFIX + BpmnType.PARALLEL_GATEWAY),
    INCLUSIVE_GATEWAY(Constants.BPMN_PREFIX + BpmnType.INCLUSIVE_GATEWAY),
    COMPLEX_GATEWAY(Constants.BPMN_PREFIX + BpmnType.COMPLEX_GATEWAY),

    START_EVENT(Constants.BPMN_PREFIX + BpmnType.START_EVENT),
    START_MESSAGE_EVENT(Constants.BPMN_PREFIX + BpmnType.MESSAGE_EVENT),
    START_TIMER_EVENT(Constants.BPMN_PREFIX + BpmnType.TIMER_EVENT),
    START_CONDITIONAL_EVENT(Constants.BPMN_PREFIX + BpmnType.CONDITIONAL_EVENT),
    START_SIGNAL_EVENT(Constants.BPMN_PREFIX + BpmnType.SIGNAL_EVENT),

    INTERMEDIATE_THROW_EVENT(Constants.BPMN_PREFIX + BpmnType.INTERMEDIATE_THROW_EVENT),
    INTERMEDIATE_CATCH_EVENT(Constants.BPMN_PREFIX + BpmnType.INTERMEDIATE_CATCH_EVENT),
    INTERMEDIATE_THROW_MESSAGE_EVENT(Constants.BPMN_PREFIX + BpmnType.MESSAGE_EVENT),
    INTERMEDIATE_CATCH_MESSAGE_EVENT(Constants.BPMN_PREFIX + BpmnType.MESSAGE_EVENT),
    INTERMEDIATE_CATCH_TIMER_EVENT(Constants.BPMN_PREFIX + BpmnType.TIMER_EVENT),
    INTERMEDIATE_THROW_ESCALATION_EVENT(Constants.BPMN_PREFIX + BpmnType.ESCALATION_EVENT),
    INTERMEDIATE_CATCH_CONDITIONAL_EVENT(Constants.BPMN_PREFIX + BpmnType.CONDITIONAL_EVENT),
    INTERMEDIATE_CATCH_LINK_EVENT(Constants.BPMN_PREFIX + BpmnType.LINK_EVENT),
    INTERMEDIATE_THROW_LINK_EVENT(Constants.BPMN_PREFIX + BpmnType.LINK_EVENT),
    INTERMEDIATE_THROW_COMPENSATE_EVENT(Constants.BPMN_PREFIX + BpmnType.COMPENSATE_EVENT),
    INTERMEDIATE_CATCH_SIGNAL_EVENT(Constants.BPMN_PREFIX + BpmnType.SIGNAL_EVENT),
    INTERMEDIATE_THROW_SIGNAL_EVENT(Constants.BPMN_PREFIX + BpmnType.SIGNAL_EVENT),

    END_EVENT(Constants.BPMN_PREFIX + BpmnType.END_EVENT),
    END_MESSAGE_EVENT(Constants.BPMN_PREFIX + BpmnType.MESSAGE_EVENT),
    END_ESCALATION_EVENT(Constants.BPMN_PREFIX + BpmnType.ESCALATION_EVENT),
    END_ERROR_EVENT(Constants.BPMN_PREFIX + BpmnType.ERROR_EVENT),
    END_COMPENSATE_EVENT(Constants.BPMN_PREFIX + BpmnType.COMPENSATE_EVENT),
    END_SIGNAL_EVENT(Constants.BPMN_PREFIX + BpmnType.SIGNAL_EVENT),
    END_TERMINATE_EVENT(Constants.BPMN_PREFIX + BpmnType.TERMINATE_EVENT);

    private final String label;

    BpmnProcessType(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return this.label;
    }

    public static BpmnProcessType typeFromString(String type)
    {
        if (type.contains(BpmnType.TASK_SCRIPT))
        {
            return BpmnProcessType.TASK_SCRIPT;
        }
        else if (type.contains(BpmnType.TASK_SERVICE))
        {
            return BpmnProcessType.TASK_SERVICE;
        }
        else if (type.contains(BpmnType.TASK_BUSINESS_RULE))
        {
            return BpmnProcessType.TASK_BUSINESS_RULE;
        }
        else if (type.contains(BpmnType.TASK_MANUAL))
        {
            return BpmnProcessType.TASK_MANUAL;
        }
        else if (type.contains(BpmnType.TASK_USER))
        {
            return BpmnProcessType.TASK_USER;
        }
        else if (type.contains(BpmnType.TASK_RECEIVE))
        {
            return BpmnProcessType.TASK_RECEIVE;
        }
        else if (type.contains(BpmnType.TASK_SEND))
        {
            return BpmnProcessType.TASK_SEND;
        }
        else if (type.contains(BpmnType.TASK))
        {
            return BpmnProcessType.TASK;
        }
        else if (type.contains(BpmnType.ACTIVITY_CALL))
        {
            return BpmnProcessType.ACTIVITY_CALL;
        }
        else
        {
            throw new UnsupportedOperationException("Type |" + type + "| is not supported.");
        }
    }

    public String generalType()
    {
        switch (this)
        {
            case START_MESSAGE_EVENT:
            case START_TIMER_EVENT:
            case START_CONDITIONAL_EVENT:
            case START_SIGNAL_EVENT:
            {
                return "bpmn:startEvent";
            }

            case END_MESSAGE_EVENT:
            case END_ESCALATION_EVENT:
            case END_ERROR_EVENT:
            case END_COMPENSATE_EVENT:
            case END_SIGNAL_EVENT:
            case END_TERMINATE_EVENT:
            {
                return "bpmn:endEvent";
            }

            case INTERMEDIATE_CATCH_CONDITIONAL_EVENT:
            case INTERMEDIATE_CATCH_LINK_EVENT:
            case INTERMEDIATE_CATCH_MESSAGE_EVENT:
            case INTERMEDIATE_CATCH_SIGNAL_EVENT:
            case INTERMEDIATE_CATCH_TIMER_EVENT:
            {
                return "bpmn:intermediateCatchEvent";
            }

            case INTERMEDIATE_THROW_COMPENSATE_EVENT:
            case INTERMEDIATE_THROW_ESCALATION_EVENT:
            case INTERMEDIATE_THROW_LINK_EVENT:
            case INTERMEDIATE_THROW_MESSAGE_EVENT:
            case INTERMEDIATE_THROW_SIGNAL_EVENT:
            {
                return "bpmn:intermediateThrowEvent";
            }

            default:
            {
                throw new IllegalStateException("Process type |" + this + "| has a known type name.");
            }
        }
    }
}
