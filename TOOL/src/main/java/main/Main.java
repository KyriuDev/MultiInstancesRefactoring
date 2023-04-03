package main;

import bpmn.BpmnParser;
import other.Dumper;
import other.Utils;
import bpmn.graph.Graph;
import bpmn.graph.GraphToList;
import bpmn.graph.ListToGraph;
import bpmn.types.process.BpmnProcessFactory;
import bpmn.types.process.BpmnProcessObject;
import bpmn.writing.generation.GraphicalGenerationWriter;
import constants.CommandLineOption;
import loops_management.LoopFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import probabilities.ProbabilityCorrecter;
import refactoring.ProblematicTasksFinder;
import refactoring.TasksBalancer;
import refactoring.dependencies.DependenciesAnalyzer;
import refactoring.dependencies.DependenciesFinder;
import refactoring.exceptions.BadDependencyException;
import refactoring.dependencies.DependenciesParser;
import refactoring.partial_order_to_bpmn.AbstractGraphsGenerator;
import refactoring.partial_order_to_bpmn.BPMNGenerator;
import resources.GlobalResourceSet;
import resources.LightOptimizer;
import resources.Optimizer;
import resources.ResourcePool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args)
    {
        final long startParseArgs = System.nanoTime();
        final CommandLineParser commandLineParser;

        try
        {
            commandLineParser = new CommandLineParser(args);
        }
        catch (FileNotFoundException e)
        {
            throw new IllegalStateException("Some necessary files have not be found or are not valid.");
        }

        final long parseArgsTime = System.nanoTime() - startParseArgs;

        //Parse the BPMN file
        final long startParseBPMN = System.nanoTime();
        final BpmnParser parser;

        try
        {
            parser = new BpmnParser((File) commandLineParser.get(CommandLineOption.BPMN_PROCESS));
            parser.parse();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            logger.error("BPMN process \"{}\" could not be parsed properly. Exiting.", ((File) commandLineParser.get(CommandLineOption.BPMN_PROCESS)).getPath());
            return;
        }

        final ArrayList<BpmnProcessObject> objects = parser.bpmnProcess().objects();
        BpmnProcessFactory.setObjectIDs(objects);
        final long parseBPMNTime = System.nanoTime() - startParseBPMN;

        //Compute the set of all resources used
        final long startComputeGlobalResources = System.nanoTime();
        final GlobalResourceSet globalResourceSet = new GlobalResourceSet(objects);
        globalResourceSet.computeGlobalResources();
        final long globalResComputationTime = System.nanoTime() - startComputeGlobalResources;

        //BPMN objects -> BPMN Graph
        final long startListToGraphInit = System.nanoTime();
        final ListToGraph listToGraph = new ListToGraph(objects);
        Graph graph = listToGraph.convert();
        final long initListToGraphTime = System.nanoTime() - startListToGraphInit;

        //Compute all loops in the BPMN process
        final long startLoopFinder = System.nanoTime();
        final LoopFinder loopFinder = new LoopFinder(graph);
        loopFinder.findLoops();
        final long loopFinderTime = System.nanoTime() - startLoopFinder;

        //Correct probabilities
        final long startCorrectingProbas = System.nanoTime();
        final ProbabilityCorrecter probabilityCorrecter = new ProbabilityCorrecter(graph, loopFinder);
        probabilityCorrecter.correctProbabilities();
        final long probaCorrectionTime = System.nanoTime() - startCorrectingProbas;

        //Compute execution time of the initial process
        final long startComputeInitExecTime = System.nanoTime();
        final LightOptimizer originalLightOptimizer = new LightOptimizer(objects, graph);
        final int originalProcessExecutionTime = originalLightOptimizer.computeProcessExecutionTime();
        final long initExecTimeComputeTime = System.nanoTime() - startComputeInitExecTime;

        //Compute resource pool needed by the original process
        final Optimizer optimizer1 = new Optimizer(objects, graph, globalResourceSet, (Integer) commandLineParser.get(CommandLineOption.IAT));
        final ResourcePool optimalPool1 = optimizer1.computeOptimalPoolForNProcesses();

        //Parse dependencies, build dependency graphs and build clusters
        final long startParsingDependencies = System.nanoTime();
        final DependenciesParser dependenciesParser = new DependenciesParser((File) commandLineParser.get(CommandLineOption.DEPENDENCIES), graph, loopFinder);

        try
        {
            dependenciesParser.parseFromFile();
        }
        catch (FileNotFoundException | BadDependencyException e)
        {
            throw new IllegalStateException(e);
        }

        final long parsingDependenciesTime = System.nanoTime() - startParsingDependencies;

        //Verify that dependencies validate SCC conditions
        final long startVerifyingSCC = System.nanoTime();
        final DependenciesAnalyzer dependenciesAnalyzer = new DependenciesAnalyzer();
        dependenciesAnalyzer.verifyDependencies(dependenciesParser.mainCluster());
        boolean initialDependenciesValidated = true;
        final long SCCVerificationTime = System.nanoTime() - startVerifyingSCC;
        long findingMissingDependenciesTime = 0L;

        if (!dependenciesAnalyzer.dependenciesValidated())
        {
            final long startFindingMissingDep = System.nanoTime();
            final DependenciesFinder dependenciesFinder = new DependenciesFinder(dependenciesAnalyzer.problematicClusters(), graph, loopFinder);
            dependenciesFinder.findOptimalDependencies();
            initialDependenciesValidated = false;
            Utils.unprocessClusters(dependenciesParser.mainCluster());
            findingMissingDependenciesTime = System.nanoTime() - startFindingMissingDep;
        }

        //Generate the abstract graph
        final long startGenerateMostOptimalGraph = System.nanoTime();
        final AbstractGraphsGenerator abstractGraphsGenerator = new AbstractGraphsGenerator(dependenciesParser.mainCluster());

        try
        {
            abstractGraphsGenerator.generateAbstractGraphs();
        }
        catch (BadDependencyException e)
        {
            throw new IllegalStateException("The dependencies validate the SCC constraints but are not sufficient" +
                    " to generate an abstract graph (see PartialOrders2AbstractGraph.java line 125 to 134)");
        }

        final long optimalGraphGenerationTime = System.nanoTime() - startGenerateMostOptimalGraph;

        //Generate the optimal BPMN process
        final long startGeneratingOptimalBPMN = System.nanoTime();
        final BPMNGenerator bpmnGenerator = new BPMNGenerator(dependenciesParser.mainCluster());
        final Graph optimalBPMN = bpmnGenerator.generate();
        final long optimalBPMNGenerationTime = System.nanoTime() - startGeneratingOptimalBPMN;

        //BPMN Graph -> List of BPMN objects
        final long startGraphToListOptimal = System.nanoTime();
        final GraphToList graphToList = new GraphToList(optimalBPMN);
        graphToList.convert();
        final long optimalBPMNGraphToListTime = System.nanoTime() - startGraphToListOptimal;

        //Analyze resources
        final long startOptimalBPMNResAnalysis = System.nanoTime();
        final Optimizer optimizer = new Optimizer(graphToList.objectsList(), optimalBPMN, globalResourceSet, (Integer) commandLineParser.get(CommandLineOption.IAT));
        final ResourcePool optimalPool = optimizer.computeOptimalPoolForNProcesses();
        final long optimalResAnalysisTime = System.nanoTime() - startOptimalBPMNResAnalysis;

        //If optimal pool > real pool, remove some tasks from the parallel gateways
        final Graph bestBPMN;
        long problematicTasksComputationTime = 0L;
        long problematicTasksBalancingTime = 0L;

        if (optimalPool.isNotIncludedIn((ResourcePool) commandLineParser.get(CommandLineOption.REAL_POOL)))
        {
            final long startProblematicTasksComputation = System.nanoTime();
            final ProblematicTasksFinder problematicTasksFinder = new ProblematicTasksFinder(
                    (ResourcePool) commandLineParser.get(CommandLineOption.REAL_POOL),
                    optimalPool,
                    optimalBPMN,
                    optimizer,
                    globalResourceSet,
                    (Integer) commandLineParser.get(CommandLineOption.IAT)
            );
            problematicTasksFinder.findProblematicTasks();
            problematicTasksComputationTime = System.nanoTime() - startProblematicTasksComputation;

            if (problematicTasksFinder.problematicTasks().isEmpty())
            {
                bestBPMN = optimalBPMN;
            }
            else
            {
                final long startTasksBalancing = System.nanoTime();
                Utils.unprocessClusters(dependenciesParser.mainCluster());
                final TasksBalancer tasksBalancer = new TasksBalancer(dependenciesParser.mainCluster(), problematicTasksFinder.problematicTasks());
                bestBPMN = tasksBalancer.balanceTasks();
                problematicTasksBalancingTime = System.nanoTime() - startTasksBalancing;
            }
        }
        else
        {
            bestBPMN = optimalBPMN;
        }

        //BPMN Graph -> List of BPMN objects
        final long startOptimalBPMNGraphToList = System.nanoTime();
        final GraphToList graphToList2 = new GraphToList(bestBPMN);
        parser.bpmnProcess().setObjects(new ArrayList<>(graphToList2.convert()));
        final long bestBPMNGraphToListTime = System.nanoTime() - startOptimalBPMNGraphToList;

        //Compute execution time of optimized process
        final long startComputingOptimalExecTime = System.nanoTime();
        final LightOptimizer lightOptimizer = new LightOptimizer(graphToList2.objectsList(), bestBPMN);
        final int optimalProcessExecutionTime = lightOptimizer.computeProcessExecutionTime();
        final int gain = 100 - (int) (((double) optimalProcessExecutionTime / (double) originalProcessExecutionTime) * 100);
        final long bestBPMNExecTimeComputeTime = System.nanoTime() - startComputingOptimalExecTime;

        final long startGraphicalGeneration = System.nanoTime();
        final GraphicalGenerationWriter graphicalGenerationWriter = new GraphicalGenerationWriter(
                commandLineParser,
                parser.bpmnHeader(),
                parser.bpmnProcess(),
                parser.bpmnCategories(),
                parser.documentation()
        );

        try
        {
            graphicalGenerationWriter.write();
        }
        catch (IOException e)
        {
            logger.error("Generation of BPMN file encountered an error:\n\n{}", e.toString());
            throw new RuntimeException(e);
        }
        final long graphicalGenerationTime = System.nanoTime() - startGraphicalGeneration;

        final long overallTime = parseArgsTime + parseBPMNTime + globalResComputationTime +
                initListToGraphTime + loopFinderTime + probaCorrectionTime + initExecTimeComputeTime +
                parsingDependenciesTime + SCCVerificationTime + findingMissingDependenciesTime +
                optimalGraphGenerationTime + optimalBPMNGenerationTime + optimalBPMNGraphToListTime +
                optimalResAnalysisTime + problematicTasksComputationTime + problematicTasksBalancingTime +
                bestBPMNGraphToListTime + bestBPMNExecTimeComputeTime + graphicalGenerationTime;

        String builder = "\n\nThe prototype took " +
                Utils.nanoSecToReadable(overallTime) +
                " to execute. This time is divided as follows:" +
                "\n     - Parsing command line arguments: " +
                Utils.nanoSecToReadable(parseArgsTime) +
                "\n     - Parsing BPMN original process: " +
                Utils.nanoSecToReadable(parseBPMNTime) +
                "\n     - Compute global resources: " +
                Utils.nanoSecToReadable(globalResComputationTime) +
                "\n     - Transform list to graph (initial process): " +
                Utils.nanoSecToReadable(initListToGraphTime) +
                "\n     - Compute process loops: " +
                Utils.nanoSecToReadable(loopFinderTime) +
                "\n     - Correct probabilities if needed: " +
                Utils.nanoSecToReadable(probaCorrectionTime) +
                "\n     - Compute initial process execution time: " +
                Utils.nanoSecToReadable(initExecTimeComputeTime) +
                "\n     - Parsing dependencies: " +
                Utils.nanoSecToReadable(parsingDependenciesTime) +
                "\n     - Verify SCC constraints: " +
                Utils.nanoSecToReadable(SCCVerificationTime) +
                "\n     - Finding missing dependencies (optional): " +
                Utils.nanoSecToReadable(findingMissingDependenciesTime) +
                "\n     - Generate optimal graph: " +
                Utils.nanoSecToReadable(optimalGraphGenerationTime) +
                "\n     - Generate optimal BPMN process: " +
                Utils.nanoSecToReadable(optimalBPMNGenerationTime) +
                "\n     - GraphToList optimal BPMN process: " +
                Utils.nanoSecToReadable(optimalBPMNGraphToListTime) +
                "\n     - Compute optimal pool of resources: " +
                Utils.nanoSecToReadable( optimalResAnalysisTime) +
                "\n     - Compute problematic tasks (optional): " +
                Utils.nanoSecToReadable(problematicTasksComputationTime) +
                "\n     - Balance problematic tasks (optional): " +
                Utils.nanoSecToReadable(problematicTasksBalancingTime) +
                "\n     - Parsing command line arguments: " +
                Utils.nanoSecToReadable(bestBPMNGraphToListTime) +
                "\n     - Compute best process execution time: " +
                Utils.nanoSecToReadable(bestBPMNExecTimeComputeTime) +
                "\n     - Generate .bpmn file: " +
                Utils.nanoSecToReadable(graphicalGenerationTime);

        System.out.println("\n\nThe pool of resources needed to execute the optimal version of the process without" +
                " latencies is: " + optimalPool);
        System.out.println(builder);
    }
}