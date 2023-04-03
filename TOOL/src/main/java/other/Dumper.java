package other;

import bpmn.BpmnCategories;
import bpmn.BpmnHeader;
import bpmn.BpmnParser;
import bpmn.BpmnProcess;
import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.writing.generation.GraphicalGenerationWriter;
import main.CommandLineParser;

import java.io.IOException;

public class Dumper
{
	private static Dumper dumper;
	private CommandLineParser commandLineParser;
	private BpmnParser bpmnParser;
	private Dumper()
	{

	}

	public static Dumper getInstance()
	{
		if (dumper == null)
		{
			dumper = new Dumper();
		}

		return dumper;
	}

	public void initializeDumper(final CommandLineParser commandLineParser,
								 final BpmnParser bpmnParser)
	{
		this.commandLineParser = commandLineParser;
		this.bpmnParser = bpmnParser;
	}

	public void dump(final Graph graph,
					 final String dumpValue)
	{
		if (this.commandLineParser == null)
		{
			throw new IllegalStateException("Dumper has not been initialized!");
		}

		final GraphToList graphToList = new GraphToList(graph);
		graphToList.convert();

		final BpmnProcess newBpmnProcess = new BpmnProcess(this.bpmnParser.bpmnProcess().id(), this.bpmnParser.bpmnProcess().isExecutable());
		newBpmnProcess.setObjects(graphToList.objectsList());

		final GraphicalGenerationWriter graphicalGenerationWriter = new GraphicalGenerationWriter(
				this.commandLineParser,
				this.bpmnParser.bpmnHeader(),
				newBpmnProcess,
				this.bpmnParser.bpmnCategories(),
				this.bpmnParser.documentation(),
				dumpValue
		);

		try
		{
			graphicalGenerationWriter.write();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
