package constants;

public class BpmnType
{
    private BpmnType()
    {

    }

    public static final String DOCUMENTATION = "documentation";
    public static final String GROUP = "group";
    public static final String TEXT_ANNOTATION = "textAnnotation";
    public static final String ASSOCIATION = "association";
    public static final String CATEGORY = "category";
    public static final String SEQUENCE_FLOW = "sequenceFlow";
    public static final String TASK = "task";
    public static final String TASK_SEND = "sendTask";
    public static final String TASK_RECEIVE = "receiveTask";
    public static final String TASK_USER = "userTask";
    public static final String TASK_MANUAL = "manualTask";
    public static final String TASK_BUSINESS_RULE = "businessRuleTask";
    public static final String TASK_SERVICE = "serviceTask";
    public static final String TASK_SCRIPT = "scriptTask";
    public static final String ACTIVITY_CALL = "callActivity";
    public static final String GATEWAY_ABSTRACT = "gateway";
    public static final String EXCLUSIVE_GATEWAY = "exclusiveGateway";
    public static final String INCLUSIVE_GATEWAY = "inclusiveGateway";
    public static final String PARALLEL_GATEWAY = "parallelGateway";
    public static final String COMPLEX_GATEWAY = "complexGateway";
    public static final String START_EVENT = "startEvent";
    public static final String INTERMEDIATE_THROW_EVENT = "intermediateThrowEvent";
    public static final String INTERMEDIATE_CATCH_EVENT = "intermediateCatchEvent";
    public static final String END_EVENT = "endEvent";
    public static final String MESSAGE_EVENT = "messageEventDefinition";
    public static final String TIMER_EVENT = "timerEventDefinition";
    public static final String CONDITIONAL_EVENT = "conditionalEventDefinition";
    public static final String SIGNAL_EVENT = "signalEventDefinition";
    public static final String ESCALATION_EVENT = "escalationEventDefinition";
    public static final String LINK_EVENT = "linkEventDefinition";
    public static final String COMPENSATE_EVENT = "compensateEventDefinition";
    public static final String ERROR_EVENT = "errorEventDefinition";
    public static final String TERMINATE_EVENT = "terminatEventDefinition";
}
