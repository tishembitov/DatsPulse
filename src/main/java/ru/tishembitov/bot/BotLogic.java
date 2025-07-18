package ru.tishembitov.bot;

import ru.tishembitov.api.ApiClient;;
import java.util.*;

import ru.tishembitov.model.*;
import ru.tishembitov.visual.SwingVisualizer;

// --- BotLogic.java---
// Ключевые стратегии:
// - Приоритет нектара > хлеб > яблоко
// - Каждый рабочий идёт к своему ресурсу (без толпы)
// - Избегание опасных зон (BotMemory)
// - Групповая сдача ресурсов
// - Динамическое распределение ролей
// - Разведка: новые клетки, ресурсы, вражеские муравейники
// - Фиксация цели (ресурс/разведка)
// - Патрулирование, если нет ресурсов
// - Бойцы атакуют только при поддержке или у муравейника
// - Задел для “цепочек” передачи ресурсов

public class BotLogic {
    private final ApiClient api;
    private final Random random = new Random();
    private static final int MAX_PATH_LENGTH = 20;
    private final BotMemory memory = new BotMemory();
    private final java.util.List<String> botLog = new java.util.ArrayList<>();
    private boolean swingStarted = false;
    private Map<String, String> resourceAssignment = null;
    private Map<String, Integer> deliveryWaits = new HashMap<>();
    private Map<String, Hex> antTargets = new HashMap<>();
    private Map<String, Integer> antStuck = new HashMap<>(); // id -> сколько ходов не двигается к цели
    private Map<String, Integer> patrolTimer = new HashMap<>(); // id -> сколько ходов патрулирует

    public BotLogic(ApiClient api) { this.api = api; }

    public void play() throws Exception {
        api.register();
        if (!swingStarted) {
            swingStarted = true;
            SwingVisualizer.launchVisualizer();
            Thread.sleep(1000);
        }
        while (true) {
            analyzeLogs();
            PlayerResponse state = api.getArena();
            if (SwingVisualizer.instance != null) {
                SwingVisualizer.instance.setData(state, memory, state.turnNo, state.score, 99, "", new java.util.ArrayList<>(botLog));
            }
            Map<String, Hex> reserved = new HashMap<>();
            List<AntMoveCommand> moves = new ArrayList<>();
            int total = state.ants.size();
            int workers = (int) state.ants.stream().filter(a -> a.type == 0).count();
            int scouts = (int) state.ants.stream().filter(a -> a.type == 2).count();
            int soldiers = (int) state.ants.stream().filter(a -> a.type == 1).count();
            int minWorkers = Math.max(1, (int)Math.ceil(total * 0.4));
            int minScouts = Math.max(2, (int)Math.ceil(total * 0.3));
            int assignedScouts = 0, assignedWorkers = 0;
            for (Ant ant : state.ants) {
                AntMoveCommand cmd = null;
                if (ant.type == 0 && assignedWorkers < minWorkers) {
                    cmd = workerLogic(ant, state, reserved);
                    assignedWorkers++;
                } else if (ant.type == 2 && assignedScouts < minScouts) {
                    cmd = scoutLogic(ant, state, reserved);
                    assignedScouts++;
                } else if (ant.type == 1 && assignedScouts < minScouts) {
                    cmd = scoutLogic(ant, state, reserved);
                    assignedScouts++;
                } else if (ant.type == 0) {
                    cmd = workerLogic(ant, state, reserved);
                } else if (ant.type == 2) {
                    cmd = scoutLogic(ant, state, reserved);
                } else if (ant.type == 1) {
                    cmd = soldierLogic(ant, state, reserved);
                }
                if (cmd != null) {
                    if (cmd.path.length > 0) reserved.put(ant.id, cmd.path[cmd.path.length-1]);
                    for (Ant a : state.ants) if (a.id.equals(ant.id)) { a.move = cmd.path; break; }
                    moves.add(cmd);
                }
            }
            api.sendMoves(new PlayerMoveCommands(moves));
            Thread.sleep(Math.max(500, (int)(state.nextTurnIn * 1000)));
        }
    }

    // --- Worker logic: приоритет нектара, фиксация цели, избегание толпы и опасностей, групповая сдача ---
    private AntMoveCommand workerLogic(Ant ant, PlayerResponse state, Map<String, Hex> reserved) {
        // --- Если есть ручные цели, игнорируем все остальные ресурсы ---
        Set<Hex> manualTargets = memory.getManualResourceTargets();
        if (!manualTargets.isEmpty()) {
            // Фиксация цели
            Hex target = antTargets.get(ant.id);
            boolean targetValid = false;
            if (target != null) {
                for (FoodOnMap food : state.food) {
                    if (food.q == target.q && food.r == target.r && manualTargets.contains(new Hex(food.q, food.r))) {
                        targetValid = true; break;
                    }
                }
            }
            if (!targetValid) {
                // Назначаем ближайшую ручную цель
                int minDist = Integer.MAX_VALUE;
                Hex best = null;
                for (Hex hex : manualTargets) {
                    for (FoodOnMap food : state.food) {
                        if (food.q == hex.q && food.r == hex.r) {
                            int dist = hexDist(ant.q, ant.r, hex.q, hex.r);
                            if (dist < minDist) { minDist = dist; best = hex; }
                        }
                    }
                }
                if (best != null) {
                    target = best;
                    antTargets.put(ant.id, target);
                } else {
                    antTargets.remove(ant.id); target = null;
                }
            }
            // Если цель есть — идём к ней
            if (target != null) {
                memory.markResource(target);
                List<Hex> path = aStarAvoidingMemory(ant, target, state, reserved, false);
                botLog.add("[Ручной сбор] Муравей " + ant.id + " идёт к отмеченному ресурсу (" + target.q + "," + target.r + ")");
                return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
            }
            // Если нет подходящих ручных целей — стоим
            return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
        }
        if (ant.food != null && ant.food.amount > 0) {
            Hex home = nearestHex(ant.q, ant.r, state.home);
            int distToHome = hexDist(ant.q, ant.r, home.q, home.r);
            boolean atHome = (distToHome == 0);
            boolean groupReady = false; int groupCount = 0;
            for (Ant other : state.ants) {
                if (!other.id.equals(ant.id) && other.type == 0 && other.food != null && other.food.amount > 0) {
                    int d = hexDist(other.q, other.r, home.q, home.r);
                    if (d <= 1) groupCount++;
                }
            }
            if (groupCount > 0) groupReady = true;
            int wait = deliveryWaits.getOrDefault(ant.id, 0);
            if (atHome || groupReady || wait > 0) {
                deliveryWaits.remove(ant.id);
                antTargets.remove(ant.id); // сбрасываем цель после сдачи
                List<Hex> path = aStarAvoidingMemory(ant, home, state, reserved, false);
                if (!path.isEmpty() && path.get(path.size()-1).q == home.q && path.get(path.size()-1).r == home.r) {
                    memory.markReturn();
                    botLog.add("[Сдача] Муравей " + ant.id + " сдал ресурсы в муравейник (группой: " + (groupCount+1) + ")");
                }
                return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
            } else {
                deliveryWaits.put(ant.id, wait+1);
                botLog.add("[Ожидание] Муравей " + ant.id + " ждёт группу для сдачи у муравейника");
                return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
            }
        } else {
            deliveryWaits.remove(ant.id);
        }
        // Если муравей стоит на клетке с food, но не может подобрать — отправлять команду на ту же клетку
        for (FoodOnMap food : state.food) {
            if (ant.q == food.q && ant.r == food.r) {
                botLog.add("[Ожидание] Муравей " + ant.id + " стоит на ресурсе и ждёт подбор");
                return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
            }
        }
        // --- Фиксация цели ---
        Hex target = antTargets.get(ant.id);
        boolean targetValid = false;
        if (target != null) {
            for (FoodOnMap food : state.food) {
                if (food.q == target.q && food.r == target.r) { targetValid = true; break; }
            }
        }
        if (!targetValid) {
            int bestScore = -1, minDist = Integer.MAX_VALUE; FoodOnMap best = null;
            Set<String> reservedResources = new HashSet<>(resourceAssignment != null ? resourceAssignment.values() : Collections.emptySet());
            for (FoodOnMap food : state.food) {
                int score = food.type == 3 ? 1000 : (food.type == 2 ? 100 : 10);
                int dist = hexDist(ant.q, ant.r, food.q, food.r);
                String key = food.q + "," + food.r;
                if ((score > bestScore || (score == bestScore && dist < minDist)) && !reservedResources.contains(key) && !memory.isDangerous(new Hex(food.q, food.r))) {
                    best = food; bestScore = score; minDist = dist;
                }
            }
            if (best != null) {
                target = new Hex(best.q, best.r);
                antTargets.put(ant.id, target);
            } else {
                antTargets.remove(ant.id); target = null;
            }
        }
        Hex prevTarget = antTargets.get(ant.id);
        if (prevTarget != null && target != null && prevTarget.equals(target)) {
            int stuck = antStuck.getOrDefault(ant.id, 0);
            if (stuck >= 3) { antTargets.remove(ant.id); antStuck.put(ant.id, 0); target = null; botLog.add("[Сброс цели] Муравей " + ant.id + " застрял, ищет новую цель"); }
            else antStuck.put(ant.id, stuck+1);
        } else {
            antStuck.put(ant.id, 0);
        }
        // Если не найдено ресурса — патрулируем, каждые 5 ходов меняем точку
        if (target == null) {
            int pt = patrolTimer.getOrDefault(ant.id, 0);
            if (pt % 5 == 0) {
                Hex fallback = findSafeExploredHex(ant, state);
                if (fallback != null && (ant.q != fallback.q || ant.r != fallback.r)) {
                    patrolTimer.put(ant.id, pt+1);
                    List<Hex> path = aStarAvoidingMemory(ant, fallback, state, reserved, false);
                    botLog.add("[Патруль] Рабочий " + ant.id + " патрулирует (" + fallback.q + "," + fallback.r + ")");
                    return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
                }
            }
            patrolTimer.put(ant.id, pt+1);
            return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
        }
        if (target != null) {
            memory.markResource(target);
            List<Hex> path = aStarAvoidingMemory(ant, target, state, reserved, false);
            botLog.add("[Сбор] Муравей " + ant.id + " идёт к ресурсу (" + target.q + "," + target.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        // fallback — патрулируем безопасную область
        Hex fallback = findSafeExploredHex(ant, state);
        if (fallback != null && (ant.q != fallback.q || ant.r != fallback.r)) {
            List<Hex> path = aStarAvoidingMemory(ant, fallback, state, reserved, false);
            botLog.add("[Патруль] Рабочий " + ant.id + " идёт в безопасную область (" + fallback.q + "," + fallback.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
    }

    // --- Scout logic: фиксация цели, разведка, ресурсы, муравейники, патруль ---
    private AntMoveCommand scoutLogic(Ant ant, PlayerResponse state, Map<String, Hex> reserved) {
        Hex target = antTargets.get(ant.id);
        boolean targetValid = false;
        Set<String> visible = state.map.stream().map(t -> t.q+","+t.r).collect(java.util.stream.Collectors.toSet());
        if (target != null) {
            String key = target.q+","+target.r;
            if (!visible.contains(key)) targetValid = true;
        }
        if (!targetValid) {
            // 1. Ищем ближайший неразведанный гекс
            int minDist = Integer.MAX_VALUE; Hex best = null;
            for (int dq = -8; dq <= 8; dq++) for (int dr = -8; dr <= 8; dr++) {
                int nq = ant.q + dq, nr = ant.r + dr;
                String key = nq+","+nr;
                if (!visible.contains(key) && !memory.isDangerous(new Hex(nq, nr))) {
                    int dist = hexDist(ant.q, ant.r, nq, nr);
                    if (dist < minDist) { best = new Hex(nq, nr); minDist = dist; }
                }
            }
            if (best != null) { target = best; antTargets.put(ant.id, target); }
            else antTargets.remove(ant.id);
        }
        if (target != null) {
            List<Hex> path = aStarAvoidingMemory(ant, target, state, reserved, false);
            botLog.add("[Разведка] Разведчик " + ant.id + " исследует (" + target.q + "," + target.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        // 2. Если есть ресурсы — помогать собирать
        if (!state.food.isEmpty()) return workerLogic(ant, state, reserved);
        // 3. Ищем ближайший чужой муравейник
        Hex enemyHome = findEnemyHome(state, state.home);
        if (enemyHome != null && (ant.q != enemyHome.q || ant.r != enemyHome.r)) {
            List<Hex> path = aStarAvoidingMemory(ant, enemyHome, state, reserved, false);
            botLog.add("[Разведка] Разведчик " + ant.id + " идёт к чужому муравейнику (" + enemyHome.q + "," + enemyHome.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        // 4. fallback — патрулируем безопасную область
        Hex fallback = findSafeExploredHex(ant, state);
        if (fallback != null && (ant.q != fallback.q || ant.r != fallback.r)) {
            List<Hex> path = aStarAvoidingMemory(ant, fallback, state, reserved, false);
            botLog.add("[Патруль] Разведчик " + ant.id + " патрулирует (" + fallback.q + "," + fallback.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        // В начале класса:
        // В scoutLogic: часть ходов (например, если ant.id.hashCode()%5==turn%5) — случайный соседний гекс
        if (random.nextInt(5) == 0) {
            int[][] dirs = directions();
            int[] d = dirs[random.nextInt(dirs.length)];
            int nq = ant.q + d[0], nr = ant.r + d[1];
            Tile tile = state.map.stream().filter(t -> t.q == nq && t.r == nr).findFirst().orElse(null);
            if (tile != null && tile.type != 5 && tile.type != 4) {
                botLog.add("[Рандом] Разведчик " + ant.id + " идёт случайно на (" + nq + "," + nr + ")");
                return new AntMoveCommand(ant.id, new Hex[]{new Hex(nq, nr)});
            }
        }
        return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
    }

    // --- Soldier logic: атакует только при поддержке или у муравейника, иначе патрулирует ---
    private AntMoveCommand soldierLogic(Ant ant, PlayerResponse state, Map<String, Hex> reserved) {
        Enemy target = null; int minDist = Integer.MAX_VALUE;
        for (Enemy e : state.enemies) {
            boolean nearHome = isNearHome(e.q, e.r, state.home);
            boolean valuable = (e.food != null && e.food.amount > 0);
            boolean weak = (e.health < 70);
            int dist = hexDist(ant.q, ant.r, e.q, e.r);
            boolean support = isAllyNear(e.q, e.r, state.ants, ant.id, 1);
            if ((valuable || weak || nearHome) && dist < minDist && (support || nearHome) && !memory.isDangerous(new Hex(e.q, e.r))) {
                minDist = dist; target = e;
            }
        }
        if (target != null && minDist <= 6) {
            memory.markEnemy(new Hex(target.q, target.r));
            List<Hex> path = aStarAvoidingMemory(ant, new Hex(target.q, target.r), state, reserved, true);
            botLog.add("[Атака] Боец " + ant.id + " атакует врага (" + target.q + "," + target.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        // fallback — патрулируем вокруг муравейника
        Hex patrol = nearestHex(ant.q, ant.r, state.home);
        if (patrol != null && (ant.q != patrol.q || ant.r != patrol.r)) {
            List<Hex> path = aStarAvoidingMemory(ant, patrol, state, reserved, false);
            botLog.add("[Патруль] Боец " + ant.id + " патрулирует (" + patrol.q + "," + patrol.r + ")");
            return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
        }
        // Если рядом рабочий идёт к опасному ресурсу — сопровождать его
        for (Ant w : state.ants) {
            if (w.type == 0 && ant.id != w.id && antTargets.containsKey(w.id)) {
                Hex t = antTargets.get(w.id);
                if (memory.isDangerous(t) && hexDist(ant.q, ant.r, w.q, w.r) <= 2) {
                    List<Hex> path = aStarAvoidingMemory(ant, t, state, reserved, false);
                    botLog.add("[Сопровождение] Боец " + ant.id + " сопровождает рабочего " + w.id + " к опасному ресурсу");
                    return new AntMoveCommand(ant.id, path.toArray(new Hex[0]));
                }
            }
        }
        return new AntMoveCommand(ant.id, new Hex[]{new Hex(ant.q, ant.r)});
    }

    // --- A* с учётом памяти ---
    private List<Hex> aStarAvoidingMemory(Ant ant, Hex target, PlayerResponse state, Map<String, Hex> reserved, boolean avoidEnemies) {
        Set<String> closed = new HashSet<>();
        PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        Map<String, PathNode> allNodes = new HashMap<>();
        PathNode start = new PathNode(ant.q, ant.r, null, 0, hexDist(ant.q, ant.r, target.q, target.r));
        open.add(start);
        allNodes.put(ant.q+","+ant.r, start);
        while (!open.isEmpty()) {
            PathNode node = open.poll();
            if (node.q == target.q && node.r == target.r) {
                List<Hex> path = new ArrayList<>();
                PathNode cur = node;
                while (cur.parent != null) {
                    path.add(0, new Hex(cur.q, cur.r));
                    cur = cur.parent;
                }
                return path;
            }
            closed.add(node.q+","+node.r);
            for (int[] d : directions()) {
                int nq = node.q + d[0], nr = node.r + d[1];
                if (closed.contains(nq+","+nr)) continue;
                Tile tile = state.map.stream().filter(t -> t.q == nq && t.r == nr).findFirst().orElse(null);
                if (tile == null || tile.type == 5) continue;
                if (tile.type == 4) continue;
                if (reserved.values().stream().anyMatch(h -> h.q == nq && h.r == nr)) continue;
                if (state.ants.stream().anyMatch(a -> !a.id.equals(ant.id) && a.q == nq && a.r == nr)) continue;
                if (state.enemies.stream().anyMatch(e -> e.q == nq && e.r == nr)) continue;
                if (avoidEnemies && isEnemyNear(nq, nr, state.enemies, 1)) continue;
                // Усиленное избегание опасных зон
                boolean isHomeTarget = state.home.stream().anyMatch(h -> h.q == target.q && h.r == target.r);
                if (memory.isDangerous(new Hex(nq, nr)) && !(isHomeTarget && nq == target.q && nr == target.r)) continue;
                int g = node.g + tile.cost + (memory.isEnemySpot(new Hex(nq, nr)) ? 2 : 0);
                if (g > MAX_PATH_LENGTH) continue;
                int h = hexDist(nq, nr, target.q, target.r);
                PathNode next = new PathNode(nq, nr, node, g, h);
                String key = nq+","+nr;
                if (!allNodes.containsKey(key) || g < allNodes.get(key).g) {
                    open.add(next);
                    allNodes.put(key, next);
                }
            }
        }
        return Collections.singletonList(new Hex(ant.q, ant.r));
    }

    private int[][] directions() {
        return new int[][]{{1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}};
    }

    // --- Вспомогательные методы ---
    private boolean isEnemyNear(int q, int r, List<Enemy> enemies, int radius) {
        for (Enemy e : enemies) {
            if (hexDist(q, r, e.q, e.r) <= radius) return true;
        }
        return false;
    }
    private boolean isAllyNear(int q, int r, List<Ant> ants, String excludeId, int radius) {
        for (Ant a : ants) {
            if (!a.id.equals(excludeId) && hexDist(q, r, a.q, a.r) <= radius) return true;
        }
        return false;
    }
    private boolean isNearHome(int q, int r, List<Hex> home) {
        for (Hex h : home) if (hexDist(q, r, h.q, h.r) <= 2) return true;
        return false;
    }
    private Hex nearestHex(int q, int r, List<Hex> hexes) {
        Hex best = null;
        int minDist = Integer.MAX_VALUE;
        for (Hex h : hexes) {
            int d = hexDist(q, r, h.q, h.r);
            if (d < minDist) { minDist = d; best = h; }
        }
        return best != null ? best : new Hex(q, r);
    }
    private int hexDist(int q1, int r1, int q2, int r2) {
        int dq = q2 - q1, dr = r2 - r1;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    // --- Вспомогательный класс для A* ---
    private static class PathNode {
        int q, r, g, h, f;
        PathNode parent;
        PathNode(int q, int r, PathNode parent, int g, int h) {
            this.q = q; this.r = r; this.parent = parent; this.g = g; this.h = h; this.f = g + h;
        }
    }

    // --- Анализ логов ---
    private void analyzeLogs() {
        try {
            for (LogMessage log : api.getLogs()) {
                if (log.message.contains("уничтожен") || log.message.contains("погиб")) {
                    // Пытаемся извлечь координаты из сообщения
                    int q = extractInt(log.message, "q=");
                    int r = extractInt(log.message, "r=");
                    if (q != Integer.MIN_VALUE && r != Integer.MIN_VALUE) {
                        memory.markDeath(new Hex(q, r));
                        botLog.add("[Смерть] " + log.message);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
    private int extractInt(String text, String key) {
        int idx = text.indexOf(key);
        if (idx == -1) return Integer.MIN_VALUE;
        int start = idx + key.length();
        int end = start;
        while (end < text.length() && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '-')) end++;
        try { return Integer.parseInt(text.substring(start, end)); } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    // --- Поиск безопасной разведанной области ---
    private Hex findSafeExploredHex(Ant ant, PlayerResponse state) {
        List<Tile> safe = new java.util.ArrayList<>();
        for (Tile t : state.map) {
            if (t.type != 5 && t.type != 4 && !memory.isDangerous(new Hex(t.q, t.r))) {
                boolean occupied = false;
                for (Ant a : state.ants) if (a.q == t.q && a.r == t.r) occupied = true;
                for (Enemy e : state.enemies) if (e.q == t.q && e.r == t.r) occupied = true;
                if (!occupied) safe.add(t);
            }
        }
        if (safe.isEmpty()) return null;
        Tile best = safe.get((ant.id.hashCode() & 0x7fffffff) % safe.size());
        return new Hex(best.q, best.r);
    }

    // --- Поиск ближайшего чужого муравейника ---
    private Hex findEnemyHome(PlayerResponse state, List<Hex> myHome) {
        // Ищем гекс муравейника, который не совпадает с нашими
        Set<String> myHomeSet = new java.util.HashSet<>();
        for (Hex h : myHome) myHomeSet.add(h.q+","+h.r);
        for (Tile t : state.map) {
            if (t.type == 1 && !myHomeSet.contains(t.q+","+t.r)) {
                return new Hex(t.q, t.r);
            }
        }
        return null;
    }
} 