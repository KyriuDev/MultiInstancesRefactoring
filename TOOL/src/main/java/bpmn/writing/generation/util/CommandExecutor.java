package bpmn.writing.generation.util; /**
 * 
 */


import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author ajayk
 *     Utility class to execute system commands. 
 *     Internally uses ProcessBuilder
 */
public class CommandExecutor {

  private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);

  private String stdOut;
  private String stdError;
  private List<String> command;
  private File directory;

  /**
   * 
   * @param command Command and list of options
   * @param directory The directory context where command will be executed.
   */
  public CommandExecutor(List<String> command, File directory) {
    this.command = command;
    this.directory = directory;
  }

  public int executeCommand() {
    int intValue = -99;
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.directory(directory);
      Process process = processBuilder.start();

      InputStream output = process.getInputStream();
      InputStream error = process.getErrorStream();

      stdOut = IOUtils.toString(output, StandardCharsets.UTF_8.name());
      stdError = IOUtils.toString(error, StandardCharsets.UTF_8.name());

      intValue = process.waitFor();
    } catch (IOException ioe) {
      logger.warn("Execption executing the system command", ioe);
      throw new RuntimeException(ioe);

    } catch (InterruptedException ie) {
      logger.warn("InterruptedException - Unable to get the exit value", ie);
      throw new RuntimeException(ie);
    }
    return intValue;
  }

  public String getErrors() {
    return stdError;
  }

  public String getOutput() {
    return stdOut;
  }
}
