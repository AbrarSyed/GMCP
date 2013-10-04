package com.github.abrarsyed.gmcp.tasks

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util
import com.google.common.io.Files
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ApplyFernflowerTask extends DefaultTask
{

    @InputFile
    def File fernflower

    @InputFile
    def File input

    @OutputFile
    def File output

    @TaskAction
    void doTask()
    {
        def outJar = new File(output.getParentFile(), input.getName());

        project.javaexec {
            args(
                    fernflower.getAbsolutePath(),
                    "-din=0",
                    "-rbr=0",
                    "-dgs=1",
                    "-asc=1",
                    "-log=ERROR",
                    input.getAbsolutePath(),
                    temporaryDir
            );

            setMain "-jar"
            setWorkingDir fernflower.getParentFile()

            classpath Util.getClassPath()

            setStandardOutput Util.getNullStream()
        }

        // rename it to the correct file.
        Files.move(new File(temporaryDir, input.name), output)
    }
}
