package ru.tishembitov.bot;

import ru.tishembitov.model.Hex;
import java.util.*;

public class BotMemory {
    // Координаты, где часто гибнут юниты
    private final Map<String, Integer> dangerZones = new HashMap<>();
    // Координаты, где были замечены враги
    private final Set<String> enemySpots = new HashSet<>();
    // Координаты, где были ресурсы
    private final Set<String> resourceSpots = new HashSet<>();
    // Счётчик смертей
    private int deaths = 0;
    // Счётчик успешных сдач ресурсов
    private int successfulReturns = 0;
    // Ручной выбор ресурсов пользователем
    private final Set<Hex> manualResourceTargets = new HashSet<>();

    public void markDeath(Hex hex) {
        String key = hex.q + "," + hex.r;
        dangerZones.put(key, dangerZones.getOrDefault(key, 0) + 1);
        deaths++;
    }
    public void markEnemy(Hex hex) {
        enemySpots.add(hex.q + "," + hex.r);
    }
    public void markResource(Hex hex) {
        resourceSpots.add(hex.q + "," + hex.r);
    }
    public void markReturn() { successfulReturns++; }

    public boolean isDangerous(Hex hex) {
        return dangerZones.getOrDefault(hex.q + "," + hex.r, 0) > 1;
    }
    public boolean isEnemySpot(Hex hex) {
        return enemySpots.contains(hex.q + "," + hex.r);
    }
    public boolean isResourceSpot(Hex hex) {
        return resourceSpots.contains(hex.q + "," + hex.r);
    }
    public int getDeaths() { return deaths; }
    public int getSuccessfulReturns() { return successfulReturns; }
    public Set<String> getDangerZones() { return dangerZones.keySet(); }

    public void addManualResourceTarget(Hex hex) {
        manualResourceTargets.add(hex);
    }
    public void removeManualResourceTarget(Hex hex) {
        manualResourceTargets.remove(hex);
    }
    public boolean isManualResourceTarget(Hex hex) {
        return manualResourceTargets.contains(hex);
    }
    public Set<Hex> getManualResourceTargets() {
        return manualResourceTargets;
    }
} 