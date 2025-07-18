package ru.tishembitov.model;

public class Hex {
    public int q;
    public int r;

    public Hex() {}
    public Hex(int q, int r) {
        this.q = q;
        this.r = r;
    }
    @Override
    public String toString() {
        return "(" + q + "," + r + ")";
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hex hex = (Hex) o;
        return q == hex.q && r == hex.r;
    }
    @Override
    public int hashCode() {
        return 31 * q + r;
    }
} 