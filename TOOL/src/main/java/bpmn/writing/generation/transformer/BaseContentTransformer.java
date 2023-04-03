package bpmn.writing.generation.transformer; /**
 * 
 */


import bpmn.writing.generation.model.Node;
import bpmn.writing.generation.model.Process;
import bpmn.writing.generation.model.Sequence;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * @author ajayk
 *
 */
public class BaseContentTransformer implements ContentTransformer {

  private static final String PIF_PREFIX = "pif";

  static final Logger logger = LoggerFactory.getLogger(BaseContentTransformer.class);

  private static String PIF_URI = "http://www.example.org/PIF";
  private static String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";
  private Process process;
  private File output;

  public BaseContentTransformer(Process process, File output) {
    this.process = process;
    this.output = output;

  }

  /*
   * (non-Javadoc)
   * 
   * @see fr.inria.convecs.optimus.transformer.ContentTransformer#transform()
   */
  @Override
  public void transform() {
    try {
      XMLOutputFactory2 xmlOutputFactory = (XMLOutputFactory2) XMLOutputFactory.newFactory();
      XMLStreamWriter2 xmlStreamWriter = (XMLStreamWriter2) xmlOutputFactory
          .createXMLStreamWriter(new FileWriter(output));
      xmlStreamWriter.writeStartDocument("utf-8", "1.0");
      xmlStreamWriter.setPrefix(PIF_PREFIX, PIF_URI);
      xmlStreamWriter.setPrefix("xsi", XSI_URI);
      xmlStreamWriter.writeStartElement(PIF_URI, "Process");
      xmlStreamWriter.writeNamespace(PIF_PREFIX, PIF_URI);
      xmlStreamWriter.writeNamespace("xsi", XSI_URI);

      writeElements(xmlStreamWriter);

      xmlStreamWriter.writeEndElement();
      xmlStreamWriter.writeEndDocument();

    } catch (XMLStreamException | IOException ioe) {
      output.delete();
      logger.error("Error transforming the input", ioe);
      throw new RuntimeException(ioe);
    }

  }

  private void writeElements(XMLStreamWriter2 xmlStreamWriter) throws XMLStreamException {
    xmlStreamWriter.writeStartElement(PIF_URI, "name");
    xmlStreamWriter.writeCharacters(this.process.getId());
    xmlStreamWriter.writeEndElement();

    // TODO: Handle the documentation
    xmlStreamWriter.writeStartElement(PIF_URI, "documentation");
    xmlStreamWriter.writeCharacters("Dummy text for documentation");
    xmlStreamWriter.writeEndElement();

    xmlStreamWriter.writeStartElement(PIF_URI, "behaviour");

    writeNodes(xmlStreamWriter);

    writeSequenceFlows(xmlStreamWriter);

    // InitialNode
    for (Node node : process.getNodes(Node.NodeType.INITIAL_EVENT)) {
      xmlStreamWriter.writeStartElement(PIF_URI, "initialNode");
      xmlStreamWriter.writeCharacters(node.getId());
      xmlStreamWriter.writeEndElement();
    }

    for (Node node : process.getNodes(Node.NodeType.END_EVENT)) {
      xmlStreamWriter.writeStartElement(PIF_URI, "finalNodes");
      xmlStreamWriter.writeCharacters(node.getId());
      xmlStreamWriter.writeEndElement();
    }

    xmlStreamWriter.writeEndElement();

  }

  private void writeSequenceFlows(XMLStreamWriter2 xmlStreamWriter) throws XMLStreamException {

    List<Sequence> sequenceList = process.getSequences();

    for (Sequence sequence : sequenceList) {
      xmlStreamWriter.writeStartElement(PIF_URI, "sequenceFlows");
      xmlStreamWriter.writeAttribute("id", sequence.getId());
      xmlStreamWriter.writeAttribute("source", sequence.getSource());
      xmlStreamWriter.writeAttribute("target", sequence.getTarget());
      xmlStreamWriter.writeEndElement();
    }

  }

  private void writeNodes(XMLStreamWriter2 xmlStreamWriter) throws XMLStreamException {
    List<Node> nodeList = process.getNodes();

    for (Node node : nodeList) {
      xmlStreamWriter.writeStartElement(PIF_URI, "nodes");
      xmlStreamWriter.writeAttribute("id", node.getId());
      xmlStreamWriter.writeAttribute(XSI_URI, "type", PIF_PREFIX + ":" + node.getType().toString());

      // incoming flows
      List<String> incomingFlows = node.getIncomingFlows();
      if (null != incomingFlows) {
        for (String flow : incomingFlows) {
          xmlStreamWriter.writeStartElement(PIF_URI, "incomingFlows");
          xmlStreamWriter.writeCharacters(flow);
          xmlStreamWriter.writeEndElement();
        }
      }

      // outgoing flows
      List<String> outgoingFlows = node.getOutgoingFlows();
      if (null != outgoingFlows) {
        for (String flow : outgoingFlows) {
          xmlStreamWriter.writeStartElement(PIF_URI, "outgoingFlows");
          xmlStreamWriter.writeCharacters(flow);
          xmlStreamWriter.writeEndElement();
        }
      }

      xmlStreamWriter.writeEndElement();
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see fr.inria.convecs.optimus.transformer.ContentTransformer#generateOutput()
   */
  @Override
  public void generateOutput() {
    //TODO: implement handling of any specific type of output
  }

}
