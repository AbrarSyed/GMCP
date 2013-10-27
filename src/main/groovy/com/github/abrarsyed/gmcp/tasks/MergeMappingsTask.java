package com.github.abrarsyed.gmcp.tasks;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import com.github.abrarsyed.gmcp.Constants;
import com.github.abrarsyed.gmcp.GMCP;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ObjectArrays;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import lombok.Getter;
import lombok.Setter;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import com.google.code.regexp.Pattern;

public class MergeMappingsTask extends CachedTask
{
    public static final Pattern SIG_PATTERN = Pattern.compile("([\\[ZBCSIJFDV]|L([\\w\\\\/]+);)");
    public static final Pattern PACK_PATTERN = Pattern.compile("net\\\\minecraft\\\\src\\\\\\w+");
    public static final Pattern METHOD_SIG_PATTERN = Pattern.compile("^(?<className>[^\\.]+)\\.(?<methodName>[^\\(]+)(?<signature>.*)$");
    private HashMap<String, String> packages = new HashMap();

    @Getter
    @Setter
    @InputFile
    File packageCSV;

    @Getter
    @Setter
    @InputFile
    File inSRG;

    @Getter
    @Setter
    @InputFile
    File inEXC;

    @Getter
    @Setter
    @InputFile
    File inPatch;

    @Setter
    @OutputFile
    @Cached
    Object outPatch;

    @Setter
    @Cached
    @OutputFile
    Object outSRG;

    @Setter
    @Cached
    @OutputFile
    Object outEXC;

    @TaskAction
    public void doTask() throws IOException
    {
        // verify files
        outPatch = getProject().file(outPatch);
        outSRG = getProject().file(outSRG);
        outEXC = getProject().file(outEXC);

        // read SRG.
        // using this lib because SpecialSource needs it anyways.
        CSVReader reader = new CSVReader(new FileReader(packageCSV), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false);
        for (String[] line : reader.readAll())
        {
            packages.put(line[0], line[1]);
        }

        getLogger().info("Fixing the SRG");
        fixSRG(inSRG, (File) outSRG);

        getLogger().info("Fixing the EXC");
        fixExceptor(inEXC, (File) outEXC);

        getLogger().info("Fixing MCP patches");
        fixPatch(inPatch, (File) outPatch);
    }

    private void fixSRG(File inSRG, File outSRG) throws IOException
    {
        Files.touch(inSRG);

        StringBuilder outText = new StringBuilder();

        // start reading lines.
        BufferedReader reader = Files.newReader(inSRG, Charset.defaultCharset());

        // itterate through lines
        String line;
        String[] sections;
        while ((line = reader.readLine()) != null)
        {
            sections = line.split(" ");

            if (sections[0].equals("CL:"))
            {
                sections[2] = repackageClass(sections[2]);
            }
            else if (sections[0].equals("FD:"))
            {
                String[] split = rsplit(sections[2], "/");
                split[0] = repackageClass(split[0]);
                sections[2] = Joiner.on("/").join(split);
            }
            else if (sections[0].equals("MD:"))
            {
                String[] split = rsplit(sections[3], "/");
                split[0] = repackageClass(split[0]);
                sections[3] = Joiner.on("/").join(split);
                sections[4] = repackageSig(sections[4]);
            }

            line = Joiner.on(" ").join(sections);
            outText.append(line).append(Constants.NEWLINE);
        }
        getLogger().info("READ SRG");

        // don't forget to close.
        reader.close();

        // write the new text.
        Files.write(outText.toString(), outSRG, Charset.defaultCharset());
        getLogger().info("WROTE SRG");

    }

    // thanks shartte. This code is stolen directly from him/her
    private void fixExceptor(File inExc, File outExc) throws IOException
    {
        Files.touch(outExc);

        Properties mappings = new Properties();
        Properties mappingsOut = new Properties();

        // Try to load the mappings
        mappings.load(Files.newInputStreamSupplier(inExc).getInput());

        for (Map.Entry entry : mappings.entrySet())
        {
            Matcher matcher = METHOD_SIG_PATTERN.matcher((String) entry.getKey());

            if (!matcher.matches())
            {
                // There are some new fields in MCP for MC 1.6 that are not straight up method signatures
                mappingsOut.put(entry.getKey(), entry.getValue());
                continue;
            }

            String className = matcher.group("className");
            String methodName = matcher.group("methodName");
            String signature = matcher.group("signature");
            String[] exceptionsAndParams = ((String) entry.getValue()).split("\\|");

            String exceptions;
            if (exceptionsAndParams.length > 0 && !Strings.isNullOrEmpty(exceptions = exceptionsAndParams[0]))
            {
                String[] excs = exceptions.split(",");

                // repackage exceptions
                for (int i = 0; i < excs.length; i++)
                {
                    excs[i] = repackageClass(excs[i]);
                }
                exceptionsAndParams[0] = Joiner.on(',').join(excs);
            }
            // add an element to make sure there are 3
            while (exceptionsAndParams.length < 2)
            {
                exceptionsAndParams = ObjectArrays.concat(exceptionsAndParams, "");
            }

            signature = repackageSig(signature);
            className = repackageClass(className);

            String newKey = className + "." + methodName + signature;

            mappingsOut.setProperty(newKey, Joiner.on('|').join(exceptionsAndParams));
        }

        mappingsOut.store(Files.newOutputStreamSupplier(outExc).getOutput(), "");
    }

    private void fixPatch(File patch, File outPatch) throws IOException
    {
        String text = Files.readLines(patch, Charset.defaultCharset(), new PatchLineProcessor());
        Files.touch(outPatch);
        Files.write(text, outPatch, Charset.defaultCharset());
    }

    private String repackageClass(String input)
    {
        if (input.startsWith("net/minecraft/src"))
        {
            String className = input.substring(18);
            String pack = packages.get(className);
            if (!Strings.isNullOrEmpty(pack))
            {
                return pack + "/" + className;
            }
        }

        return input;
    }

    private String repackageSig(String sig)
    {
        String[] split = rsplit(sig, ")");
        String params = split[0];
        String ret = split[1];

        StringBuilder out = new StringBuilder("(");

        // add in changed parameters
        Matcher match = SIG_PATTERN.matcher(params);
        while (match.find())
        {
            if (match.group().length() > 1)
            {
                out.append('L').append(repackageClass(match.group(2))).append(';');
            }
            else
            {
                out.append(match.group());
            }
        }

        out.append(')');

        match = SIG_PATTERN.matcher(ret);
        while (match.find())
        {
            if (match.group().length() > 1)
            {
                out.append('L').append(repackageClass(match.group(2))).append(';');
            }
            else
            {
                out.append(match.group());
            }
        }

        return out.toString();
    }

    public static String[] rsplit(String input, String splitter)
    {
        int index = input.lastIndexOf(splitter);

        if (index == -1)
        {
            return new String[]{input};
        }

        String pieceOne = input.substring(0, index);
        String pieceTwo = input.substring(index + 1);
        return new String[]{pieceOne, pieceTwo};
    }

    public File getOutPatch()
    {
        if (outPatch instanceof File)
        {
            return (File) outPatch;
        }
        else
        {
            outPatch = getProject().file(outPatch);
            return (File) outPatch;
        }
    }

    public File getOutSRG()
    {
        if (outSRG instanceof File)
        {
            return (File) outSRG;
        }
        else
        {
            outSRG = getProject().file(outSRG);
            return (File) outSRG;
        }
    }

    public File getOutEXC()
    {
        if (outEXC instanceof File)
        {
            return (File) outEXC;
        }
        else
        {
            outEXC = getProject().file(outEXC);
            return (File) outEXC;
        }
    }

    private class PatchLineProcessor implements LineProcessor<String>
    {
        StringBuilder builder = new StringBuilder();
        private final char DELIM = GMCP.os == Constants.OperatingSystem.WINDOWS ? '\\' : '/';

        @Override
        public boolean processLine(String s) throws IOException
        {
            if (s.startsWith("+++") || s.startsWith("---") || s.startsWith("diff"))
            {
                GMCP.project.getLogger().info("OLD  "+s);

                s = s.replaceAll("minecraft\\\\(net\\\\minecraft)", "$1");

                Matcher match = PACK_PATTERN.matcher(s);
                String clazz;
                if (match.find())
                {
                    clazz = repackageClass(match.group().replace("\\", "/")).replace("/", "\\");
                    s = s.replace(match.group(), clazz);
                }

                s = s.replaceAll("[\\\\]", "/");

                GMCP.project.getLogger().info("NEW  "+s);
            }

            builder.append(s).append(Constants.NEWLINE);

            return true;
        }

        @Override
        public String getResult()
        {
            // remove the last newline before making it a string
            return builder.substring(0, builder.length() - Constants.NEWLINE.length()).toString();
        }
    }
}
