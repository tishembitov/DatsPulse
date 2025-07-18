package ru.tishembitov.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Enemy {
    public String id;
    public int type; // 0 - Worker, 1 - Soldier, 2 - Scout
    public int q;
    public int r;
    public int health;
    public Food food;
    public int attack;
} 