package bpmn.writing.generation.parser; /**
 * 
 */

import bpmn.writing.generation.model.Node;
import bpmn.writing.generation.model.Process;
import bpmn.writing.generation.model.Sequence;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.util.*;

/**
 * @author ajayk
 *
 */
public class BaseContentHandler implements ContentHandler {

  static final Logger logger = LoggerFactory.getLogger(BaseContentHandler.class);

  private File input;
  private Process output;

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SEQUENCE_FLOW = "sequenceFlow";
  private static final String ID = "id";
  private static final String OUTGOING = "outgoing";
  private static final String INCOMING = "incoming";
  private static final String PROCESS = "process";
  private static final String EXCLUSIVE_GATEWAY = "exclusiveGateway";
  private static final String INCLUSIVE_GATEWAY = "inclusiveGateway";
  private static final String EVENT_BASED_GATEWAY = "eventBasedGateway";
  private static final String PARALLEL_GATEWAY = "parallelGateway";
  // private static final String USER_TASK = "userTask";
  private static final String TASK = "Task";

  private static final Set<String> NODE_VALUES = new HashSet<>(Arrays.asList(START_EVENT, END_EVENT,
      TASK, EXCLUSIVE_GATEWAY, INCLUSIVE_GATEWAY, PARALLEL_GATEWAY, EVENT_BASED_GATEWAY));

  public BaseContentHandler(File input) {
    this.input = input;
  }

  /*
   * (non-Javadoc)
   * 
   * @see fr.inria.convecs.optimus.parser.ContentHandler#handle()
   */
  @Override
  public void handle() {

    try {

      XMLInputFactory2 inputFactory = (XMLInputFactory2) XMLInputFactory2.newInstance();
      XMLStreamReader2 streamReader = inputFactory.createXMLStreamReader(input);

      Process process = null;
      String processId = null;
      List<Node> nodeList = new ArrayList<Node>();
      List<Sequence> sequenceList = new ArrayList<Sequence>();
      while (streamReader.hasNext()) {
        int eventType = streamReader.next(); // eventReader.next();
        if (streamReader.getEventType() == XMLStreamReader.END_ELEMENT) {
          String elementName = streamReader.getLocalName();
          if (elementName.equals(PROCESS)) {
            // update flows if they are not listed as incoming/outgoing flows in the input
            List<Node> updatedNodeList = verfiyAndUpdateNodes(nodeList, sequenceList);
            // quick fix: dot is not handled at backend
            processId = processId.replace(".", "_");
            process = new Process(processId, updatedNodeList, sequenceList);
            break;
          }

        } else if (streamReader.getEventType() == XMLStreamReader.START_ELEMENT) {
          String elementName = streamReader.getLocalName();
          if (elementName.equals(PROCESS)) {
            processId = streamReader.getAttributeValue(null, ID);
            logger.debug("Parsing Process id: {} ", processId);
          } else if (NODE_VALUES.contains(elementName)) {
            Node node = handleNode(streamReader, elementName);
            logger.debug("Node info: {}", node.toString());
            nodeList.add(node);
          } else if (elementName.endsWith(TASK) || elementName.equals("task")) {
            Node task = handleNode(streamReader, elementName);
            logger.debug("Task info: {}", task.toString());
            nodeList.add(task);
          } else if (elementName.equals(SEQUENCE_FLOW)) {
            String id = streamReader.getAttributeValue(null, ID);
            String source = streamReader.getAttributeValue(null, "sourceRef");
            String target = streamReader.getAttributeValue(null, "targetRef");
            logger.debug("Parsing Sequence id: {} ", id);
            Sequence sequence = new Sequence(id, source, target);
            logger.debug("Sequence info: {}", sequence.toString());
            sequenceList.add(sequence);
          }
        }
      }
      this.output = process;

    } catch (XMLStreamException xmle) {
      logger.error("Error parsing the XML", xmle);
      throw new RuntimeException(xmle);
    }
  }

  private Node handleNode(final XMLStreamReader2 streamReader, String eventName)
      throws XMLStreamException {
    Node node = new Node();
    List<String> incomingFlows = new ArrayList<String>();
    List<String> outgoingFlows = new ArrayList<String>();

    while (streamReader.hasNext()) {
      if (streamReader.getEventType() == XMLStreamReader.END_ELEMENT) {
        String elementName = streamReader.getLocalName();
        if (elementName.equals(eventName)) {
          node.setIncomingFlows(incomingFlows);
          node.setOutgoingFlows(outgoingFlows);
          break;
        }
      } else if (streamReader.getEventType() == XMLStreamReader.START_ELEMENT) {
        String elementName = streamReader.getLocalName();
        String id;
        if (elementName.equals(eventName)) {
          id = streamReader.getAttributeValue(null, ID);
          logger.debug("Parsing {} id: {} ", elementName, id);
          node.setId(id);
        } else if (elementName.equals(INCOMING)) {
          String flowId = readCharacters(streamReader);
          incomingFlows.add(flowId);
        } else if (elementName.equals(OUTGOING)) {
          String flowId = readCharacters(streamReader);
          outgoingFlows.add(flowId);
        }
      }

      int eventType = streamReader.next();

    }

    if (eventName.equals(START_EVENT)) {
      node.setType(Node.NodeType.INITIAL_EVENT);
      node.setIncomingFlows(null);
    } else if (eventName.equals(END_EVENT)) {
      node.setType(Node.NodeType.END_EVENT);
      node.setOutgoingFlows(null);
    } else if (eventName.equals(EXCLUSIVE_GATEWAY)) {
      if (incomingFlows.size() < outgoingFlows.size()) {
        node.setType(Node.NodeType.XOR_SPLIT_GATEWAY);
      } else {
        node.setType(Node.NodeType.XOR_JOIN_GATEWAY);
      }
    } else if (eventName.equals(EVENT_BASED_GATEWAY)) {
      if (incomingFlows.size() < outgoingFlows.size()) {
        node.setType(Node.NodeType.XOR_SPLIT_GATEWAY);
      } else {
        node.setType(Node.NodeType.XOR_JOIN_GATEWAY);
      }
    } else if (eventName.equals(INCLUSIVE_GATEWAY)) {
      if (incomingFlows.size() < outgoingFlows.size()) {
        node.setType(Node.NodeType.OR_SPLIT_GATEWAY);
      } else {
        node.setType(Node.NodeType.OR_JOIN_GATEWAY);
      }
    } else if (eventName.equals(PARALLEL_GATEWAY)) {
      if (incomingFlows.size() < outgoingFlows.size()) {
        node.setType(Node.NodeType.AND_SPLIT_GATEWAY);
      } else {
        node.setType(Node.NodeType.AND_JOIN_GATEWAY);
      }
    } else if (eventName.endsWith(TASK) || eventName.equals("task")) {
      node.setType(Node.NodeType.TASK);
    } else {
      logger.error("Unhandled event! : {}", eventName);
    }

    return node;
  }

  private String readCharacters(final XMLStreamReader2 streamReader) throws XMLStreamException {
    StringBuilder result = new StringBuilder();
    while (streamReader.hasNext()) {
      int eventType = streamReader.next();
      if (eventType == XMLStreamReader.CHARACTERS) {
        result.append(streamReader.getText());
      } else if (eventType == XMLStreamReader.END_ELEMENT) {
        return result.toString();
      }
    }
    logger.error("Error parsing the file @ {}", streamReader.getLocation().toString());
    throw new XMLStreamException("Error parsing the file");
  }

  /*
   * (non-Javadoc)
   * 
   * @see fr.inria.convecs.optimus.parser.ContentHandler#getOutput()
   */
  @Override
  public Object getOutput() {
    return this.output;
  }

  private List<Node> verfiyAndUpdateNodes(final List<Node> nodeList,
      final List<Sequence> sequenceList) {
    /*
     * List<Node> gateways = nodeList.stream() .filter(node ->
     * node.getType().equals(NodeType.XOR_JOIN_GATEWAY) ||
     * node.getType().equals(NodeType.OR_JOIN_GATEWAY) ||
     * node.getType().equals(NodeType.AND_JOIN_GATEWAY)) .collect(Collectors.toList());
     */

    List<Node> newNodeList = new ArrayList<Node>();

    for (Node node : nodeList)
    {
      Node updatedNode = updateFlows(node, sequenceList);

      if (updatedNode.getType().equals(Node.NodeType.XOR_JOIN_GATEWAY))
      {
        if (updatedNode.getIncomingFlows() == null)
        {
          logger.error("Node {} has no incoming flows (null)", node.getId());
          continue;
        }
        else if (updatedNode.getOutgoingFlows() == null)
        {
          logger.error("Node {} has no outgoing flows (null)", node.getId());
          continue;
        }

        if (updatedNode.getIncomingFlows().size() < updatedNode.getOutgoingFlows().size())
        {
          updatedNode.setType(Node.NodeType.XOR_SPLIT_GATEWAY);
          logger.debug("Updated node gateway!! {}", updatedNode.toString());
        }
      }

      if (updatedNode.getType().equals(Node.NodeType.OR_JOIN_GATEWAY))
      {
        if (updatedNode.getIncomingFlows() == null)
        {
          logger.error("Node {} has no incoming flows (null)", node.getId());
          continue;
        }
        else if (updatedNode.getOutgoingFlows() == null)
        {
          logger.error("Node {} has no outgoing flows (null)", node.getId());
          continue;
        }

        if (updatedNode.getIncomingFlows().size() < updatedNode.getOutgoingFlows().size())
        {
          updatedNode.setType(Node.NodeType.OR_SPLIT_GATEWAY);
          logger.debug("Updated node gateway!! {}", updatedNode.toString());
        }
      }

      if (updatedNode.getType().equals(Node.NodeType.AND_JOIN_GATEWAY))
      {
        if (updatedNode.getIncomingFlows() == null)
        {
          logger.error("Node {} has no incoming flows (null)", node.getId());
          continue;
        }
        else if (updatedNode.getOutgoingFlows() == null)
        {
          logger.error("Node {} has no outgoing flows (null)", node.getId());
          continue;
        }

        if (updatedNode.getIncomingFlows().size() < updatedNode.getOutgoingFlows().size())
        {
          updatedNode.setType(Node.NodeType.AND_SPLIT_GATEWAY);
          logger.debug("Updated node gateway!! {}", updatedNode.toString());
        }
      }

      newNodeList.add(updatedNode);
    }

    return newNodeList;
  }

  private Node updateFlows(Node node, List<Sequence> sequenceList) {
    String nodeName = node.getId();
    Node updatedNode = node;
    List<String> incomingFlows = new ArrayList<>();
    List<String> outgoingFlows = new ArrayList<>();
    // update flows only if flows are not captured during initial parsing
    if (node.getIncomingFlows() == null || node.getIncomingFlows().isEmpty()) {
      for (Sequence sequence : sequenceList) {
        if (sequence.getSource().equals(nodeName)) {
          outgoingFlows.add(sequence.getId());
        }
        if (sequence.getTarget().equals(nodeName)) {
          incomingFlows.add(sequence.getId());
        }
      }
      updatedNode.setIncomingFlows(incomingFlows);
      updatedNode.setOutgoingFlows(outgoingFlows);
    }
    return updatedNode;
  }

}
