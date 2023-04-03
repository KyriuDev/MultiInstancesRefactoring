package bpmn.writing.generation.transformer; /**
 * 
 */


/**
 * @author ajayk
 *
 */
public interface ContentTransformer {

  /**
   * Transforms the object representation into PIF xml.
   */
  public void transform();

  /**
   * Generates the PIF xml in specified format.
   */
  public void generateOutput();

}
