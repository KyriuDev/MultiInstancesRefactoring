package bpmn.types.process;

import bpmn.types.process.events.Event;

import java.util.ArrayList;

public class BpmnProcessFactory
{
    private static ArrayList<String> objectIDs;

    private BpmnProcessFactory()
    {

    }

    public static void setObjectIDs(ArrayList<BpmnProcessObject> objects)
    {
        if (objectIDs == null)
        {
            objectIDs = new ArrayList<>();
        }

        if (!objectIDs.isEmpty())
        {
            return;
        }

        for (BpmnProcessObject object : objects)
        {
            objectIDs.add(object.id());
        }
    }

    public static void setObjectIDsString(ArrayList<String> objects)
    {
        if (objectIDs == null
            || objects == null)
        {
            objectIDs = new ArrayList<>();
        }

        if (!objectIDs.isEmpty())
        {
            return;
        }

        objectIDs.addAll(objects == null ? new ArrayList<>() : objects);
    }

    public static boolean validID(final String id)
    {
        //logger.debug("IDs already existing : {}", objectIDs);

        for (String idToCheck : objectIDs)
        {
            if (idToCheck.equalsIgnoreCase(id))
            {
                //logger.debug("ID {} already exists.", id);
                return false;
            }
        }

        //logger.debug("ID {} does not exist.", id);

        return true;
    }

    public static ArrayList<String> saveIDS()
    {
        return objectIDs;
    }

    public static void addID(String id)
    {
        if (!objectIDs.contains(id))
        {
            objectIDs.add(id);
        }
    }

    public static String generateLongID()
    {
        return generateID(8);
    }

    public static String generateVeryLongID()
    {
        return generateID(15);
    }

    public static String generateID()
    {
        return generateID(6);
    }

    public static String generateID(BpmnProcessObject object)
    {
        final String builder = generateID();
        final String id;

        if (object instanceof Task)
        {
            id = "Activity_" + builder;
        }
        else if (object instanceof Gateway)
        {
            id = "Gateway_" + builder;
        }
        else if (object instanceof Event)
        {
            if (object.type == BpmnProcessType.START_EVENT
                || object.type == BpmnProcessType.START_CONDITIONAL_EVENT
                || object.type == BpmnProcessType.START_MESSAGE_EVENT
                || object.type == BpmnProcessType.START_SIGNAL_EVENT
                || object.type == BpmnProcessType.START_TIMER_EVENT)
            {
                id = "StartEvent_" + builder;
            }
            else
            {
                id = "Event_" + builder;
            }
        }
        else if (object instanceof SequenceFlow)
        {
            id = "Flow_" + builder;
        }
        else
        {
            //logger.error("Object {} not managed", object.id());
            throw new UnsupportedOperationException(String.format("Object |%s| not managed.", object.id()));
        }

        if (validID(id))
        {
            objectIDs.add(id);
            return id;
        }
        else
        {
            return generateID(object);
        }
    }

    public static BpmnProcessObject generateExclusiveGateway()
    {
        return BpmnProcessFactory.generateGateway(BpmnProcessType.EXCLUSIVE_GATEWAY);
    }

    public static BpmnProcessObject generateParallelGateway()
    {
        return BpmnProcessFactory.generateGateway(BpmnProcessType.PARALLEL_GATEWAY);
    }

    public static BpmnProcessObject generateInclusiveGateway()
    {
        return BpmnProcessFactory.generateGateway(BpmnProcessType.INCLUSIVE_GATEWAY);
    }

    public static BpmnProcessObject generateComplexGateway()
    {
        return BpmnProcessFactory.generateGateway(BpmnProcessType.COMPLEX_GATEWAY);
    }

    public static BpmnProcessObject generateSequenceFlow(final String source,
                                                         final String destination)
    {
        return new SequenceFlow("Flow_" + generateID(), source, destination);
    }

    //Private methods

    private static BpmnProcessObject generateGateway(BpmnProcessType type)
    {
        return new Gateway(type, "Gateway_" + generateID());
    }

    private static String generateID(int nbCharacters)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < nbCharacters; i++)
        {
            int randomInt = (int) (Math.random() * 62);

            if (randomInt < 10)
            {
                //integer
                builder.append((char) (randomInt + 48));
            }
            else if (randomInt < 36)
            {
                //caps
                builder.append((char) (randomInt + 55));
            }
            else
            {
                builder.append((char) (randomInt + 61));
            }
        }

        if (validID(builder.toString()))
        {
            objectIDs.add(builder.toString());
            return builder.toString();
        }
        else
        {
            return generateID(nbCharacters);
        }
    }
}
