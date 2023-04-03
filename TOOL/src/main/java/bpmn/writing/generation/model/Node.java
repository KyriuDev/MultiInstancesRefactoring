package bpmn.writing.generation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ajayk
 *
 */
public class Node {

  public enum NodeType {
    // http://stackoverflow.com/questions/3978654/best-way-to-create-enum-of-strings
    INITIAL_EVENT("InitialEvent"), END_EVENT("EndEvent"), OR_SPLIT_GATEWAY(
        "OrSplitGateway"), OR_JOIN_GATEWAY("OrJoinGateway"), XOR_SPLIT_GATEWAY(
            "XOrSplitGateway"), XOR_JOIN_GATEWAY("XOrJoinGateway"), AND_JOIN_GATEWAY(
                "AndJoinGateway"), AND_SPLIT_GATEWAY("AndSplitGateway"), TASK("Task");

    private final String value;

    private NodeType(final String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }

  }

  private String id;
  private NodeType type;
  private List<String> incomingFlows;
  private List<String> outgoingFlows;

  public Node() {
    // default constructor
  }

  /**
   * @param id
   * @param type
   * @param incomingFlows
   * @param outgoingFlows
   */
  public Node(String id, NodeType type, List<String> incomingFlows, List<String> outgoingFlows) {
    super();
    this.id = id;
    this.type = type;
    this.incomingFlows = incomingFlows;
    this.outgoingFlows = outgoingFlows;
  }

  /**
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * @param id
   *          the id to set
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * @return the type
   */
  public NodeType getType() {
    return type;
  }

  /**
   * @param type
   *          the type to set
   */
  public void setType(NodeType type) {
    this.type = type;
  }

  /**
   * @return the incomingFlows
   */
  public List<String> getIncomingFlows() {
    if (!(null == incomingFlows || incomingFlows.isEmpty())) {
      return new ArrayList<String>(this.incomingFlows);
    } else {
      return null;
    }
  }

  /**
   * @param incomingFlows
   *          the incomingFlows to set
   */
  public void setIncomingFlows(List<String> incomingFlows) {
    if (!(null == incomingFlows || incomingFlows.isEmpty())) {
      this.incomingFlows = new ArrayList<String>(incomingFlows);
    }
  }

  /**
   * @return the outgoingFlows
   */
  public List<String> getOutgoingFlows() {
    if (!(null == outgoingFlows || outgoingFlows.isEmpty())) {
      return new ArrayList<String>(this.outgoingFlows);
    } else {
      return null;
    }
  }

  /**
   * @param outgoingFlows
   *          the outgoingFlows to set
   */
  public void setOutgoingFlows(List<String> outgoingFlows) {
    if (!(null == outgoingFlows || outgoingFlows.isEmpty())) {
      this.outgoingFlows = new ArrayList<String>(outgoingFlows);
    }
  }

  /**
   * Clone method
   */
  public Object clone() {
    Node node = new Node(this.id, this.getType(), this.incomingFlows, this.outgoingFlows);
    return node;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("[").append("Id: ").append(this.getId()).append(", ").append("Type: ")
        .append(this.type).append(", ").append("Incoming flows: ");
    //if (null != this.incomingFlows && !incomingFlows.isEmpty()) {
      result.append(this.incomingFlows == null ? null : this.incomingFlows.toString());
    //}
    result.append(", ").append("Outgoing flows: ");
    //if (null != this.outgoingFlows && !outgoingFlows.isEmpty()) {
      result.append(this.outgoingFlows == null ? null : this.outgoingFlows.toString());
    //}
    result.append("]");
    return result.toString();
  }
}
