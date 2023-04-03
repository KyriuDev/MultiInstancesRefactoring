package main;

import other.Utils;
import constants.CommandLineOption;
import resources.ResourcePool;
import resources.ResourcePoolParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommandLineParser
{
    private final Map<CommandLineOption, Object> commands;

    public CommandLineParser(final String[] args) throws FileNotFoundException
    {
        this.commands = new HashMap<>();
        this.parse(args);

        if (this.commands.containsKey(CommandLineOption.HELP))
        {
            System.out.println(this.helpMessage());
            this.commands.clear();
        }

        this.retrieveArgsFromWorkingDirectory();

        if (!this.verifyArgs())
        {
            throw new IllegalStateException("Necessary arguments are missing. Please make sure you have specified the" +
                    "following elements: IAT, BPMN process, resource pool, or that the working directory that you have" +
                    "specified contains all of these elements.");
        }
    }

    public Object get(CommandLineOption commandLineOption)
    {
        return this.commands.get(commandLineOption);
    }

    //Private methods

    private String helpMessage()
    {
        //TODO
        return "";
    }

    private void parse(String[] commandLineArgs) throws FileNotFoundException
    {
        for (String arg : commandLineArgs)
        {
            if (this.isIAT(arg))
            {
                this.commands.put(CommandLineOption.IAT, Integer.parseInt(arg));
            }
            else if (this.isResourcePool(arg))
            {
                this.commands.put(CommandLineOption.REAL_POOL, ResourcePoolParser.parse(new File(arg)));
            }
            else if (this.isBpmnProcess(arg))
            {
                this.commands.put(CommandLineOption.BPMN_PROCESS, new File(arg));
            }
            else if (this.isWorkingDirectory(arg))
            {
                this.commands.put(CommandLineOption.WORKING_DIRECTORY, new File(arg));
            }
            else if (this.isOverwrite(arg))
            {
                this.commands.put(CommandLineOption.OVERWRITE, true);
            }
            else if (this.isDependencies(arg))
            {
                this.commands.put(CommandLineOption.DEPENDENCIES, new File(arg));
            }
            else
            {
                throw new IllegalStateException("Unrecognized argument |" + arg + "|.");
            }
        }
    }

    private boolean isIAT(final String arg)
    {
        return Utils.isAnInt(arg);
    }

    private boolean isResourcePool(final String arg)
    {
        return (arg.endsWith(".rsp") || arg.endsWith(".rp"))
                && new File(arg).isFile();
    }

    private boolean isBpmnProcess(final String arg)
    {
        return arg.endsWith(".bpmn")
                && new File(arg).isFile();
    }

    private boolean isWorkingDirectory(final String arg)
    {
        return new File(arg).isDirectory();
    }

    private boolean isDependencies(final String arg)
    {
        return arg.endsWith(".dep")
                && new File(arg).isFile();
    }

    private boolean isOverwrite(final String arg)
    {
        final String lowerArg = arg.toLowerCase();

        return lowerArg.equals("-f")
                || lowerArg.equals("--f")
                || lowerArg.equals("-overwrite")
                || lowerArg.equals("--overwrite");
    }

    private void retrieveArgsFromWorkingDirectory() throws FileNotFoundException
    {
        if (this.commands.get(CommandLineOption.IAT) == null)
        {
            return;
        }

        if (this.commands.get(CommandLineOption.WORKING_DIRECTORY) == null)
        {
            //No working directory was specified --> compute one based on the location of the BPMN process
            if (this.commands.get(CommandLineOption.BPMN_PROCESS) == null
                || this.commands.get(CommandLineOption.REAL_POOL) == null
                || this.commands.get(CommandLineOption.DEPENDENCIES) == null)
            {
                return;
            }

            final File bpmnProcess = (File) this.commands.get(CommandLineOption.BPMN_PROCESS);
            final File workingDirectory = bpmnProcess.getParentFile().isDirectory() ? bpmnProcess.getParentFile() : null;
            this.commands.put(CommandLineOption.WORKING_DIRECTORY, workingDirectory);
        }
        else
        {
            //Working directory was specified --> check whether it contains a BPMN process and/or a resource file
            final File workingDirectory = (File) this.commands.get(CommandLineOption.WORKING_DIRECTORY);

            for (File file : Objects.requireNonNull(workingDirectory.listFiles()))
            {
                if (this.isBpmnProcess(file.getPath()))
                {
                    if (this.commands.containsKey(CommandLineOption.BPMN_PROCESS))
                    {
                        System.out.println("Warning: BPMN Process |" +
                                ((File) this.commands.get(CommandLineOption.BPMN_PROCESS)).getPath() + "| will be" +
                                " overwritten by BPMN process |" + file.getPath() + "|.");
                    }

                    this.commands.put(CommandLineOption.BPMN_PROCESS, file);
                }
                else if (this.isResourcePool(file.getPath()))
                {
                    final ResourcePool resourcePool = ResourcePoolParser.parse(file);

                    if (this.commands.containsKey(CommandLineOption.REAL_POOL))
                    {
                        System.out.println("Warning: Resource pool |" +
                                this.commands.get(CommandLineOption.REAL_POOL).toString() + "| will be" +
                                " overwritten by Resource pool |" + resourcePool + "|.");
                    }

                    this.commands.put(CommandLineOption.REAL_POOL, resourcePool);
                }
                else if (this.isDependencies(file.getPath()))
                {
                    if (this.commands.containsKey(CommandLineOption.DEPENDENCIES))
                    {
                        System.out.println("Warning: Dependencies |" +
                                ((File) this.commands.get(CommandLineOption.DEPENDENCIES)).getPath() + "| will be" +
                                " overwritten by dependencies |" + file.getPath() + "|.");
                    }

                    this.commands.put(CommandLineOption.DEPENDENCIES, file);
                }
            }
        }
    }

    private boolean verifyArgs()
    {
        return this.commands.get(CommandLineOption.IAT) != null
                && this.commands.get(CommandLineOption.BPMN_PROCESS) != null
                && this.commands.get(CommandLineOption.REAL_POOL) != null
                && this.commands.get(CommandLineOption.WORKING_DIRECTORY) != null
                && this.commands.get(CommandLineOption.DEPENDENCIES) != null;
    }
}
