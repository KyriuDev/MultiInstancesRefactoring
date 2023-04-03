package bpmn.writing.generation.util; /**
 * 
 */

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import java.io.File;

/**
 * @author ajayk
 *
 */
public class XmlUtil {

  private static final Logger logger = LoggerFactory.getLogger(XmlUtil.class);

  public static boolean isDocumentValid(File inputXml, File schema) {
    boolean isValid = false;

    try {
      XMLValidationSchemaFactory schemaFactory = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
      XMLValidationSchema xsd = schemaFactory.createSchema(schema);
      XMLInputFactory2 inputFactory = (XMLInputFactory2) XMLInputFactory.newInstance();
      XMLStreamReader2 streamReader = inputFactory.createXMLStreamReader(inputXml);

      streamReader.validateAgainst(xsd);
      while (streamReader.hasNext()) {
        streamReader.next();
      }

      isValid = true;
    } catch (Exception e) {
      logger.warn("Error while validating {} against the schema {}", inputXml.getName(),
          schema.getName(), e);
    }

    return isValid;
  }
}
