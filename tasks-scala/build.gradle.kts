
tasks.register("assemble", Exec::class) {
    workingDir(project.projectDir)

    commandLine("./sbt", "package")
}

tasks.register("executeShellCommand", Exec::class) {
    // Set the command to execute. Example: "echo", "Hello, World!"
    commandLine("echo", "Hello, World!")
    commandLine("echo", project.projectDir)

    // Optionally, set the working directory
    workingDir(project.rootDir)

    // Logging the output
    standardOutput = System.out
}
