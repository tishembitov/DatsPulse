package ru.tishembitov.model;

public class AntMoveCommand {
    public String ant;
    public Hex[] path;
    public AntMoveCommand(String ant, Hex[] path) {
        this.ant = ant;
        this.path = path;
    }
} 