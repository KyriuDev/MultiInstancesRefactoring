package bpmn.writing.generation;

import bpmn.*;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.SequenceFlow;
import bpmn.types.process.Task;
import bpmn.types.process.events.Event;
import bpmn.writing.direct.DirectWriter;
import bpmn.writing.generation.service.RuntimeValidationService;
import bpmn.writing.generation.transformer.PifContentTransformer;
import constants.CommandLineOption;
import main.CommandLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import resources.Resource;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;


public class GraphicalGenerationWriter
{
    private static final Logger logger = LoggerFactory.getLogger(GraphicalGenerationWriter.class);
    private static final String TMP_BPMN_FILE = "tmp_diagram.bpmn";
    private static final String REFACTORED_BPMN_FILE = "refactored_process.bpmn";
    private final CommandLineParser commandLineParser;
    private final BpmnHeader header;
    private final BpmnProcess process;
    private final BpmnCategories categories;
    private final String documentation;
    private File pifFile;

    public GraphicalGenerationWriter(CommandLineParser commandLineParser,
                                     BpmnHeader header,
                                     BpmnProcess process,
                                     BpmnCategories categories,
                                     String documentation)
    {

        this.commandLineParser = commandLineParser;
        this.header = header;
        this.process = process;
        this.categories = categories;
        this.documentation = documentation;
    }

    public void write() throws IOException
    {
        this.writeTmpBpmnFile();
        this.generatePif();
        this.writeFinalBpmnFile();
        this.deleteTmpFiles();
        this.correctFinalBpmnFile();
    }

    //Private methods

    private void writeTmpBpmnFile() throws IOException
    {
        DirectWriter directWriter = new DirectWriter(
                Paths.get(((File) this.commandLineParser.get(CommandLineOption.WORKING_DIRECTORY)).getPath(), TMP_BPMN_FILE).toString(),
                this.header,
                this.process,
                null,
                this.categories,
                this.documentation
        );

        directWriter.writeInitialBpmnFile();
    }

    private void generatePif()
    {
        final RuntimeValidationService runtimeValidationService = new RuntimeValidationService();
        final File temporaryFile = new File(Paths.get(((File) this.commandLineParser.get(CommandLineOption.WORKING_DIRECTORY)).getPath(), TMP_BPMN_FILE).toString());
        this.pifFile = runtimeValidationService.parseAndTransform(temporaryFile);

        if (pifFile.exists())
        {
            logger.info("PIF file was generated.");
        }
        else
        {
            logger.error("PIF file was not generated!");
        }
    }

    private void writeFinalBpmnFile()
    {
        final File finalFile = new File((Paths.get(((File) this.commandLineParser.get(CommandLineOption.WORKING_DIRECTORY)).getPath(), REFACTORED_BPMN_FILE).toString()));
        final PifContentTransformer pifContentTransformer = new PifContentTransformer(this.pifFile, finalFile);
        pifContentTransformer.transform();
        logger.info("BPMN file was generated.");
    }

    private void deleteTmpFiles()
    {
        final boolean tmpBpmnDeleted = new File((Paths.get(((File) this.commandLineParser.get(CommandLineOption.WORKING_DIRECTORY)).getPath(), TMP_BPMN_FILE).toString())).delete();
        final boolean pifDeleted = this.pifFile.delete();

        if (!tmpBpmnDeleted
            || !pifDeleted)
        {
            logger.error("Temporary files were not properly deleted.");
        }
    }

    private void correctFinalBpmnFile()
    {
        final ArrayList<BpmnProcessObject> oldObjects = this.process.objects();
        final File generatedFile = new File((Paths.get(((File) this.commandLineParser.get(CommandLineOption.WORKING_DIRECTORY)).getPath(), REFACTORED_BPMN_FILE).toString()));
        final BpmnParser parser;

        try
        {
            parser = new BpmnParser(generatedFile, false, false, false);
            parser.parse();
        }
        catch (ParserConfigurationException | IOException | SAXException e)
        {
            throw new RuntimeException("An exception has occurred during the parsing of the generated BPMN file.");
        }

        for (BpmnProcessObject generatedObject : parser.bpmnProcess().objects())
        {
            if (generatedObject instanceof SequenceFlow)
            {
                final SequenceFlow newFlow = (SequenceFlow) generatedObject;
                final SequenceFlow oldFlow = getOldFlowFromNewFlow(newFlow, oldObjects);

                if (oldFlow == null)
                {
                    return;
                }

                newFlow.setProbability(oldFlow.probability());
            }
            else if (generatedObject instanceof Task)
            {
                final Task oldTask = (Task) getObjectFromID(generatedObject.id(), oldObjects);
                final StringBuilder builder = new StringBuilder();
                builder.append(oldTask.name());

                if (oldTask.duration() != -1)
                {
                    builder.append(" (")
                            .append(oldTask.duration())
                            .append(")");
                }

                if (!oldTask.resourceUsage().resources().isEmpty())
                {
                    builder.append(" &#60;");
                    String separator = "";

                    for (Resource resource : oldTask.resourceUsage().resources())
                    {
                        final int usage = oldTask.resourceUsage().getUsageOf(resource);
                        builder.append(separator)
                                .append(usage)
                                .append(" ")
                                .append(resource.name());

                        separator = ", ";
                    }

                    builder.append("&#62;");
                }

                generatedObject.setName(builder.toString());
            }
            else if (!(generatedObject instanceof Event))
            {
                generatedObject.setName("");
            }
        }

        final DirectWriter directWriter;

        try
        {
            directWriter = new DirectWriter(
                    generatedFile.getPath(),
                    parser.bpmnHeader(),
                    parser.bpmnProcess(),
                    parser.bpmnDiagram(),
                    parser.bpmnCategories(),
                    parser.documentation()
            );

            directWriter.writeInitialBpmnFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write corrected BPMN file.");
        }
    }

    private BpmnProcessObject getObjectFromID(String id,
                                              ArrayList<BpmnProcessObject> objects)
    {
        for (BpmnProcessObject object : objects)
        {
            if (object.id().equals(id))
            {
                return object;
            }
        }

        logger.error("No object with id {} was found in the object list.", id);
        throw new IllegalStateException();
    }

    private SequenceFlow getOldFlowFromNewFlow(final SequenceFlow newFlow,
                                               final ArrayList<BpmnProcessObject> objects)
    {
        for (BpmnProcessObject object : objects)
        {
            if (object instanceof SequenceFlow)
            {
                if (newFlow.sourceRef().equals(((SequenceFlow) object).sourceRef())
                    && newFlow.targetRef().equals(((SequenceFlow) object).targetRef()))
                {
                    return (SequenceFlow) object;
                }
            }
        }

        if (newFlow.sourceRef().toLowerCase(Locale.ROOT).contains("event")
            || newFlow.targetRef().toLowerCase(Locale.ROOT).contains("event"))
        {
            return null;
        }

        throw new IllegalStateException("Flow between |" + newFlow.sourceRef() + "| and |" + newFlow.targetRef() + "| " +
                "was not found.");
    }
}
