# Recovery Tool

Rundeck recovery tool to extract info from the jobs from logstorage.

This is an emergency tool to retrieve information about project's jobs in case something catastrophically fails.
It is not infallible.
If a job has not been executed directly, it will not be loaded.
If a job has been modified and has not been run, an old version will be loaded.

However, it is a way to recover much of the information.

## Usage

Usage: `java -jar rundeck-recovery-tool-1.0.jar`

 `-i,--input <arg>`     folder that contains the execution xml files

 `-e`                   (optional) Include executions. It may cause a substantial  increase in load time when restoring the project.
 `-o,--output <arg>`    (optional) output location folder, default: same folder.
 `-p,--project <arg>`   (optional) project name, default, the same name as the input folder.

