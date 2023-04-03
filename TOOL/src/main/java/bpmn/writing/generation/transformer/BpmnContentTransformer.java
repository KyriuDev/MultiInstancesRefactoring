package bpmn.writing.generation.transformer; /**
 * 
 */

import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author ajayk
 *
 */
public class BpmnContentTransformer implements ContentTransformer {

	static final Logger logger = LoggerFactory.getLogger(BpmnContentTransformer.class);

	private String input;
	private String output;

	public BpmnContentTransformer(String input) {
		this.input = input;
	}

	@Override
	public void transform() {

		InputStream inputstream;
		try {
			inputstream = IOUtils.toInputStream(input, StandardCharsets.UTF_8.name());
			BpmnXMLConverter converter = new BpmnXMLConverter();
			XMLInputFactory factory = XMLInputFactory.newInstance();
			XMLStreamReader reader = factory.createXMLStreamReader(inputstream);
			BpmnModel model = converter.convertToBpmnModel(reader);
			new BpmnAutoLayout(model).execute();
			byte[] bpmnXml = new BpmnXMLConverter().convertToXML(model);
			output = new String(bpmnXml);
		} catch (XMLStreamException | IOException ioe) {
			logger.error("Error transforming the input", ioe);
			throw new RuntimeException(ioe);
		}

	}

	@Override
	public void generateOutput() {
		// TODO Auto-generated method stub

	}
	
	public String getBpmnLayout() {
		return output;
	}

}
