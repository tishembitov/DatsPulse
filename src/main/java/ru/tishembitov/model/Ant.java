package ru.tishembitov.model;

public class Ant {
    public String id;
    public int type; // 0 - Worker, 1 - Soldier, 2 - Scout
    public int q;
    public int r;
    public int health;
    public Food food;
    public Hex lastAttack;
    public String lastEnemyAnt;
    public Hex[] lastMove;
    public Hex[] move;
} 