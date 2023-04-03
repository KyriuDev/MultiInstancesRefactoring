package bpmn.writing.generation.service; /**
 * 
 */

import bpmn.writing.generation.config.AppProperty;
import bpmn.writing.generation.transformer.BpmnContentTransformer;
import bpmn.writing.generation.transformer.ContentTransformer;
import bpmn.writing.generation.transformer.PifContentTransformer;
import bpmn.writing.generation.util.VbpmnExceptionMapper;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * @author ajayk
 *
 */
@Path("/transform")
public class TransformationService {

	private static final Logger logger = LoggerFactory.getLogger(TransformationService.class);

	private static final String OUTPUT_PATH = AppProperty.getInstance().getFolder("OUTPUT_PATH");

	private static final String SCRIPTS_PATH = "/WEB-INF/classes";

	private static final String PIF_SCHEMA = "/pif.xsd";

	@Context ServletContext servletContext;

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces("text/plain")
	@Path("/pif2bpmn")
	public Response validateVbpmn(@FormDataParam("file1") InputStream fileStream1,
			@FormDataParam("file1") FormDataContentDisposition fileInfo1, FormDataMultiPart formData) {

		Response httpResponse = null;
		try {      

			String outputDir = Files.createTempDirectory(Paths.get(OUTPUT_PATH), "vbpmn_")
					.toAbsolutePath().toString();

			File pifInput = new File(outputDir + File.separator + fileInfo1.getFileName());
			Files.copy(fileStream1, pifInput.toPath(), StandardCopyOption.REPLACE_EXISTING);
			
			File bpmnOutput = new File(outputDir + File.separator + fileInfo1.getFileName()+".bpmn");
			
			ContentTransformer pifContentTransformer = new PifContentTransformer(pifInput, bpmnOutput);
			pifContentTransformer.transform();
			
			String bpmnResult = IOUtils.toString(new FileInputStream(bpmnOutput), StandardCharsets.UTF_8);
			
			httpResponse = Response.status(Status.OK).entity(bpmnResult).build();
			
			return httpResponse;

		} catch (Exception e) {
			logger.error("Error processing request: ", e);
			throw VbpmnExceptionMapper.createWebAppException(e);
		}
	}
	
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	@Path("/bpmn")
	public Response generateBpmnLayout(String inputBpmn) {

		Response httpResponse = null;
		try {      
			
			logger.info("bpmn xml {}", inputBpmn);
			BpmnContentTransformer transformer = new BpmnContentTransformer(inputBpmn);
			transformer.transform();
			String bpmnResult = transformer.getBpmnLayout();
			httpResponse = Response.status(Status.OK).entity(bpmnResult).build();
			
			return httpResponse;

		} catch (Exception e) {
			logger.error("Error processing request: ", e);
			throw VbpmnExceptionMapper.createWebAppException(e);
		}
	}
}
