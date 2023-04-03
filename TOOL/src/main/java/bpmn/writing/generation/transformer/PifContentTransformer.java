package bpmn.writing.generation.transformer; /**
 * 
 */

import bpmn.writing.generation.pif.*;
import bpmn.writing.generation.pif.Process;
import bpmn.writing.generation.util.BpmnBuilder;
import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author ajayk Transforms PIF to BPMN
 */
public class PifContentTransformer implements ContentTransformer {

	static final Logger logger = LoggerFactory.getLogger(PifContentTransformer.class);

	private File pifInput;
	private File bpmnOutput;

	public PifContentTransformer(File pifInput, File bpmnOutput) {
		this.pifInput = pifInput;
		this.bpmnOutput = bpmnOutput;
	}

	/*
	 * (non-Javadoc) Generates BPMN 2.0 XML from PIF TODO: Quick dirty
	 * implementation - refine
	 */
	@Override
	public void transform() {
		JAXBContext jaxbContext;
		try {
			jaxbContext = JAXBContext.newInstance(Process.class);

			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			Process pifProcess = (Process) jaxbUnmarshaller.unmarshal(pifInput);

			BpmnModel model = new BpmnModel();
			org.activiti.bpmn.model.Process bpmProcess = new org.activiti.bpmn.model.Process();
			BpmnBuilder modelBuilder = new BpmnBuilder();

			bpmProcess.setId(pifProcess.getName());
			bpmProcess.setName(pifProcess.getName());

			InitialEvent startNode = pifProcess.getBehaviour().getInitialNode();
			List<JAXBElement<Object>> endNodes = pifProcess.getBehaviour().getFinalNodes();

			bpmProcess.addFlowElement(modelBuilder.createStartEvent(startNode.getId()));

			for (JAXBElement<Object> node : endNodes) {
				EndEvent event = (EndEvent) node.getValue();
				bpmProcess.addFlowElement(modelBuilder.createEndEvent(event.getId()));

			}

			List<WorkflowNode> pifNodes = pifProcess.getBehaviour().getNodes();

			for (WorkflowNode wfNode : pifNodes) {
				if (wfNode instanceof Task) {
					Task task = (Task) wfNode;
					bpmProcess.addFlowElement(modelBuilder.createUserTask(task.getId(), task.getId()));
				} else if (wfNode instanceof AndJoinGateway) {
					AndJoinGateway gateway = (AndJoinGateway) wfNode;
					bpmProcess
							.addFlowElement(modelBuilder.createParallelGateway(gateway.getId(), gateway.getId(), null));

				} else if (wfNode instanceof AndSplitGateway) {
					AndSplitGateway gateway = (AndSplitGateway) wfNode;
					bpmProcess
							.addFlowElement(modelBuilder.createParallelGateway(gateway.getId(), gateway.getId(), null));

				} else if (wfNode instanceof XOrJoinGateway) {
					XOrJoinGateway gateway = (XOrJoinGateway) wfNode;
					bpmProcess.addFlowElement(
							modelBuilder.createExclusiveGateway(gateway.getId(), gateway.getId(), null));

				} else if (wfNode instanceof XOrSplitGateway) {
					XOrSplitGateway gateway = (XOrSplitGateway) wfNode;
					bpmProcess.addFlowElement(
							modelBuilder.createExclusiveGateway(gateway.getId(), gateway.getId(), null));

				} else if (wfNode instanceof OrJoinGateway) {
					OrJoinGateway gateway = (OrJoinGateway) wfNode;
					bpmProcess.addFlowElement(
							modelBuilder.createInclusiveGateway(gateway.getId(), gateway.getId(), null));
				} else if (wfNode instanceof OrSplitGateway) {
					OrSplitGateway gateway = (OrSplitGateway) wfNode;
					bpmProcess.addFlowElement(
							modelBuilder.createInclusiveGateway(gateway.getId(), gateway.getId(), null));

				} else {
					logger.error("Unable to determine the PIF node instance - {}: {}", wfNode.getId(),
							wfNode.getClass().getName());
				}
			}

			List<SequenceFlow> flows = pifProcess.getBehaviour().getSequenceFlows();

			// List<org.activiti.main.java.bpmn.model.SequenceFlow> bpmFlows = new
			// ArrayList<>();
			for (SequenceFlow flow : flows) {
				org.activiti.bpmn.model.SequenceFlow bpmFlow = modelBuilder.createSequenceFlow(flow.getSource().getId(),
						flow.getTarget().getId());
				bpmProcess.addFlowElement(bpmFlow);
			}

			model.addProcess(bpmProcess);
			new BpmnAutoLayout(model).execute();
			byte[] bpmnXml = new BpmnXMLConverter().convertToXML(model);

			FileOutputStream fileOutputStream = new FileOutputStream(bpmnOutput);
			IOUtils.write(bpmnXml, fileOutputStream);

		} catch (JAXBException | IOException e) {
			bpmnOutput.delete();
			logger.error("Error transforming the input", e);
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * fr.inria.convecs.optimus.transformer.ContentTransformer#generateOutput()
	 */
	@Override
	public void generateOutput() {
		// TODO Auto-generated method stub

	}

}
