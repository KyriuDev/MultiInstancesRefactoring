package bpmn.writing.generation.validator; /**
 * 
 */


import bpmn.writing.generation.util.CommandExecutor;
import bpmn.writing.generation.util.PifUtil;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author silverquick
 *
 */
public class RuntimeValidator implements ModelValidator {

	private Logger logger = LoggerFactory.getLogger(ModelValidator.class);

	private String scriptsFolder;

	private String outputFolder;

	private String result;

	public RuntimeValidator(String scriptsFolder, String outputFolder) {
		this.scriptsFolder = scriptsFolder;
		this.outputFolder = outputFolder;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.inria.convecs.optimus.validator.ModelValidator#validate(java.io.File, java.lang.String)
	 */
	@Override
	public void validate(final File modelFile, final List<String> options) {

		validate(modelFile, modelFile, options);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.inria.convecs.optimus.validator.ModelValidator#validate(java.io.File, java.io.File,
	 * java.lang.String)
	 */
	@Override
	public void validate(final File modelFile1, final File modelFile2, final List<String> options) {
		Boolean isBalanced = false;
		isBalanced = PifUtil.isPifBalanced(modelFile1);
		//isBalanced = isBalanced && PifUtil.isPifBalanced(modelFile2);
		logger.debug("The input isBalanced? : {}", isBalanced);
		List<String> vbpmnCommand = new ArrayList<String>();
		vbpmnCommand.add("python");
		if(isBalanced)
			vbpmnCommand.add(scriptsFolder + File.separator + "vbpmn2.py");
		else
			vbpmnCommand.add(scriptsFolder + File.separator + "vbpmn2.py");
		vbpmnCommand.add(modelFile1.getAbsolutePath());
		vbpmnCommand.add(modelFile2.getAbsolutePath());
		vbpmnCommand.addAll(options);
		logger.debug("The command is: {}", vbpmnCommand.toString());
		try {
			File outputDirectory = new File(outputFolder);
			Files.copy(new File(scriptsFolder+ File.separator + "bpmntypes.lnt").toPath(), 
					new File(outputFolder+ File.separator +"bpmntypes.lnt").toPath());
			CommandExecutor commandExecutor = new CommandExecutor(vbpmnCommand, outputDirectory);
			int execResult = commandExecutor.executeCommand();

			logger.debug("The return value of execution of command is: {}", execResult);

			String response = handleResponse(commandExecutor.getOutput().trim(),
					commandExecutor.getErrors().trim());

			StringBuilder resultBuilder = new StringBuilder();
			if(response.equalsIgnoreCase("TRUE") || response.equalsIgnoreCase("FALSE"))
			{
				resultBuilder.append(response).append("|");
				String dotModel1 = generateautfile(modelFile1.getAbsolutePath().replace(".pif", ".bcg"));
				//String dotModel2 = generateautfile(modelFile2.getAbsolutePath().replace(".pif", ".bcg"));
				resultBuilder.append(dotModel1).append("|");
				//resultBuilder.append(dotModel2);
				if (response.equalsIgnoreCase("FALSE")) {
					String bcgFileName = "bisimulator.bcg";
					if(options.contains("property-implied") || options.contains("property-and"))
						bcgFileName = "evaluator.bcg";
					File bcgFile = new File(outputFolder + File.separator + bcgFileName);
					String autBcg = generateautfile(bcgFile.getAbsolutePath());
					resultBuilder.append("|").append(autBcg);
				}
				this.result = resultBuilder.toString();
			}
			else 
			{
				this.result = response;
			}

		} catch (Exception e) {
			logger.error("Failed executing the command", e);
			throw new RuntimeException(e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.inria.convecs.optimus.validator.ModelValidator#getResult()
	 */
	@Override
	public String getResult() {
		return this.result;
	}

	/**
	 * 
	 * @param commandExecutor
	 * @return
	 */
	private String handleResponse(final String stdOut, final String stdErr) {
		StringBuilder responseBuilder = new StringBuilder();

		if (null != stdErr && !stdErr.isEmpty()) 
		{
			logger.debug("The stderr of command execution: {}", stdErr);
			responseBuilder.append("Std error executing the command: ").append(stdErr);

		} 
		else if (null != stdOut && !stdOut.isEmpty()) 
		{
			logger.debug("The stdout of command execution: {}", stdOut);
			// TODO: crude method -cleaner approach required
			if (stdOut.contains("ERROR")) 
			{
				responseBuilder.append("Internal error executing the command: ").append(stdOut);
			} 
			else 
			{
				List<String> resp = Arrays.asList(stdOut.split("\n"));
				List<String> respLastLines = new ArrayList<String>(resp.subList(Math.max(0, resp.size() - 3), resp.size()));

				responseBuilder.append(respLastLines);
			}
		} 
		else 
		{
			responseBuilder.append("*** Unable to process request - No Result Available ***");
		}

		return responseBuilder.toString();
	}

	private String generateautfile(String absolutePath) throws IOException, InterruptedException {
		String autfile = absolutePath.replace(".bcg", ".aut");
		logger.debug("aut file: {}", autfile);
		List<String> command = new ArrayList<String>();
		command.add("bcg_io");
		command.add(absolutePath);
		command.add(autfile);

		CommandExecutor commandExecutor = new CommandExecutor(command, new File(outputFolder));
		int execResult = commandExecutor.executeCommand();

		logger.debug("The exec result of command [ {} ] is {}", command, execResult);

		if (execResult != 0) {
			throw new RuntimeException("Erorr executing BCG draw - " + commandExecutor.getErrors());
		}

		File outputFile = new File(autfile);

		String dotOutput = FileUtils.readFileToString(outputFile, "UTF-8");
		dotOutput = dotOutput.replaceAll("\\R", " "); // Java 8 carriage return replace

		return dotOutput.trim();
	}
}
