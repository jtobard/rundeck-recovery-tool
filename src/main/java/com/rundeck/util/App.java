package com.rundeck.util;

import org.apache.commons.cli.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class App {

    private static String project = "test";
    private static String appVersion = "3.0.11";
    private static String path = "input";
    private static String outpath;
    private static boolean includeExec = false;

    public static void main(String args[]) throws IOException{
        System.out.println("Starting recovery");

        Options options = new Options();

        Option input = new Option("i", "input", true, "folder that contains the execution xml files.");
        input.setRequired(true);
        options.addOption(input);

        Option project = new Option("p", "project", true, "(optional) project name, default, the same name as the input folder.");
        project.setRequired(false);
        options.addOption(project);

        Option output = new Option("o", "output", true, "(optional) output location folder, default: same folder.");
        output.setRequired(false);
        options.addOption(output);

        Option executions = new Option("e", "(optional) Include executions. It may cause a substantial  increase in load time when loading the project.");
        executions.setRequired(false);
        options.addOption(executions);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);

            String inputPath = cmd.getOptionValue("input");
            String inputProject;
            if(cmd.hasOption("project")){
                inputProject = cmd.getOptionValue("project");
            }else{
                inputProject = inputPath;
            }
            if(cmd.hasOption("output")){
                outpath = cmd.getOptionValue("output");
            }
            if(cmd.hasOption("e")){
                includeExec = true;
            }

            run(inputProject, inputPath);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("rundeck-recovery-tool", options);

            System.exit(1);
        }
    }

    public static void run(String inputProject, String inputPath) throws IOException
    {
        project=inputProject;
        path = inputPath;
        Path tempDir = createTemp();
        if(tempDir == null){
            return;
        }
        Path root = tempDir.resolve("rundeck-" + project);

        String outJar = "export-"+project+".jar";
        if(outpath!= null){
            File outdir = new File(outpath);
            if(outdir.exists() && outdir.isDirectory() && outdir.canWrite()){
                outJar=outpath+outJar;
            }
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Rundeck-Application-Version", appVersion);
        manifest.getMainAttributes().putValue("Rundeck-Archive-Format-Version", "1.0");
        manifest.getMainAttributes().putValue("Rundeck-Archive-Project-Name", project);
        manifest.getMainAttributes().putValue("Rundeck-Archive-Export-Date", getDate());



        JarOutputStream target = new JarOutputStream(new FileOutputStream(outJar), manifest);
        add(tempDir.toFile().getPath(), root.toFile(), target);
        target.close();
    }

    //create file structure
    private static Path createTemp() {
        try {
            Path tempDirWithPrefix = Files.createTempDirectory(null);
            Path root = tempDirWithPrefix.resolve("rundeck-" + project);
            if (!root.toFile().exists()) {
                root.toFile().mkdir();
                Path jobs = root.resolve("jobs");
                Path executions = root.resolve("executions");
                if (!jobs.toFile().exists()) {
                    jobs.toFile().mkdir();
                }
                if(includeExec){
                    if (!executions.toFile().exists()) {
                        executions.toFile().mkdir();
                    }
                    //copy each execution
                    copyExecutions(executions);
                }
                //create job files
                extractJobs(jobs);
            }
            return tempDirWithPrefix;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void extractJobs(Path destination){
        Path org = Paths.get(path);
        File[] listOfFiles = org.toFile().listFiles();
        Arrays.sort(listOfFiles);
        for (File file : listOfFiles) {
            if (file.isFile() && file.getName().endsWith("xml")) {
                extractJob(file, destination);
            }
        }
    }

    private static void extractJob(File exec,Path destination){
        try {
            debug("Extractiong job from "+exec.getName());
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(exec);
            NodeList fullJob = doc.getElementsByTagName("fullJob");
            if (fullJob != null && fullJob.item(0)!=null) {
                String uuid = "";
                NodeList nodes = fullJob.item(0).getChildNodes();

                Document newXmlDocument = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder().newDocument();
                Element joblist = newXmlDocument.createElement("joblist");
                newXmlDocument.appendChild(joblist);
                Element job = newXmlDocument.createElement("job");
                joblist.appendChild(job);

                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    if (node.getNodeName().equals("uuid")) {
                        uuid = node.getFirstChild().getNodeValue();
                    }
                    Node copyNode = newXmlDocument.importNode(node, true);
                    job.appendChild(copyNode);
                }
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                Result output = new StreamResult(destination.resolve("job-" + uuid + ".xml").toFile());
                Source input = new DOMSource(newXmlDocument);

                transformer.transform(input, output);
            }
        }catch (TransformerException | SAXException| ParserConfigurationException | IOException e){
            debug("An error has ocurred extracting job from "+exec.getName());
            e.printStackTrace();
        }

    }

    private static void copyExecutions(Path dest) throws IOException{
        Path org = Paths.get(path);
        copyFolder(org,dest);

    }

    private static  void copyFolder(Path src, Path dest) throws IOException {
        Files.walk(src)
                .forEach(source -> copy(source, dest.resolve(src.relativize(source))));
    }

    private static void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static String getDate(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }
    private static void add(String temp, File source, JarOutputStream target) throws IOException {
        BufferedInputStream in = null;
        try {
            if (source.isDirectory()) {
                String name = source.getPath().replace("\\", "/");
                name = name.substring(temp.length()+1,name.length());
                if (!name.isEmpty()) {
                    if (!name.endsWith("/"))
                        name += "/";
                    JarEntry entry = new JarEntry(name);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile : source.listFiles())
                    add(temp,nestedFile, target);
                return;
            }

            String name = source.getPath().replace("\\", "/");
            name = name.substring(temp.length()+1,name.length());
            JarEntry entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true) {
                int count = in.read(buffer);
                if (count == -1)
                    break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();
        } finally {
            if (in != null)
                in.close();
        }
    }





    static void debug(Object o){
        System.out.println(o);
    }
}