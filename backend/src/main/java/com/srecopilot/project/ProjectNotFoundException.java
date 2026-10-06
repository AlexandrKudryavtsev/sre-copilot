package com.srecopilot.project;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(long id) {
        super("Project '%d' was not found.".formatted(id));
    }
}
