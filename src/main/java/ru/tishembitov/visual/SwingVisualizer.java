package ru.tishembitov.visual;

import ru.tishembitov.bot.BotMemory;
import ru.tishembitov.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SwingVisualizer extends JFrame {
    private final VisualPanel panel;
    private final InfoPanel infoPanel;
    public SwingVisualizer() {
        setTitle("DatsPulse Swing Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 1100);
        setLocationRelativeTo(null);
        panel = new VisualPanel();
        infoPanel = new InfoPanel(panel);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.EAST);
        setVisible(true);
    }
    public void setData(PlayerResponse state, BotMemory memory, int turn, int score, int maxAnts, String status, List<String> eventLog) {
        panel.setData(state, memory, turn, score, maxAnts, status, eventLog);
        infoPanel.setData(state, memory, turn, score, maxAnts, status, eventLog, panel.selectedAnt, panel.selectedTarget);
        panel.repaint();
        infoPanel.repaint();
    }
    public static SwingVisualizer instance;
    public static void launchVisualizer() {
        SwingUtilities.invokeLater(() -> instance = new SwingVisualizer());
    }
    public static class VisualPanel extends JPanel {
        private double hexSize = 28;
        private double offsetX = 400;
        private double offsetY = 400;
        private double dragStartX, dragStartY;
        private double dragOriginX, dragOriginY;
        private int layer = 0;
        private PlayerResponse state;
        private BotMemory memory;
        private int turn, score, maxAnts;
        private String status;
        private List<String> eventLog;
        private String selectedAntId = null;
        private List<Hex> selectedPath = null;
        private Ant selectedAnt = null;
        private Hex selectedTarget = null;
        private int mouseX = -1, mouseY = -1;
        private boolean showRoutes = true, showEnemies = true, showResources = true, showDanger = true;
        public VisualPanel() {
            addMouseWheelListener(e -> {
                if (e.getWheelRotation() < 0) hexSize = Math.min(80, hexSize + 2);
                else hexSize = Math.max(8, hexSize - 2);
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                    dragOriginX = offsetX;
                    dragOriginY = offsetY;
                    requestFocusInWindow();
                    int[] qr = pixelToHex(e.getX(), e.getY());
                    // --- Ручное выделение ресурса ---
                    if (state != null && memory != null) {
                        for (FoodOnMap food : state.food) {
                            if (food.q == qr[0] && food.r == qr[1]) {
                                Hex hex = new Hex(food.q, food.r);
                                if (memory.isManualResourceTarget(hex)) {
                                    memory.removeManualResourceTarget(hex);
                                } else {
                                    memory.addManualResourceTarget(hex);
                                }
                                repaint();
                                return;
                            }
                        }
                    }
                    // --- Выделение муравья по клику ---
                    selectedAntId = null;
                    selectedPath = null;
                    selectedAnt = null;
                    selectedTarget = null;
                    if (state != null) {
                        for (Ant a : state.ants) {
                            if (a.q == qr[0] && a.r == qr[1]) {
                                selectedAntId = a.id;
                                selectedAnt = a;
                                if (a.move != null && a.move.length > 0) {
                                    selectedPath = new java.util.ArrayList<>();
                                    for (Hex h : a.move) selectedPath.add(h);
                                    selectedTarget = a.move[a.move.length-1];
                                }
                                break;
                            }
                        }
                    }
                    repaint();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    offsetX = dragOriginX + (e.getX() - dragStartX);
                    offsetY = dragOriginY + (e.getY() - dragStartY);
                    repaint();
                }
                public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    repaint();
                }
            });
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_1) layer = 1;
                    else if (e.getKeyCode() == KeyEvent.VK_2) layer = 2;
                    else if (e.getKeyCode() == KeyEvent.VK_3) layer = 3;
                    else if (e.getKeyCode() == KeyEvent.VK_0) layer = 0;
                    repaint();
                }
            });
        }
        public void setData(PlayerResponse state, BotMemory memory, int turn, int score, int maxAnts, String status, List<String> eventLog) {
            this.state = state;
            this.memory = memory;
            this.turn = turn;
            this.score = score;
            this.maxAnts = maxAnts;
            this.status = status;
            this.eventLog = eventLog;
        }
        public void setShowRoutes(boolean b) { showRoutes = b; }
        public void setShowEnemies(boolean b) { showEnemies = b; }
        public void setShowResources(boolean b) { showResources = b; }
        public void setShowDanger(boolean b) { showDanger = b; }
        public void clearSelection() { selectedAntId = null; selectedAnt = null; selectedPath = null; selectedTarget = null; }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (state == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 1. Карта
            for (Tile tile : state.map) {
                Point xy = hexToPixel(tile.q, tile.r);
                g2.setColor(tileColor(tile.type));
                fillHex(g2, xy.x, xy.y, hexSize);
                g2.setColor(Color.GRAY);
                drawHex(g2, xy.x, xy.y, hexSize);
            }
            // 2. Опасные зоны
            if (showDanger && memory != null) {
                Set<String> danger = memory.getDangerZones();
                for (String s : danger) {
                    String[] parts = s.split(",");
                    int q = Integer.parseInt(parts[0]);
                    int r = Integer.parseInt(parts[1]);
                    Point xy = hexToPixel(q, r);
                    g2.setColor(new Color(255,0,0,45));
                    fillHex(g2, xy.x, xy.y, hexSize);
                }
            }
            // 3. Ресурсы
            if (showResources) {
                for (FoodOnMap food : state.food) {
                    Point xy = hexToPixel(food.q, food.r);
                    g2.setColor(foodColor(food.type));
                    g2.fillOval((int)(xy.x - 7), (int)(xy.y - 7), 14, 14);
                    // --- Выделение выбранных пользователем ресурсов ---
                    if (memory != null && memory.isManualResourceTarget(new Hex(food.q, food.r))) {
                        g2.setColor(Color.YELLOW);
                        g2.setStroke(new BasicStroke(3f));
                        g2.drawOval((int)(xy.x - 10), (int)(xy.y - 10), 20, 20);
                        g2.setStroke(new BasicStroke(1f));
                    }
                }
            }
            // 4. Муравейник
            for (Hex h : state.home) {
                Point xy = hexToPixel(h.q, h.r);
                g2.setColor(Color.MAGENTA);
                drawHex(g2, xy.x, xy.y, hexSize);
            }
            if (state.spot != null) {
                Point xy = hexToPixel(state.spot.q, state.spot.r);
                g2.setColor(Color.MAGENTA.darker());
                fillHex(g2, xy.x, xy.y, hexSize);
            }
            // 5. Враги
            if (showEnemies) {
                for (Enemy e : state.enemies) {
                    Point xy = hexToPixel(e.q, e.r);
                    g2.setColor(Color.RED);
                    g2.fillRect((int)(xy.x - 8), (int)(xy.y - 8), 16, 16);
                }
            }
            // 6. Свои муравьи (по слоям)
            Predicate<Ant> filter = a -> true;
            if (layer == 1) filter = a -> a.type == 0;
            else if (layer == 2) filter = a -> a.type == 1;
            else if (layer == 3) filter = a -> a.type == 2;
            for (Ant a : state.ants) {
                if (!filter.test(a)) continue;
                Point xy = hexToPixel(a.q, a.r);
                g2.setColor(antColor(a.type));
                g2.fillOval((int)(xy.x - 12), (int)(xy.y - 12), 24, 24);
                // HP-полоска под муравьём
                int hpBarW = 24, hpBarH = 5;
                int hpX = (int)(xy.x - hpBarW/2), hpY = (int)(xy.y + 14);
                double hpFrac = Math.max(0, Math.min(1, a.health / 100.0));
                g2.setColor(Color.RED);
                g2.fillRect(hpX, hpY, hpBarW, hpBarH);
                g2.setColor(new Color(0,200,0));
                g2.fillRect(hpX, hpY, (int)(hpBarW * hpFrac), hpBarH);
                g2.setColor(Color.BLACK);
                g2.drawRect(hpX, hpY, hpBarW, hpBarH);
                // Оверлей: тип по центру (цифра)
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 15f));
                g2.setColor(Color.BLACK);
                g2.drawString(String.valueOf(a.type), (int)(xy.x - 4), (int)(xy.y + 5));
                // Чёткая стрелка маршрута: белая толстая подложка + цветная стрелка сверху
                if (showRoutes && a.move != null && a.move.length > 0) {
                    Point prev = xy;
                    boolean isSelected = (selectedAntId != null && selectedAntId.equals(a.id));
                    for (Hex h : a.move) {
                        Point next = hexToPixel(h.q, h.r);
                        // Сначала белая толстая стрелка (контур)
                        drawArrow(g2, prev.x, prev.y, next.x, next.y, Color.WHITE, isSelected ? 8 : 6);
                        // Затем цветная стрелка
                        Color arrowColor = isSelected ? Color.MAGENTA : antColor(a.type);
                        drawArrow(g2, prev.x, prev.y, next.x, next.y, arrowColor, isSelected ? 4 : 3);
                        prev = next;
                    }
                }
            }
            // Подсветка маршрута выбранного муравья — убираю дублирование, только выделение цели и инфо
            if (selectedPath != null && !selectedPath.isEmpty()) {
                if (selectedTarget != null) {
                    Point p = hexToPixel(selectedTarget.q, selectedTarget.r);
                    g2.setColor(Color.MAGENTA);
                    g2.drawRect((int)(p.x - hexSize/2), (int)(p.y - hexSize/2), (int)hexSize, (int)hexSize);
                }
            }
            // Информация о выбранном муравье
            if (selectedAnt != null) {
                StringBuilder info = new StringBuilder();
                info.append("Выбран: id=").append(selectedAnt.id)
                    .append(", тип=").append(selectedAnt.type)
                    .append(", q=").append(selectedAnt.q)
                    .append(", r=").append(selectedAnt.r)
                    .append(", HP=").append(selectedAnt.health);
                if (selectedAnt.food != null && selectedAnt.food.amount > 0)
                    info.append(", ресурс=").append(selectedAnt.food.type).append(", кол-во=").append(selectedAnt.food.amount);
                if (selectedTarget != null)
                    info.append(", цель: q=").append(selectedTarget.q).append(", r=").append(selectedTarget.r);
                g2.setColor(Color.MAGENTA.darker());
                g2.drawString(info.toString(), 10, getHeight() - 30);
            }
            // 7. UI-информация
            g2.setColor(Color.BLACK);
            g2.drawString("Ход: " + turn + " | Калории: " + score + " | Статус: " + status + " | Муравьи: " + state.ants.size() + "/" + maxAnts, 20, 30);
            g2.drawString("Слой: " + (layer == 0 ? "Все" : (layer == 1 ? "Рабочие" : (layer == 2 ? "Бойцы" : "Разведчики"))) + " | 1-3: слои | 0: все | Мышь: drag/zoom, клик=маршрут", 20, 50);
            // 8. Продвинутая легенда (русский)
            // В легенде:
            int legendY = 70;
            g2.setColor(Color.BLACK);
            g2.drawString("Легенда:", 20, legendY); legendY += 16;
            g2.setColor(new Color(0,180,0)); g2.fillOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("0 — рабочий", 35, legendY+10); legendY += 14;
            g2.setColor(new Color(80,80,255)); g2.fillOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("1 — боец", 35, legendY+10); legendY += 14;
            g2.setColor(Color.CYAN); g2.fillOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("2 — разведчик", 35, legendY+10); legendY += 14;
            g2.setColor(Color.GREEN); g2.fillOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("Яблоко", 35, legendY+10); legendY += 14;
            g2.setColor(Color.ORANGE); g2.fillOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("Хлеб", 35, legendY+10); legendY += 14;
            g2.setColor(Color.BLUE); g2.fillOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("Нектар", 35, legendY+10); legendY += 14;
            g2.setColor(Color.YELLOW); g2.drawOval(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("Ресурс выбран вручную (клик)", 35, legendY+10); legendY += 14;
            g2.setColor(Color.MAGENTA); g2.drawRect(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("Муравейник", 35, legendY+10); legendY += 14;
            g2.setColor(new Color(255,0,0,45)); g2.fillRect(20, legendY, 10, 10); g2.setColor(Color.BLACK); g2.drawString("Опасная зона", 35, legendY+10); legendY += 14;
            g2.setColor(Color.WHITE); g2.drawLine(20, legendY+6, 32, legendY+6); g2.setColor(Color.MAGENTA); g2.drawLine(34, legendY+6, 46, legendY+6); g2.setColor(Color.BLACK); g2.drawString("Стрелка маршрута", 50, legendY+10); legendY += 14;
            // 9. Логи (справа, шире, больше строк, автоперенос)
            // if (eventLog != null) {
            //     int logX = 950;
            //     int logY = 40;
            //     int logW = 420;
            //     int logH = 600;
            //     g2.setColor(new Color(255,255,255,230));
            //     g2.fillRect(logX-10, logY-20, logW, logH);
            //     g2.setColor(Color.BLACK);
            //     g2.drawRect(logX-10, logY-20, logW, logH);
            //     g2.drawString("Логи событий:", logX, logY);
            //     logY += 18;
            //     int maxLines = (logH-30)/16;
            //     int start = Math.max(0, eventLog.size() - maxLines);
            //     for (int i = start; i < eventLog.size(); i++) {
            //         String msg = eventLog.get(i);
            //         while (msg.length() > 0) {
            //             String part = msg.length() > 60 ? msg.substring(0, 60) : msg;
            //             g2.drawString(part, logX, logY);
            //             logY += 16;
            //             if (msg.length() > 60) msg = msg.substring(60);
            //             else break;
            //         }
            //     }
            // }
            // Добавить тултипы: при наведении мыши на гекс или муравья показывать инфо
            // Для этого — сохранить координаты мыши и в paintComponent рисовать тултип, если мышь над объектом
            if (mouseX >= 0 && mouseY >= 0) {
                int[] qr = pixelToHex(mouseX, mouseY);
                String tooltip = null;
                // Проверка муравьёв
                for (Ant a : state.ants) {
                    if (a.q == qr[0] && a.r == qr[1]) {
                        tooltip = "Муравей id=" + a.id + ", тип=" + a.type + ", HP=" + a.health + (a.food != null && a.food.amount > 0 ? ", несёт: " + a.food.type + "(" + a.food.amount + ")" : "");
                        break;
                    }
                }
                // Если не муравей — проверка врагов
                if (tooltip == null) {
                    for (Enemy e : state.enemies) {
                        if (e.q == qr[0] && e.r == qr[1]) {
                            tooltip = "Враг, HP=" + e.health + ", тип=" + e.type;
                            break;
                        }
                    }
                }
                // Если не враг — проверка ресурсов
                if (tooltip == null) {
                    for (FoodOnMap food : state.food) {
                        if (food.q == qr[0] && food.r == qr[1]) {
                            tooltip = "Ресурс: " + food.type + ", кол-во=" + food.amount;
                            break;
                        }
                    }
                }
                // Если не ресурс — тайл
                if (tooltip == null) {
                    for (Tile tile : state.map) {
                        if (tile.q == qr[0] && tile.r == qr[1]) {
                            tooltip = "Тайл: тип=" + tile.type;
                            break;
                        }
                    }
                }
                if (tooltip != null) {
                    g2.setColor(new Color(255,255,220,240));
                    g2.fillRoundRect(mouseX+12, mouseY-18, g2.getFontMetrics().stringWidth(tooltip)+16, 24, 8, 8);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(mouseX+12, mouseY-18, g2.getFontMetrics().stringWidth(tooltip)+16, 24, 8, 8);
                    g2.drawString(tooltip, mouseX+20, mouseY);
                }
            }
        }
        private Point hexToPixel(int q, int r) {
            int x = (int) (hexSize * Math.sqrt(3) * (q + r / 2.0) + offsetX);
            int y = (int) (hexSize * 3.0 / 2 * r + offsetY);
            return new Point(x, y);
        }
        private void drawHex(Graphics2D g, int x, int y, double size) {
            int[] xs = new int[6];
            int[] ys = new int[6];
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 3 * i + Math.PI / 6;
                xs[i] = (int) (x + size * Math.cos(angle));
                ys[i] = (int) (y + size * Math.sin(angle));
            }
            g.drawPolygon(xs, ys, 6);
        }
        private void fillHex(Graphics2D g, int x, int y, double size) {
            int[] xs = new int[6];
            int[] ys = new int[6];
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 3 * i + Math.PI / 6;
                xs[i] = (int) (x + size * Math.cos(angle));
                ys[i] = (int) (y + size * Math.sin(angle));
            }
            g.fillPolygon(xs, ys, 6);
        }
        private Color tileColor(int type) {
            switch (type) {
                case 1: return new Color(200, 120, 255); // anthill (фиолетовый)
                case 2: return new Color(180, 255, 180); // plain (зелёный)
                case 3: return new Color(180, 140, 80);  // dirt (коричневый)
                case 4: return new Color(100, 255, 255); // acid (бирюзовый)
                case 5: return new Color(80, 80, 80);    // rock (тёмно-серый)
                default: return Color.LIGHT_GRAY;
            }
        }
        private Color foodColor(int type) {
            switch (type) {
                case 1: return Color.GREEN;
                case 2: return Color.ORANGE;
                case 3: return Color.BLUE;
                default: return Color.GRAY;
            }
        }
        private Color antColor(int type) {
            switch (type) {
                case 0: return new Color(0, 180, 0);
                case 1: return new Color(80, 80, 255);
                case 2: return Color.CYAN;
                default: return Color.BLACK;
            }
        }
        private void drawArrow(Graphics2D g, int x1, int y1, int x2, int y2, Color color, int thickness) {
            Stroke old = g.getStroke();
            g.setColor(color);
            g.setStroke(new BasicStroke(thickness));
            g.drawLine(x1, y1, x2, y2);
            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.sqrt(dx*dx + dy*dy);
            if (len < 1) { g.setStroke(old); return; }
            double ux = dx / len, uy = dy / len;
            double arrowSize = 8 + thickness;
            int ax = (int) (x2 - arrowSize * (ux * Math.cos(Math.PI/6) + uy * Math.sin(Math.PI/6)));
            int ay = (int) (y2 - arrowSize * (uy * Math.cos(Math.PI/6) - ux * Math.sin(Math.PI/6)));
            int bx = (int) (x2 - arrowSize * (ux * Math.cos(-Math.PI/6) + uy * Math.sin(-Math.PI/6)));
            int by = (int) (y2 - arrowSize * (uy * Math.cos(-Math.PI/6) - ux * Math.sin(-Math.PI/6)));
            g.drawLine(x2, y2, ax, ay);
            g.drawLine(x2, y2, bx, by);
            g.setStroke(old);
        }
        private int[] pixelToHex(int x, int y) {
            double q = ((x - offsetX) * Math.sqrt(3)/3 - (y - offsetY) / 3.0) / hexSize;
            double r = (y - offsetY) * 2.0/3 / hexSize;
            return new int[]{(int)Math.round(q), (int)Math.round(r)};
        }
    }
    public static class InfoPanel extends JPanel {
        private JCheckBox showRoutes, showEnemies, showResources, showDanger;
        private JButton resetSelection;
        private JLabel rolesLabel, resourcesLabel, selectedLabel, turnLabel;
        private PlayerResponse state;
        private BotMemory memory;
        private int turn, score, maxAnts;
        private String status;
        private List<String> eventLog;
        private Ant selectedAnt;
        private Hex selectedTarget;
        private VisualPanel visualPanel;
        public InfoPanel(VisualPanel visualPanel) {
            this.visualPanel = visualPanel;
            setPreferredSize(new Dimension(260, 0));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
            turnLabel = new JLabel();
            add(turnLabel);
            add(Box.createVerticalStrut(10));
            rolesLabel = new JLabel();
            add(rolesLabel);
            add(Box.createVerticalStrut(10));
            resourcesLabel = new JLabel();
            add(resourcesLabel);
            add(Box.createVerticalStrut(10));
            selectedLabel = new JLabel();
            add(selectedLabel);
            add(Box.createVerticalStrut(20));
            showRoutes = new JCheckBox("Показывать маршруты", true);
            showEnemies = new JCheckBox("Показывать врагов", true);
            showResources = new JCheckBox("Показывать ресурсы", true);
            showDanger = new JCheckBox("Показывать опасные зоны", true);
            add(showRoutes);
            add(showEnemies);
            add(showResources);
            add(showDanger);
            add(Box.createVerticalStrut(20));
            resetSelection = new JButton("Сбросить выделение");
            add(resetSelection);
            // Слушатели чекбоксов и кнопки
            showRoutes.addActionListener(e -> { visualPanel.setShowRoutes(showRoutes.isSelected()); visualPanel.repaint(); });
            showEnemies.addActionListener(e -> { visualPanel.setShowEnemies(showEnemies.isSelected()); visualPanel.repaint(); });
            showResources.addActionListener(e -> { visualPanel.setShowResources(showResources.isSelected()); visualPanel.repaint(); });
            showDanger.addActionListener(e -> { visualPanel.setShowDanger(showDanger.isSelected()); visualPanel.repaint(); });
            resetSelection.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    visualPanel.clearSelection();
                    visualPanel.repaint();
                }
            });
        }
        public void setData(PlayerResponse state, BotMemory memory, int turn, int score, int maxAnts, String status, List<String> eventLog, Ant selectedAnt, Hex selectedTarget) {
            this.state = state;
            this.memory = memory;
            this.turn = turn;
            this.score = score;
            this.maxAnts = maxAnts;
            this.status = status;
            this.eventLog = eventLog;
            this.selectedAnt = selectedAnt;
            this.selectedTarget = selectedTarget;
            updateLabels();
        }
        private void updateLabels() {
            if (state == null) return;
            turnLabel.setText("Ход: " + turn + " | Калории: " + score + " | Муравьи: " + state.ants.size() + "/" + maxAnts);
            int workers = 0, soldiers = 0, scouts = 0;
            for (Ant a : state.ants) {
                if (a.type == 0) workers++;
                else if (a.type == 1) soldiers++;
                else if (a.type == 2) scouts++;
            }
            rolesLabel.setText("Роли: Рабочие: " + workers + ", Бойцы: " + soldiers + ", Разведчики: " + scouts);
            int apples = 0, bread = 0, nectar = 0;
            for (FoodOnMap f : state.food) {
                if (f.type == 1) apples += f.amount;
                else if (f.type == 2) bread += f.amount;
                else if (f.type == 3) nectar += f.amount;
            }
            resourcesLabel.setText("Ресурсы: яблоки=" + apples + ", хлеб=" + bread + ", нектар=" + nectar);
            if (selectedAnt != null) {
                selectedLabel.setText("Выбран: id=" + selectedAnt.id + ", тип=" + selectedAnt.type + ", HP=" + selectedAnt.health);
            } else if (selectedTarget != null) {
                selectedLabel.setText("Цель: q=" + selectedTarget.q + ", r=" + selectedTarget.r);
            } else {
                selectedLabel.setText("Ничего не выбрано");
            }
        }
    }
} 