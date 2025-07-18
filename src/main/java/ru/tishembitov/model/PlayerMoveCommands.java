package ru.tishembitov.model;

import java.util.List;

public class PlayerMoveCommands {
    public List<AntMoveCommand> moves;
    public PlayerMoveCommands(List<AntMoveCommand> moves) {
        this.moves = moves;
    }
} 