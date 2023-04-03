package bpmn.writing.generation.parser; /**
 * 
 */

/**
 * @author ajayk
 * Handles the generic file content and transforms it into object representation.
 */
public interface ContentHandler {

  /**
   * Method transforms a generic file content like BPMN 2.0 xml into object representation.
   */
  public void handle();

  /**
   * @return * Object representation of the transformed file.
   * Get the object representation of transformed file.
   */
  public Object getOutput();
}
