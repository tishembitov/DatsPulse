package ru.tishembitov.model;

import java.util.List;

public class PlayerResponse {
    public List<Ant> ants;
    public List<Enemy> enemies;
    public List<FoodOnMap> food;
    public List<Hex> home;
    public List<Tile> map;
    public double nextTurnIn;
    public int score;
    public Hex spot;
    public int turnNo;
} 