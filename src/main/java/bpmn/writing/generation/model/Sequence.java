package bpmn.writing.generation.model; /**
 * 
 */


/**
 * @author ajayk
 *
 */
public class Sequence {

  private String id;
  private String source;
  private String target;

  /**
   * @param id
   * @param source
   * @param target
   */
  public Sequence(String id, String source, String target) {
    super();
    this.id = id;
    this.source = source;
    this.target = target;
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
   * @return the source
   */
  public String getSource() {
    return source;
  }

  /**
   * @param source
   *          the source to set
   */
  public void setSource(String source) {
    this.source = source;
  }

  /**
   * @return the target
   */
  public String getTarget() {
    return target;
  }

  /**
   * @param target
   *          the target to set
   */
  public void setTarget(String target) {
    this.target = target;
  }

  /**
   * Clone method
   */
  public Object clone() {
    Sequence sequence = new Sequence(this.id, this.source, this.target);
    return sequence;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("[").append("Id: ").append(this.id).append(", ").append("Source: ")
        .append(this.source).append(", ").append("Target: ").append(this.target).append("]");

    return result.toString();
  }
}
