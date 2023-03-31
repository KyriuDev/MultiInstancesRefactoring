package bpmn;

import bpmn.types.diagram.*;
import bpmn.types.process.*;
import bpmn.types.process.events.Condition;
import bpmn.types.process.events.ConditionalEvent;
import bpmn.types.process.events.Event;
import bpmn.types.process.events.SpecialEvent;
import constants.Boolean;
import constants.BpmnType;
import constants.Constants;
import constants.FlowDirection;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import other.Utils;
import resources.Resource;
import resources.ResourcePool;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

public class BpmnParser
{
    private boolean ENRICHED_SEQUENCE_FLOWS = true;
    private boolean ENRICHED_TASKS = true;
    private boolean TASKS_WITH_RESOURCES = true;
    private BpmnProcess bpmnProcess;
    private BpmnDiagram bpmnDiagram;
    private final BpmnCategories bpmnCategories;
    private boolean legacyFile;
    private String documentation;
    private final BpmnHeader bpmnHeader;
    private final Document document;

    public BpmnParser(File file) throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        this.document = builder.parse(file);
        this.document.getDocumentElement().normalize();
        this.bpmnHeader = new BpmnHeader();
        this.bpmnCategories = new BpmnCategories();
        this.legacyFile = false;
        this.documentation = null;
    }

    public BpmnParser(File file,
                      final boolean enrichedSequenceFlow,
                      final boolean enrichedTasks,
                      final boolean tasksWithResources) throws ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        this.document = builder.parse(file);
        this.document.getDocumentElement().normalize();
        this.bpmnHeader = new BpmnHeader();
        this.bpmnCategories = new BpmnCategories();
        this.legacyFile = false;
        this.documentation = null;
        ENRICHED_SEQUENCE_FLOWS = enrichedSequenceFlow;
        ENRICHED_TASKS = enrichedTasks;
        TASKS_WITH_RESOURCES = tasksWithResources;
    }

    public void parse()
    {
        /*
            Lines 24 to 32: Retrieve the header of the BPMN file
         */
        Element root = document.getDocumentElement();

        NamedNodeMap map = root.getAttributes();

        for (int i = 0; i < map.getLength(); i++)
        {
            Node node = map.item(i);
            this.bpmnHeader.putMetadata(node.getNodeName(), node.getNodeValue());
        }

        /*
            Lines 38 to 88: Retrieve process infos
         */

        NodeList listProcess = document.getElementsByTagName(Constants.BPMN_PROCESS);

        if (listProcess.getLength() == 0)
        {
            this.legacyFile = true;

            listProcess = document.getElementsByTagName(Constants.PROCESS);

            if (listProcess.getLength() == 0)
            {
                throw new IllegalStateException("No process tag found in the given file!");
            }
        }

        Node process = listProcess.item(0);

        if (process.getNodeType() == Node.ELEMENT_NODE)
        {
            Element processElem = (Element) process;

            String processID = processElem.getAttribute(Constants.ID);
            String isExecutable = processElem.getAttribute(Constants.IS_EXECUTABLE);

            this.bpmnProcess = new BpmnProcess(processID, isExecutable.equals(Constants.TRUE));
        }

        for (int i = 0; i < process.getChildNodes().getLength(); i++)
        {
            Node node = process.getChildNodes().item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element element = (Element) node;

                if (element.getTagName().equals(BpmnType.DOCUMENTATION))
                {
                    this.documentation = element.getTextContent();
                }
                else if (element.getTagName().contains(BpmnType.SEQUENCE_FLOW))
                {
                    parseSequenceFlow(element);
                }
                else if (element.getTagName().toLowerCase().contains(BpmnType.TASK)
                        || element.getTagName().contains(BpmnType.ACTIVITY_CALL))
                {
                    parseTaskV2(element);
                }
                else if (element.getTagName().contains(BpmnType.START_EVENT))
                {
                    parseStartEvent(element);
                }
                else if (element.getTagName().contains(BpmnType.END_EVENT))
                {
                    parseEndEvent(element);
                }
                else if (element.getTagName().contains(BpmnType.INTERMEDIATE_THROW_EVENT)
                        || element.getTagName().contains(BpmnType.INTERMEDIATE_CATCH_EVENT))
                {
                    parseIntermediateEvent(element);
                }
                else if (element.getTagName().toLowerCase().contains(BpmnType.GATEWAY_ABSTRACT))
                {
                    if (element.getTagName().contains(BpmnType.EXCLUSIVE_GATEWAY))
                    {
                        parseGateway(element, BpmnProcessType.EXCLUSIVE_GATEWAY);
                    }
                    else if (element.getTagName().contains(BpmnType.PARALLEL_GATEWAY))
                    {
                        parseGateway(element, BpmnProcessType.PARALLEL_GATEWAY);
                    }
                    else if (element.getTagName().contains(BpmnType.INCLUSIVE_GATEWAY))
                    {
                        parseGateway(element, BpmnProcessType.INCLUSIVE_GATEWAY);
                    }
                    else if (element.getTagName().contains(BpmnType.COMPLEX_GATEWAY))
                    {
                        parseGateway(element, BpmnProcessType.COMPLEX_GATEWAY);
                    }
                    else
                    {
                        System.out.printf("BPMN gateway |%s| is waiting for its implementation!%n", element.getTagName());
                    }
                }
                else if (element.getTagName().contains(BpmnType.GROUP))
                {
                    parseGroup(element);
                }
                else if (element.getTagName().contains(BpmnType.TEXT_ANNOTATION))
                {
                    parseTextAnnotation(element);
                }
                else if (element.getTagName().contains(BpmnType.ASSOCIATION))
                {
                    parseAssociation(element);
                }
                else
                {
                    System.out.printf("BPMN element |%s| is waiting for its implementation!%n", element.getTagName());
                }
            }
        }

        this.bpmnProcess.setProperObjectsReferences();

        NodeList categories = document.getElementsByTagName(Constants.BPMN_CATEGORY);

        for (int i = 0; i < categories.getLength(); i++)
        {
            Element element = (Element) categories.item(i);
            final String id = element.getAttribute(Constants.ID);

            NodeList categoryValues = element.getElementsByTagName(Constants.BPMN_CATEGORY_VALUE);

            if (categoryValues.getLength() > 0)
            {
                Element categoryValue = (Element) categoryValues.item(0);
                final String categoryValueID = categoryValue.getAttribute(Constants.ID);
                final String value = categoryValue.getAttribute(Constants.VALUE);

                Category category = new Category(id, categoryValueID, value);
                this.bpmnCategories.addCategory(category);
            }
            else
            {
                throw new IllegalStateException("Categories should have at least 1 category value!");
            }
        }

        /*
            Line 94 to ...: Retrieve diagram infos
         */

        //TODO Check Plane functionment

        NodeList listDiagram = document.getElementsByTagName(Constants.BPMN_DIAGRAM);
        Element diagram = (Element) listDiagram.item(0);

        if (diagram != null)
        {
            String diagramID = diagram.getAttribute(Constants.ID);
            NodeList listPlane = diagram.getElementsByTagName(Constants.BPMN_PLANE);
            Element plane = (Element) listPlane.item(0);
            String planeID = plane.getAttribute(Constants.ID);
            String bpmnElement = plane.getAttribute(Constants.BPMN_ELEMENT);

            this.bpmnDiagram = new BpmnDiagram(diagramID, planeID, bpmnElement);

            for (int i = 0; i < plane.getChildNodes().getLength(); i++)
            {
                Node node = plane.getChildNodes().item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    Element element = (Element) node;

                    if (element.getTagName().equals(BpmnDiagramType.SHAPE.toString()))
                    {
                        //parseShape(element);
                    }
                    else if (element.getTagName().equals(BpmnDiagramType.EDGE.toString()))
                    {
                        //parseEdge(element);
                    }
                    else
                    {
                        System.out.printf("%s is waiting for its implementation!%n", element.getTagName());
                    }
                }
            }
        }

        BpmnProcessFactory.setObjectIDs(this.bpmnProcess().objects());
    }

    //Private methods
    //Process parsing

    /**
        We assume that sequence flows do not have labels, except for indicating their
        probability of execution for loops or choices.
        Thus, we expect an empty label or a double representing the probability of execution.
        Other formats will not be recognized.
     **/
    private void parseSequenceFlow(Element element)
    {
        final String sequenceFlowID = element.getAttribute(Constants.ID);
        final String sequenceFlowSourceRef = element.getAttribute(Constants.SOURCE_REF);
        final String sequenceFlowTargetRef = element.getAttribute(Constants.TARGET_REF);
        final String name = element.getAttribute(Constants.NAME).strip();
        final double probability;
        final SequenceFlow sequenceFlow = new SequenceFlow(sequenceFlowID, sequenceFlowSourceRef, sequenceFlowTargetRef);

        if (name.isEmpty())
        {
            probability = 1d;
        }
        else
        {
            if (ENRICHED_SEQUENCE_FLOWS)
            {
                try
                {
                    probability = Double.parseDouble(name);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("Expected a probability (double), got |" + name + "|.");
                }

                sequenceFlow.setProbabilized();
            }
            else
            {
                probability = 1d;
            }
        }

        sequenceFlow.setProbability(probability);

        this.bpmnProcess.addObject(sequenceFlow);
    }

    private void parseTask(Element element)
    {
        final String taskID = element.getAttribute(Constants.ID);
        final String rawName = element.getAttribute(Constants.NAME);
        final String autoStoreVariables = element.getAttribute(Constants.AUTO_STORE_VARIABLES);
        final BpmnProcessType type = BpmnProcessType.typeFromString(element.getTagName());

        final Task task = new Task(taskID, type, -1);
        task.setName(rawName.isEmpty() ? null : rawName);
        //task.setResourcePool(resources);
        task.setAutoStoreVariables(autoStoreVariables.isEmpty() ? null : Boolean.parse(autoStoreVariables));
        parseFlows(element, task);

        this.bpmnProcess.addObject(task);
    }

    /*
        In this enhanced version of BPMN, we consider that tasks name field have the following format:
            name (duration) <nb_R_i_needed R_i, ..., nb_R_j_needed R_j>
        For example:
            A (30) <2 R1, 3 R2, 1R4>
        Which means that the task A lasts 30 units of time and uses 2 replicas of R1, 3 replicas of R2,
        and 1 replica of R4
     */
    private void parseTaskV2(Element element)
    {
        final String taskID = element.getAttribute(Constants.ID);
        final String rawName = element.getAttribute(Constants.NAME);
        final String autoStoreVariables = element.getAttribute(Constants.AUTO_STORE_VARIABLES);
        final BpmnProcessType type = BpmnProcessType.typeFromString(element.getTagName());

        final int duration;
        final String name;
        final ResourcePool resources;

        if (ENRICHED_TASKS)
        {
            if (!taskIsValid(rawName))
            {
                throw new IllegalStateException("Task name should have the format \"name (duration) <nb_R_i_needed R_i, ..., nb_R_j_needed R_j>\" containing the number of resources used and the duration of the task. Current is |" + rawName + "|.");
            }

            final int leftBracket = rawName.indexOf('(');
            final int rightBracket = rawName.indexOf(')');
            final int leftChevron = rawName.indexOf('<');
            final int rightChevron = rawName.indexOf('>');

            name = rawName.substring(0, leftBracket).strip();
            final String durationStr = rawName.substring(leftBracket + 1, rightBracket).strip();

            if (!Utils.isAnInt(durationStr))
            {
                throw new IllegalStateException("Task name should have the format \"name (duration) <nb_R_i_needed R_i, ..., nb_R_j_needed R_j>\" containing the number of resources used and the duration of the task.");
            }

            duration = Integer.parseInt(durationStr);
            //System.out.println("Current task : |" + taskID + "|.");
            resources = this.parseResources(rawName.substring(leftChevron + 1, rightChevron));
        }
        else
        {
            duration = -1;
            name = rawName;
            resources = new ResourcePool();
        }


        final Task task = new Task(taskID, type, duration);
        task.setName(name);
        task.setResourcePool(resources);
        task.setAutoStoreVariables(autoStoreVariables.isEmpty() ? null : Boolean.parse(autoStoreVariables));
        parseFlows(element, task);

        this.bpmnProcess.addObject(task);
    }

    private void parseStartEvent(Element element)
    {
        final String startEventID = element.getAttribute(Constants.ID);
        final String name = element.getAttribute(Constants.NAME);
        final Event startEvent = setProperStartEvent(element, startEventID);
        startEvent.setName(name.isEmpty() ? null : name);
        parseFlows(element, startEvent);

        this.bpmnProcess.addObject(startEvent);
    }

    private void parseEndEvent(Element element)
    {
        final String endEventID = element.getAttribute(Constants.ID);
        final String name = element.getAttribute(Constants.NAME);
        final Event endEvent = setProperEndEvent(element, endEventID);
        endEvent.setName(name.isEmpty() ? null : name);
        parseFlows(element, endEvent);

        this.bpmnProcess.addObject(endEvent);
    }

    private void parseIntermediateEvent(Element element)
    {
        final String intermediateEventID = element.getAttribute(Constants.ID);
        final String name = element.getAttribute(Constants.NAME);
        final Event intermediateEvent = setProperIntermediateEvent(element, intermediateEventID);
        intermediateEvent.setName(name.isEmpty() ? null : name);
        parseFlows(element, intermediateEvent);

        this.bpmnProcess.addObject(intermediateEvent);
    }

    private void parseGateway(Element element,
                              BpmnProcessType type)
    {
        final String id = element.getAttribute(Constants.ID);
        final String name = element.getAttribute(Constants.NAME);
        final Gateway gateway = new Gateway(type, id);
        gateway.setName(name.isEmpty() ? null : name);
        parseFlows(element, gateway);

        this.bpmnProcess.addObject(gateway);
    }

    private void parseFlows(Element element,
                            BpmnProcessObject object)
    {
        final NodeList incomingFlows = element.getElementsByTagName(Constants.BPMN_INCOMNIG);

        for (int j = 0; j < incomingFlows.getLength(); j++)
        {
            final String flowName = incomingFlows.item(j).getTextContent();
            final SequenceFlow flow = (SequenceFlow) this.bpmnProcess.getObjectFromName(flowName, BpmnProcessType.SEQUENCE_FLOW);

            if (flow == null)
            {
                object.addFlow(flowName, FlowDirection.INCOMING);
            }
            else
            {
                object.addFlow(flow, FlowDirection.INCOMING);
            }
        }

        final NodeList outgoingFlows = element.getElementsByTagName(Constants.BPMN_OUTGOING);

        for (int j = 0; j < outgoingFlows.getLength(); j++)
        {
            final String flowName = outgoingFlows.item(j).getTextContent();
            final SequenceFlow flow = (SequenceFlow) this.bpmnProcess.getObjectFromName(flowName, BpmnProcessType.SEQUENCE_FLOW);

            if (flow == null)
            {
                object.addFlow(flowName, FlowDirection.INCOMING);
            }
            else
            {
                object.addFlow(flow, FlowDirection.INCOMING);
            }
        }
    }

    private void parseGroup(Element element)
    {
        final String id = element.getAttribute(Constants.ID);
        final String categoryValueRef = element.getAttribute(Constants.CATEGORY_VALUE_REF);

        final Group group = new Group(id, categoryValueRef);

        this.bpmnProcess.addObject(group);
    }

    private void parseTextAnnotation(Element element)
    {
        final String id = element.getAttribute(Constants.ID);
        final NodeList textList = element.getElementsByTagName(Constants.BPMN_TEXT);
        final TextAnnotation textAnnotation = new TextAnnotation(id);

        if (textList.getLength() > 0)
        {
            final String text = textList.item(0).getTextContent();
            textAnnotation.setText(text);
        }

        this.bpmnProcess.addObject(textAnnotation);
    }

    private void parseAssociation(Element element)
    {
        final String id = element.getAttribute(Constants.ID);
        final String sourceRef = element.getAttribute(Constants.SOURCE_REF);
        final String targetRef = element.getAttribute(Constants.TARGET_REF);

        final Association association = new Association(id, sourceRef, targetRef);

        this.bpmnProcess.addObject(association);
    }

    //Diagram parsing

    private void parseEdge(Element element)
    {
        final String id = element.getAttribute(Constants.ID);
        final BpmnProcessObject bpmnElement = this.bpmnProcess.getObjectFromName(element.getAttribute(Constants.BPMN_ELEMENT));
        final Edge edge = new Edge(id, bpmnElement);
        final NodeList waypoints = element.getElementsByTagName(this.legacyFile ? Constants.LEGACY_WAYPOINT : Constants.CURRENT_WAYPOINT);

        for (int j = 0; j < waypoints.getLength(); j++)
        {
            final Element elem = (Element) waypoints.item(j);
            final String x = elem.getAttribute(Constants.X);
            final String y = elem.getAttribute(Constants.Y);

            final Waypoint waypoint = new Waypoint(
                    x.contains(Constants.DOT) ? x.substring(0, x.indexOf(Constants.DOT)) : x,
                    y.contains(Constants.DOT) ? y.substring(0, y.indexOf(Constants.DOT)) : y
            );
            edge.addWaypoint(waypoint);
        }

        this.bpmnDiagram.addDiagramObject(edge);
    }

    private void parseShape(Element element)
    {
        final String id = element.getAttribute(Constants.ID);
        final BpmnProcessObject bpmnElement = this.bpmnProcess.getObjectFromName(element.getAttribute(Constants.BPMN_ELEMENT));
        final String isMarkerVisible = element.getAttribute(Constants.IS_MARKER_VISIBLE);

        final Shape shape = new Shape(id, bpmnElement, isMarkerVisible);

        final NodeList boundsList = element.getElementsByTagName(this.legacyFile ? Constants.LEGACY_BOUNDS : Constants.CURRENT_BOUNDS);
        final Element boundsElem = (Element) boundsList.item(0);
        final String x = boundsElem.getAttribute(Constants.X);
        final String y = boundsElem.getAttribute(Constants.Y);
        final String width = boundsElem.getAttribute(Constants.WIDTH);
        final String height = boundsElem.getAttribute(Constants.HEIGHT);
        final Bounds bounds = new Bounds(
                x.contains(Constants.DOT) ? x.substring(0, x.indexOf(Constants.DOT)) : x,
                y.contains(Constants.DOT)  ? y.substring(0, y.indexOf(Constants.DOT)) : y,
                width.contains(Constants.DOT)  ? width.substring(0, width.indexOf(Constants.DOT)) : width,
                height.contains(Constants.DOT)  ? height.substring(0, height.indexOf(Constants.DOT)) : height
        );
        shape.setBounds(bounds);

        this.bpmnDiagram.addDiagramObject(shape);
    }

    //Other

    private Event setProperStartEvent(Element element,
                                      String id)
    {
        final NodeList messageEventList = element.getElementsByTagName(BpmnProcessType.START_MESSAGE_EVENT.toString());
        final NodeList timerEventList = element.getElementsByTagName(BpmnProcessType.START_TIMER_EVENT.toString());
        final NodeList conditionalEventList = element.getElementsByTagName(BpmnProcessType.START_CONDITIONAL_EVENT.toString());
        final NodeList signalEventList = element.getElementsByTagName(BpmnProcessType.START_SIGNAL_EVENT.toString());
        final Event startEvent;

        if (messageEventList.getLength() != 0)
        {
            Element messageElement = (Element) messageEventList.item(0);
            String messageID = messageElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.START_MESSAGE_EVENT, id, messageID);
        }
        else if (timerEventList.getLength() != 0)
        {
            Element timerElement = (Element) timerEventList.item(0);
            String timerID = timerElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.START_TIMER_EVENT, id, timerID);
        }
        else if (conditionalEventList.getLength() != 0)
        {
            Element conditionalElement = (Element) conditionalEventList.item(0);
            String conditionalID = conditionalElement.getAttribute(Constants.ID);

            NodeList conditionList = conditionalElement.getElementsByTagName(Constants.BPMN_CONDITION);
            Element conditionElement = (Element) conditionList.item(0);
            String conditionType = conditionElement.getAttribute(Constants.XSI_TYPE);
            Condition condition = new Condition(conditionType);

            startEvent = new ConditionalEvent(BpmnProcessType.START_CONDITIONAL_EVENT, id, conditionalID, condition);
        }
        else if (signalEventList.getLength() != 0)
        {
            Element signalElement = (Element) signalEventList.item(0);
            String signalID = signalElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.START_SIGNAL_EVENT, id, signalID);
        }
        else
        {
            startEvent = new Event(BpmnProcessType.START_EVENT, id);
        }

        return startEvent;
    }

    private Event setProperEndEvent(Element element,
                                    String id)
    {
        final NodeList messageEventList = element.getElementsByTagName(BpmnProcessType.END_MESSAGE_EVENT.toString());
        final NodeList escalationEventList = element.getElementsByTagName(BpmnProcessType.END_ESCALATION_EVENT.toString());
        final NodeList errorEventList = element.getElementsByTagName(BpmnProcessType.END_ERROR_EVENT.toString());
        final NodeList compensateEventList = element.getElementsByTagName(BpmnProcessType.END_COMPENSATE_EVENT.toString());
        final NodeList signalEventList = element.getElementsByTagName(BpmnProcessType.END_SIGNAL_EVENT.toString());
        final NodeList terminateEventList = element.getElementsByTagName(BpmnProcessType.END_TERMINATE_EVENT.toString());
        final Event startEvent;

        if (messageEventList.getLength() != 0)
        {
            Element messageElement = (Element) messageEventList.item(0);
            String messageID = messageElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.END_MESSAGE_EVENT, id, messageID);
        }
        else if (escalationEventList.getLength() != 0)
        {
            Element timerElement = (Element) escalationEventList.item(0);
            String timerID = timerElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.END_ESCALATION_EVENT, id, timerID);
        }
        else if (errorEventList.getLength() != 0)
        {
            Element errorElement = (Element) errorEventList.item(0);
            String errorID = errorElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.END_ERROR_EVENT, id, errorID);
        }
        else if (compensateEventList.getLength() != 0)
        {
            Element compensateElement = (Element) compensateEventList.item(0);
            String compensateID = compensateElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.END_COMPENSATE_EVENT, id, compensateID);
        }
        else if (signalEventList.getLength() != 0)
        {
            Element signalElement = (Element) signalEventList.item(0);
            String signalID = signalElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.END_SIGNAL_EVENT, id, signalID);
        }
        else if (terminateEventList.getLength() != 0)
        {
            Element terminateElement = (Element) terminateEventList.item(0);
            String terminateID = terminateElement.getAttribute(Constants.ID);
            startEvent = new SpecialEvent(BpmnProcessType.END_TERMINATE_EVENT, id, terminateID);
        }
        else
        {
            startEvent = new Event(BpmnProcessType.END_EVENT, id);
        }

        return startEvent;
    }

    private Event setProperIntermediateEvent(Element element,
                                             String id)
    {
        if (element.getTagName().equals(BpmnProcessType.INTERMEDIATE_THROW_EVENT.toString()))
        {
            return setProperIntermediateThrowEvent(element, id);
        }
        else
        {
            return setProperIntermediateCatchEvent(element, id);
        }
    }

    private Event setProperIntermediateThrowEvent(Element element,
                                                  String id)
    {
        final NodeList messageEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_THROW_MESSAGE_EVENT.toString());
        final NodeList escalationEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_THROW_ESCALATION_EVENT.toString());
        final NodeList linkEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_THROW_LINK_EVENT.toString());
        final NodeList compensateEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_THROW_COMPENSATE_EVENT.toString());
        final NodeList signalEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_THROW_SIGNAL_EVENT.toString());
        final Event intermediateThrowEvent;

        if (messageEventList.getLength() != 0)
        {
            Element messageElement = (Element) messageEventList.item(0);
            String messageID = messageElement.getAttribute(Constants.ID);
            intermediateThrowEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_THROW_MESSAGE_EVENT, id, messageID);
        }
        else if (escalationEventList.getLength() != 0)
        {
            Element escalationElement = (Element) escalationEventList.item(0);
            String escalationID = escalationElement.getAttribute(Constants.ID);
            intermediateThrowEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_THROW_ESCALATION_EVENT, id, escalationID);
        }
        else if (linkEventList.getLength() != 0)
        {
            Element linkElement = (Element) linkEventList.item(0);
            String linkID = linkElement.getAttribute(Constants.ID);
            intermediateThrowEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_THROW_LINK_EVENT, id, linkID);
        }
        else if (compensateEventList.getLength() != 0)
        {
            Element compensateElement = (Element) compensateEventList.item(0);
            String compensateID = compensateElement.getAttribute(Constants.ID);
            intermediateThrowEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_THROW_COMPENSATE_EVENT, id, compensateID);
        }
        else if (signalEventList.getLength() != 0)
        {
            Element signalElement = (Element) signalEventList.item(0);
            String signalID = signalElement.getAttribute(Constants.ID);
            intermediateThrowEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_THROW_SIGNAL_EVENT, id, signalID);
        }
        else
        {
            intermediateThrowEvent = new Event(BpmnProcessType.INTERMEDIATE_THROW_EVENT, id);
        }

        return intermediateThrowEvent;
    }

    private Event setProperIntermediateCatchEvent(Element element,
                                                  String id)
    {
        final NodeList messageEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_CATCH_MESSAGE_EVENT.toString());
        final NodeList timerEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_CATCH_TIMER_EVENT.toString());
        final NodeList conditionalEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_CATCH_CONDITIONAL_EVENT.toString());
        final NodeList linkEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_CATCH_LINK_EVENT.toString());
        final NodeList signalEventList = element.getElementsByTagName(BpmnProcessType.INTERMEDIATE_CATCH_SIGNAL_EVENT.toString());
        final Event intermediateCatchEvent;

        if (messageEventList.getLength() != 0)
        {
            Element messageElement = (Element) messageEventList.item(0);
            String messageID = messageElement.getAttribute(Constants.ID);
            intermediateCatchEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_CATCH_MESSAGE_EVENT, id, messageID);
        }
        else if (timerEventList.getLength() != 0)
        {
            Element timerElement = (Element) timerEventList.item(0);
            String timerID = timerElement.getAttribute(Constants.ID);
            intermediateCatchEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_CATCH_TIMER_EVENT, id, timerID);
        }
        else if (conditionalEventList.getLength() != 0)
        {
            Element conditionalElement = (Element) conditionalEventList.item(0);
            String conditionalID = conditionalElement.getAttribute(Constants.ID);

            NodeList conditionList = conditionalElement.getElementsByTagName(Constants.BPMN_CONDITION);
            Element conditionElement = (Element) conditionList.item(0);
            String conditionType = conditionElement.getAttribute(Constants.XSI_TYPE);
            Condition condition = new Condition(conditionType);

            intermediateCatchEvent = new ConditionalEvent(BpmnProcessType.INTERMEDIATE_CATCH_CONDITIONAL_EVENT, id, conditionalID, condition);
        }
        else if (linkEventList.getLength() != 0)
        {
            Element linkElement = (Element) linkEventList.item(0);
            String linkID = linkElement.getAttribute(Constants.ID);
            intermediateCatchEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_CATCH_LINK_EVENT, id, linkID);
        }
        else if (signalEventList.getLength() != 0)
        {
            Element signalElement = (Element) signalEventList.item(0);
            String signalID = signalElement.getAttribute(Constants.ID);
            intermediateCatchEvent = new SpecialEvent(BpmnProcessType.INTERMEDIATE_CATCH_SIGNAL_EVENT, id, signalID);
        }
        else
        {
            throw new IllegalStateException();
        }

        return intermediateCatchEvent;
    }

    private boolean taskIsValid(final String name)
    {
        final int leftBracket = name.indexOf('(');
        final int rightBracket = name.indexOf(')');
        final int leftChevron = name.indexOf('<');
        final int rightChevron = name.indexOf('>');

        return leftBracket != -1
                && rightBracket != -1
                && leftChevron != -1
                && rightChevron != -1
                && leftBracket <= rightBracket
                && leftChevron <= rightChevron;
    }

    private ResourcePool parseResources(final String resources)
    {
        String myResources = resources;
        final ResourcePool resourcePool = new ResourcePool();
        int coma = resources.indexOf(',');

        if (!TASKS_WITH_RESOURCES)
        {
            return resourcePool;
        }

        //System.out.println("Full resources: " + resources);

        while (coma != -1)
        {
            //Should be of the form "nb_R_used R"
            final String resourceAndValue = myResources.substring(0, coma).strip();
            //System.out.println("Current resource and value: " + resourceAndValue);
            final StringBuilder valueBuilder = new StringBuilder();
            final StringBuilder resourceBuilder = new StringBuilder();
            boolean nonDigitEncountered = false;

            for (char c : resourceAndValue.toCharArray())
            {
                if (nonDigitEncountered)
                {
                    resourceBuilder.append(c);
                }
                else
                {
                    if (Character.isDigit(c))
                    {
                        valueBuilder.append(c);
                    }
                    else
                    {
                        nonDigitEncountered = true;
                        resourceBuilder.append(c);
                    }
                }
            }

            final String valueStr = valueBuilder.toString().strip();
            final String resourceStr = resourceBuilder.toString().strip();

            if (valueStr.isEmpty()
                || resourceStr.isEmpty()
                || !Utils.isAnInt(valueStr)
                || Utils.isAnInt(resourceStr))
            {
                throw new IllegalStateException("Resource usage is badly written.");
            }

            final int value = Integer.parseInt(valueStr);
            final Resource resource = new Resource(resourceStr);

            try
            {
                resourcePool.addResource(resource, value);
            }
            catch (IllegalStateException e)
            {
                //System.out.println(resourcePool);
                throw new IllegalStateException();
            }

            myResources = myResources.substring(coma + 1);
            coma = myResources.indexOf(',');
        }

        //Should be of the form "nb_R_used R"
        final StringBuilder valueBuilder = new StringBuilder();
        final StringBuilder resourceBuilder = new StringBuilder();
        boolean nonDigitEncountered = false;

        for (char c : myResources.strip().toCharArray())
        {
            if (nonDigitEncountered)
            {
                resourceBuilder.append(c);
            }
            else
            {
                if (Character.isDigit(c))
                {
                    valueBuilder.append(c);
                }
                else
                {
                    nonDigitEncountered = true;
                    resourceBuilder.append(c);
                }
            }
        }

        final String valueStr = valueBuilder.toString().strip();
        final String resourceStr = resourceBuilder.toString().strip();

        if (valueStr.isEmpty()
            || resourceStr.isEmpty()
            || !Utils.isAnInt(valueStr)
            || Utils.isAnInt(resourceStr))
        {
            throw new IllegalStateException("Resource usage is badly written.");
        }

        final int value = Integer.parseInt(valueStr);
        final Resource resource = new Resource(resourceStr);

        resourcePool.addResource(resource, value);

        //System.out.println(resourcePool);

        return resourcePool;
    }

    public BpmnProcess bpmnProcess()
    {
        return this.bpmnProcess;
    }

    public BpmnHeader bpmnHeader()
    {
        return this.bpmnHeader;
    }

    public BpmnDiagram bpmnDiagram()
    {
        return this.bpmnDiagram;
    }

    public BpmnCategories bpmnCategories()
    {
        return this.bpmnCategories;
    }

    public String documentation()
    {
        return this.documentation;
    }
}
