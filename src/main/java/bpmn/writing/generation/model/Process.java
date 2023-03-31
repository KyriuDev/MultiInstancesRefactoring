package bpmn.writing.generation.model; /**
 * 
 */


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ajayk
 *
 */
public class Process {

  private String id;
  private List<Node> nodes;
  private List<Sequence> sequences;
  private Map<String, Node> nodeMap;
  private Map<String, Sequence> sequenceMap;

  /**
   * @param processId
   * @param nodes
   * @param sequences
   */
  public Process(String processId, List<Node> nodes, List<Sequence> sequences) {
    super();
    this.id = processId;
    this.nodes = nodes;
    this.sequences = sequences;
    this.nodeMap = null;
    this.sequenceMap = null;
  }

  /**
   * @return the nodes
   */
  public List<Node> getNodes() {
    List<Node> nodeList = new ArrayList<Node>();
    for (Node node : this.nodes) {
      nodeList.add((Node) node.clone());
    }
    return nodeList;
  }

  /**
   * @param nodes
   *          the nodes to set
   */
  public void setNodes(List<Node> nodes) {
    for (Node node : nodes) {
      this.nodes.add((Node) node.clone());
    }
  }

  /**
   * @return the sequences
   */
  public List<Sequence> getSequences() {
    List<Sequence> sequenceList = new ArrayList<Sequence>();
    for (Sequence sequence : this.sequences) {
      sequenceList.add((Sequence) sequence.clone());
    }
    return sequenceList;
  }

  /**
   * the sequences to set.
   * 
   * @param sequences
   * 
   */
  public void setSequences(List<Sequence> sequences) {
    for (Sequence sequence : sequences) {
      this.sequences.add((Sequence) sequence.clone());
    }
  }

  /**
   * return id.
   * 
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * the id to set.
   * 
   * @param id
   * 
   */
  public void setId(String id) {
    this.id = id;
  }

  /***
   * 
   * @param nodeType
   * @return
   */
  public List<Node> getNodes(Node.NodeType nodeType) {

    List<Node> nodeList = new ArrayList<Node>();
    for (Node node : this.nodes) {
      if (node.getType().equals(nodeType)) {
        nodeList.add(node);
      }
    }

    return nodeList;
  }

  /***
   * 
   * @return
   */
  public Map<String, Node> getNodeMap() {
    if (null == this.nodeMap) {
      this.nodeMap = new HashMap<String, Node>();
      for (Node node : this.nodes) {
        nodeMap.put(node.getId(), node);
      }
    }
    return this.nodeMap;
  }

  /**
   * 
   * @return
   */
  public Map<String, Sequence> getSequenceMap() {
    if (null == this.sequenceMap) {
      this.sequenceMap = new HashMap<String, Sequence>();
      for (Sequence seq : this.sequences) {
        sequenceMap.put(seq.getId(), seq);
      }
    }
    return this.sequenceMap;
  }
}
