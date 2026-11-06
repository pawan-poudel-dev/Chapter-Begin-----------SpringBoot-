import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/**
 * FRIDAY 5:59 PM DEPLOY SURVIVOR  (v2)
 * Dodge bugs, grab coffee, beat THE MANAGER boss, chain close calls for huge combos.
 *
 * Controls: Mouse or LEFT/RIGHT (A/D) = move, ENTER = start, P = screenshot,
 *           M = mute, C = copy LinkedIn share text (game over screen).
 */
public class FridayDeploySurvivor extends JPanel
        implements ActionListener, KeyListener, MouseMotionListener, MouseListener {

    // ====== CHANGE THIS to your own name before recording your video ======
    static final String AUTHOR = "Built by Your Name  |  #Java #BuildInPublic";

    static final int W = 900, H = 600;
    static final String SAVE_FILE = "highscore.txt";

    enum State { MENU, PLAYING, OVER }

    // type: 0 = bug, 1 = coffee, 2 = stack overflow shield, 3 = code review (+life), 4 = git revert (slow-mo)
    static class Item {
        double x, y, vx, vy, wob, wobAmp;
        int type, w;
        String label, reaction;
        boolean passed, fast, small;
    }

    static class Particle {
        double x, y, vx, vy;
        int life, max;
        Color c;
        float size;
    }

    static class Pop {
        String text;
        double x, y;
        int life;
        Color c;
    }

    static class Rain {
        double x, y, speed;
        char ch;
    }

    // ====================== SOUND (synthesized, no files needed) ======================
    static class Sfx {
        static volatile boolean muted = false;
        static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sfx");
            t.setDaemon(true);
            return t;
        });

        /** pairs of (frequencyHz, milliseconds) */
        static void tone(int... seq) {
            if (muted) return;
            POOL.submit(() -> {
                try {
                    AudioFormat af = new AudioFormat(22050f, 8, 1, true, false);
                    try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
                        line.open(af);
                        line.start();
                        for (int i = 0; i + 1 < seq.length; i += 2) {
                            int n = (int) (22050 * seq[i + 1] / 1000.0);
                            byte[] b = new byte[n];
                            for (int j = 0; j < n; j++) {
                                double env = 1.0 - (double) j / n;
                                b[j] = (byte) (Math.sin(2 * Math.PI * seq[i] * j / 22050.0) * 45 * env);
                            }
                            line.write(b, 0, n);
                        }
                        line.drain();
                    }
                } catch (Throwable ignored) {
                    // no audio device available: stay silent
                }
            });
        }

        static void hit() { tone(160, 110, 110, 160); }
        static void coffee() { tone(660, 60, 880, 60, 1100, 90); }
        static void shield() { tone(500, 80, 750, 130); }
        static void lgtm() { tone(523, 70, 659, 70, 784, 70, 1047, 130); }
        static void revert() { tone(900, 60, 600, 80, 350, 140); }
        static void close() { tone(1300, 45); }
        static void boss() { tone(300, 160, 200, 160, 300, 160, 200, 200); }
        static void win() { tone(523, 100, 659, 100, 784, 100, 1047, 260); }
        static void level() { tone(440, 70, 660, 110); }
        static void over() { tone(400, 160, 300, 160, 200, 160, 120, 420); }
    }

    // ====================== CONTENT ======================

    static final String[][] BUGS = {
            {"NullPointerException", "Surprise! Null."},
            {"It works on my machine", "Then ship your machine!"},
            {"Merge Conflict", "Git blame says: you."},
            {"Friday 5PM Deploy", "Weekend: cancelled."},
            {"TODO: fix later", "Later is now."},
            {"Infinite Loop", "See: Infinite Loop."},
            {"Missing semicolon", "One tiny ; = chaos."},
            {"Prod is down", "Slack: 99+ messages."},
            {"Works in dev", "Prod disagrees."},
            {"StackOverflowError", "Irony detected."},
            {"Legacy code", "Nobody knows why it works."},
            {"Intern pushed to main", "Pray. Just pray."}
    };

    static final String[][] BOSS_BULLETS = {
            {"Small feature", "Scope creep intensifies."},
            {"Quick call?", "It was not quick."},
            {"Why is it not done?", "Because Jira."},
            {"Make logo bigger", "Pixels cry."},
            {"Can we pivot?", "Pivot detected."},
            {"Per my last email", "Passive aggression: 100."}
    };

    static final String[] BOSS_LINES = {
            "Can we add one small feature?", "Let's circle back.", "Why is it not done?",
            "I need this by EOD.", "Great job. Now do it again.", "Let's take this offline.",
            "Do more with less."
    };

    static final String[] TICKER = {
            "Tip: Never deploy on Friday. You are deploying on Friday.",
            "HR: 'We are a family.' Family does not fire people at 6 PM.",
            "Fun fact: 90% of bugs are fixed by restarting your laptop.",
            "Manager: 'Quick call?' Narrator: It was not quick.",
            "Coffee level critical. Please insert more coffee.",
            "Remember: it's not a bug, it's an undocumented feature.",
            "Pro tip: near misses build combos. Live dangerously."
    };

    static final String[] FIRED = {
            "You got fired. Reason: 'Culture fit'.",
            "HR: 'Let's circle back.' (They did not.)",
            "Production is down. So is your career.",
            "Your severance package: 1 cold coffee.",
            "Manager: 'Great effort. Please clear your desk.'"
    };

    // ====================== STATE ======================

    final Random rnd = new Random();
    final List<Item> items = new ArrayList<>();
    final List<Particle> parts = new ArrayList<>();
    final List<Pop> pops = new ArrayList<>();
    final List<Rain> rain = new ArrayList<>();
    final Set<String> unlocked = new HashSet<>();
    final double[] trail = new double[8];

    final Font itemFont = new Font("SansSerif", Font.BOLD, 14);
    final Font popFont = new Font("SansSerif", Font.BOLD, 18);

    State state = State.MENU;
    double playerX = W / 2.0, targetX = W / 2.0;
    final int playerY = H - 95;
    boolean left, right;

    int tick, menuTick, spawnTimer, level = 1, lives = 3, score, best;
    int shake, hurt, flash, shieldTicks, coffeeTicks, slowTicks, bannerTicks;
    int combo, bestCombo, coffees, hits, bossesBeaten;
    int toastTicks;
    String banner = "", toast = "", firedMsg = FIRED[0];

    // boss
    boolean bossActive;
    int bossTicks, nextBoss, bossAttackTimer, helpTimer, bossSayTicks;
    double bossX = W / 2.0, bossY = -80;
    String bossSay = "";

    public FridayDeploySurvivor() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        for (int i = 0; i < 70; i++) {
            Rain r = new Rain();
            r.x = rnd.nextInt(W);
            r.y = rnd.nextInt(H);
            r.speed = 1 + rnd.nextDouble() * 3;
            r.ch = randomCode();
            rain.add(r);
        }
        loadBest();
        new javax.swing.Timer(16, this).start();
    }

    char randomCode() {
        String s = "01{}();<>/=+*#&[]";
        return s.charAt(rnd.nextInt(s.length()));
    }

    void loadBest() {
        try {
            best = Integer.parseInt(Files.readAllLines(Paths.get(SAVE_FILE)).get(0).trim());
        } catch (Exception ignored) {
            best = 0;
        }
    }

    void saveBest() {
        try {
            Files.write(Paths.get(SAVE_FILE), String.valueOf(best).getBytes());
        } catch (Exception ignored) {
            // not critical
        }
    }

    // ====================== GAME LOGIC ======================

    void startGame() {
        state = State.PLAYING;
        items.clear();
        parts.clear();
        pops.clear();
        tick = 0;
        spawnTimer = 30;
        level = 1;
        lives = 3;
        score = 0;
        combo = bestCombo = coffees = hits = bossesBeaten = 0;
        shake = hurt = flash = shieldTicks = coffeeTicks = slowTicks = 0;
        bossActive = false;
        bossY = -80;
        nextBoss = 1500;
        banner = "FRIDAY 5:59 PM. DEPLOY NOW.";
        bannerTicks = 150;
    }

    void gameOver() {
        state = State.OVER;
        firedMsg = FIRED[rnd.nextInt(FIRED.length)];
        if (score > best) {
            best = score;
            saveBest();
        }
        Sfx.over();
    }

    int mult() {
        return 1 + Math.min(7, combo / 3);
    }

    int add(int base) {
        int g = base * mult();
        score += g;
        return g;
    }

    void unlock(String name, String desc) {
        if (unlocked.add(name)) {
            toast = "ACHIEVEMENT: " + name + " - " + desc;
            toastTicks = 260;
            Sfx.tone(784, 70, 988, 70, 1319, 140);
        }
    }

    Item makeItem(int type) {
        Item it = new Item();
        it.type = type;
        FontMetrics fm = getFontMetrics(itemFont);
        switch (type) {
            case 0:
                if (level >= 3 && rnd.nextInt(100) < 14) {
                    it.label = "URGENT!!!";
                    it.reaction = "Priority changed again.";
                    it.fast = true;
                } else {
                    String[] b = BUGS[rnd.nextInt(BUGS.length)];
                    it.label = b[0];
                    it.reaction = b[1];
                    it.wobAmp = 14;
                }
                break;
            case 1: it.label = "Coffee"; break;
            case 2: it.label = "Copied from StackOverflow"; break;
            case 3: it.label = "LGTM! (Code Review)"; break;
            default: it.label = "git revert HEAD"; break;
        }
        it.w = fm.stringWidth(it.label) + 28;
        it.x = it.w / 2.0 + rnd.nextDouble() * (W - it.w);
        it.y = -30;
        it.vy = (2.0 + level * 0.45 + rnd.nextDouble() * 1.5) * (it.fast ? 1.7 : 1.0);
        it.wob = rnd.nextDouble() * 6;
        return it;
    }

    void spawn() {
        int roll = rnd.nextInt(100);
        int type = roll < 70 ? 0 : roll < 81 ? 1 : roll < 88 ? 2 : roll < 93 ? 3 : 4;
        items.add(makeItem(type));
    }

    void spawnHelp() {
        int roll = rnd.nextInt(100);
        int type = roll < 35 ? 1 : roll < 60 ? 2 : roll < 80 ? 3 : 4;
        items.add(makeItem(type));
    }

    void spawnWave() {
        int cols = 8;
        double cw = W / (double) cols;
        int gap = rnd.nextInt(cols - 1); // columns gap and gap+1 stay empty
        FontMetrics fm = getFontMetrics(itemFont);
        double vy = 2.6 + level * 0.3;
        for (int c = 0; c < cols; c++) {
            if (c == gap || c == gap + 1) continue;
            Item it = new Item();
            it.type = 0;
            it.small = true;
            it.label = "bug";
            it.reaction = "Sprint velocity: panic.";
            it.w = fm.stringWidth(it.label) + 28;
            it.x = cw * c + cw / 2;
            it.y = -30;
            it.vy = vy;
            items.add(it);
        }
        banner = "SPRINT PLANNING WAVE! Find the gap!";
        bannerTicks = 110;
    }

    void startBoss() {
        bossActive = true;
        bossTicks = 0;
        bossAttackTimer = 60;
        helpTimer = 120;
        bossSay = "Got a minute?";
        bossSayTicks = 100;
        banner = "BOSS FIGHT: THE MANAGER";
        bannerTicks = 160;
        shake = 12;
        nextBoss = tick + 3000;
        Sfx.boss();
    }

    void bossAttack() {
        String[] b = BOSS_BULLETS[rnd.nextInt(BOSS_BULLETS.length)];
        FontMetrics fm = getFontMetrics(itemFont);
        int w = fm.stringWidth(b[0]) + 28;
        double vy = 3.0 + level * 0.2;
        int pattern = rnd.nextInt(3);
        int count = pattern == 0 ? 1 : 3;
        for (int i = 0; i < count; i++) {
            Item it = new Item();
            it.type = 0;
            it.label = b[0];
            it.reaction = b[1];
            it.w = w;
            it.y = 110;
            it.vy = vy;
            it.x = bossX;
            if (pattern == 0) { // aimed at the player
                double t = (playerY - it.y) / vy;
                it.vx = (playerX - bossX) / t;
            } else if (pattern == 1) { // spread
                it.vx = (i - 1) * 2.4;
            } else { // scope-creep rain
                it.x = 60 + rnd.nextDouble() * (W - 120);
                it.y = 20;
            }
            items.add(it);
        }
        if (rnd.nextInt(3) == 0) {
            bossSay = BOSS_LINES[rnd.nextInt(BOSS_LINES.length)];
            bossSayTicks = 90;
        }
    }

    void endBoss() {
        bossActive = false;
        bossesBeaten++;
        score += 200;
        banner = "BOSS DEFEATED! +200  Manager: 'Fine. Take Monday off.'";
        bannerTicks = 180;
        flash = 14;
        spawnTimer = 60;
        for (int i = 0; i < 6; i++) {
            burst(100 + rnd.nextInt(W - 200), 100 + rnd.nextInt(200),
                    Color.getHSBColor(rnd.nextFloat(), 0.7f, 1f), 30);
        }
        unlock("Boss Slayer", "Survived the Manager");
        Sfx.win();
    }

    void burst(double x, double y, Color c, int n) {
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            double a = rnd.nextDouble() * Math.PI * 2;
            double s = 1 + rnd.nextDouble() * 6;
            p.vx = Math.cos(a) * s;
            p.vy = Math.sin(a) * s - 2;
            p.max = p.life = 25 + rnd.nextInt(30);
            p.c = c;
            p.size = 3 + rnd.nextFloat() * 5;
            parts.add(p);
        }
    }

    void pop(String text, double x, double y, Color c) {
        Pop p = new Pop();
        p.text = text;
        p.x = x;
        p.y = y;
        p.c = c;
        p.life = 70;
        pops.add(p);
    }

    double itemX(Item it) {
        return it.x + Math.sin(it.wob) * it.wobAmp;
    }

    void update() {
        for (Rain r : rain) {
            r.y += r.speed;
            if (r.y > H) {
                r.y = -10;
                r.x = rnd.nextInt(W);
                r.ch = randomCode();
            }
        }

        if (state == State.MENU) { // demo bugs floating on the menu
            if (++menuTick % 45 == 0) {
                Item it = makeItem(rnd.nextInt(100) < 70 ? 0 : 1 + rnd.nextInt(4));
                it.vy = 1.5 + rnd.nextDouble() * 1.5;
                items.add(it);
            }
            for (int i = items.size() - 1; i >= 0; i--) {
                Item it = items.get(i);
                it.y += it.vy;
                it.wob += 0.12;
                if (it.y > H + 20) items.remove(i);
            }
            updateEffects();
            return;
        }
        if (state == State.OVER) {
            updateEffects();
            return;
        }

        tick++;

        double speed = coffeeTicks > 0 ? 13 : 9;
        if (left) targetX -= speed;
        if (right) targetX += speed;
        targetX = Math.max(45, Math.min(W - 45, targetX));
        playerX += (targetX - playerX) * 0.25;
        for (int i = trail.length - 1; i > 0; i--) trail[i] = trail[i - 1];
        trail[0] = playerX;

        int newLevel = 1 + tick / 700;
        if (newLevel != level) {
            level = newLevel;
            String[] msgs = {"", "", "LEVEL 2: Manager: 'Quick small change?'",
                    "LEVEL 3: Client changed the requirements. URGENT bugs incoming!",
                    "LEVEL 4: Intern pushed to main.", "LEVEL 5: 'Can we do it by EOD?'"};
            banner = level < msgs.length ? msgs[level] : "LEVEL " + level + ": Legacy code awakens.";
            bannerTicks = 140;
            flash = 8;
            Sfx.level();
        }

        if (tick % 6 == 0) score += coffeeTicks > 0 ? 2 : 1;
        if (tick == 1800 && hits == 0) unlock("Untouchable", "30 seconds, zero bugs");

        // boss / normal spawning
        if (!bossActive && tick >= nextBoss) startBoss();
        if (bossActive) {
            bossTicks++;
            bossX = W / 2.0 + Math.sin(bossTicks * 0.025) * 320;
            bossY = bossTicks < 25 ? -80 + bossTicks * 5.7 : bossTicks > 690 ? 62 - (bossTicks - 690) * 4 : 62;
            if (bossTicks > 90 && bossTicks < 660 && --bossAttackTimer <= 0) {
                bossAttack();
                bossAttackTimer = Math.max(22, 48 - level * 3);
            }
            if (--helpTimer <= 0) {
                spawnHelp();
                helpTimer = 200;
            }
            if (bossTicks >= 720) endBoss();
        } else {
            if (--spawnTimer <= 0) {
                spawn();
                spawnTimer = Math.max(12, 46 - level * 5) + rnd.nextInt(10);
            }
            if (tick % 1100 == 700) spawnWave();
        }

        double dt = slowTicks > 0 ? 0.45 : 1.0;
        Rectangle2D.Double pr = new Rectangle2D.Double(playerX - 42, playerY, 84, 56);
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            it.y += it.vy * dt;
            it.x += it.vx * dt;
            it.wob += 0.12 * dt;
            double ix = itemX(it);
            Rectangle2D.Double r = new Rectangle2D.Double(ix - it.w / 2.0, it.y, it.w, 28);
            if (r.intersects(pr)) {
                collect(it, ix);
                items.remove(i);
            } else {
                if (it.type == 0 && !it.passed && it.y > playerY + 58) {
                    it.passed = true;
                    double gap = Math.abs(ix - playerX) - it.w / 2.0 - 42;
                    if (gap < 45) closeCall(ix);
                }
                if (it.y > H + 20 || it.x < -150 || it.x > W + 150) {
                    if (it.type == 0 && it.y > H + 20) score += 1;
                    items.remove(i);
                }
            }
        }
        updateEffects();
    }

    void closeCall(double ix) {
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        int g = add(5);
        pop("CLOSE CALL! +" + g, ix, playerY - 25, new Color(255, 225, 90));
        Sfx.close();
        if (combo == 10) unlock("Flow State", "10 close calls in a row");
    }

    void collect(Item it, double ix) {
        switch (it.type) {
            case 0:
                if (shieldTicks > 0) {
                    burst(ix, it.y, new Color(80, 200, 255), 25);
                    pop("BLOCKED! StackOverflow saves again", ix, it.y, new Color(80, 200, 255));
                    add(5);
                    Sfx.shield();
                } else {
                    lives--;
                    hits++;
                    combo = 0;
                    shake = 20;
                    hurt = 30;
                    burst(ix, it.y, new Color(255, 80, 80), 45);
                    pop(it.reaction, ix, it.y, new Color(255, 110, 110));
                    Sfx.hit();
                    unlock("Welcome to Production", "Got hit for the first time");
                    if (lives <= 0) gameOver();
                }
                break;
            case 1:
                coffeeTicks = 360;
                coffees++;
                add(10);
                burst(ix, it.y, new Color(200, 140, 70), 30);
                pop("+Coffee: Productivity x2", ix, it.y, new Color(255, 200, 120));
                Sfx.coffee();
                if (coffees == 3) unlock("Caffeinated", "3 coffees. Heart rate: 140");
                break;
            case 2:
                shieldTicks = 360;
                burst(ix, it.y, new Color(80, 200, 255), 30);
                pop("Ctrl+C, Ctrl+V: Shield ON", ix, it.y, new Color(120, 220, 255));
                Sfx.shield();
                break;
            case 3:
                lives = Math.min(5, lives + 1);
                burst(ix, it.y, new Color(90, 255, 140), 35);
                pop("LGTM! +1 life", ix, it.y, new Color(120, 255, 160));
                Sfx.lgtm();
                break;
            default:
                slowTicks = 300;
                burst(ix, it.y, new Color(190, 130, 255), 30);
                pop("git revert HEAD: time slows down", ix, it.y, new Color(200, 150, 255));
                Sfx.revert();
        }
    }

    void updateEffects() {
        if (shake > 0) shake--;
        if (hurt > 0) hurt--;
        if (flash > 0) flash--;
        if (shieldTicks > 0) shieldTicks--;
        if (coffeeTicks > 0) coffeeTicks--;
        if (slowTicks > 0) slowTicks--;
        if (bannerTicks > 0) bannerTicks--;
        if (toastTicks > 0) toastTicks--;
        if (bossSayTicks > 0) bossSayTicks--;
        for (int i = parts.size() - 1; i >= 0; i--) {
            Particle p = parts.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.18;
            if (--p.life <= 0) parts.remove(i);
        }
        for (int i = pops.size() - 1; i >= 0; i--) {
            Pop p = pops.get(i);
            p.y -= 0.8;
            if (--p.life <= 0) pops.remove(i);
        }
    }

    // ====================== DRAWING ======================

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // background gradient: gets angrier each level
        int red = Math.min(80, 15 + level * 12);
        g.setPaint(new GradientPaint(0, 0, new Color(10, 10, 30), 0, H, new Color(red, 15, 50)));
        g.fillRect(0, 0, W, H);

        // matrix-style code rain
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(slowTicks > 0 ? new Color(170, 110, 255, 60) : new Color(0, 255, 120, 45));
        for (Rain r : rain) g.drawString(String.valueOf(r.ch), (int) r.x, (int) r.y);

        if (shake > 0) g.translate(rnd.nextInt(shake + 1) - shake / 2, rnd.nextInt(shake + 1) - shake / 2);

        if (state == State.PLAYING && (bossActive || bossY > -60)) drawBoss(g);
        for (Item it : items) drawItem(g, it);
        if (state != State.MENU) drawPlayer(g);

        for (Particle p : parts) {
            float a = Math.max(0f, (float) p.life / p.max);
            g.setColor(new Color(p.c.getRed(), p.c.getGreen(), p.c.getBlue(), (int) (255 * a)));
            g.fill(new Ellipse2D.Double(p.x, p.y, p.size, p.size));
        }

        g.setFont(popFont);
        for (Pop p : pops) {
            int a = Math.min(255, p.life * 6);
            center(g, p.text, (int) Math.max(150, Math.min(W - 150, p.x)), (int) p.y,
                    new Color(p.c.getRed(), p.c.getGreen(), p.c.getBlue(), a));
        }

        if (slowTicks > 0) {
            g.setColor(new Color(130, 90, 255, 38));
            g.fillRect(-20, -20, W + 40, H + 40);
        }
        if (hurt > 0) {
            g.setColor(new Color(255, 0, 0, hurt * 3));
            g.fillRect(-20, -20, W + 40, H + 40);
        }
        if (flash > 0) {
            g.setColor(new Color(255, 255, 255, Math.min(255, flash * 12)));
            g.fillRect(-20, -20, W + 40, H + 40);
        }
        g.dispose();

        Graphics2D h = (Graphics2D) g0.create();
        h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        h.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (state == State.PLAYING) drawHud(h);
        else if (state == State.MENU) drawMenu(h);
        else drawGameOver(h);
        h.dispose();
    }

    void drawBoss(Graphics2D g) {
        int bx = (int) bossX, by = (int) bossY;
        // body + suit
        g.setColor(new Color(40, 40, 62));
        g.fillRoundRect(bx - 55, by + 30, 110, 70, 30, 30);
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[]{bx - 16, bx + 16, bx}, new int[]{by + 33, by + 33, by + 62}, 3);
        g.setColor(new Color(210, 40, 50));
        g.fillPolygon(new int[]{bx - 6, bx + 6, bx + 9, bx - 9}, new int[]{by + 42, by + 42, by + 88, by + 88}, 4);
        // head
        g.setColor(new Color(240, 190, 150));
        g.fillOval(bx - 38, by - 38, 76, 76);
        g.setColor(new Color(150, 150, 160));
        g.fillArc(bx - 40, by - 42, 80, 50, 0, 180);
        // eyes follow the player
        double dx = Math.max(-3, Math.min(3, (playerX - bx) / 80));
        g.setColor(Color.WHITE);
        g.fillOval(bx - 26, by - 8, 20, 16);
        g.fillOval(bx + 6, by - 8, 20, 16);
        g.setColor(Color.BLACK);
        g.fillOval((int) (bx - 20 + dx), by - 5, 8, 10);
        g.fillOval((int) (bx + 12 + dx), by - 5, 8, 10);
        // angry brows
        g.setStroke(new BasicStroke(5f));
        g.setColor(new Color(90, 70, 60));
        g.drawLine(bx - 30, by - 20, bx - 6, by - 10);
        g.drawLine(bx + 30, by - 20, bx + 6, by - 10);
        // mouth
        g.setColor(new Color(110, 30, 30));
        if (bossSayTicks > 0 && (tick / 6) % 2 == 0) g.fillRoundRect(bx - 14, by + 14, 28, 14, 8, 8);
        else g.fillRoundRect(bx - 14, by + 18, 28, 6, 6, 6);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        center(g, "MANAGER", bx + 24, by + 92, Color.WHITE);
        // speech bubble
        if (bossSayTicks > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            int bw = g.getFontMetrics().stringWidth(bossSay) + 24;
            int bubx = bx + 60 + bw < W ? bx + 60 : bx - 60 - bw;
            int buby = by - 34;
            g.setColor(Color.WHITE);
            g.fillRoundRect(bubx, buby, bw, 30, 14, 14);
            center(g, bossSay, bubx + bw / 2, buby + 20, new Color(30, 30, 40));
        }
    }

    void drawItem(Graphics2D g, Item it) {
        double ix = itemX(it);
        Color c;
        switch (it.type) {
            case 0: c = it.fast ? new Color(255, 140, 30) : new Color(220, 50, 70); break;
            case 1: c = new Color(150, 95, 45); break;
            case 2: c = new Color(40, 140, 220); break;
            case 3: c = new Color(40, 170, 90); break;
            default: c = new Color(140, 85, 220);
        }
        RoundRectangle2D.Double pill = new RoundRectangle2D.Double(ix - it.w / 2.0, it.y, it.w, 28, 28, 28);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
        g.fill(new RoundRectangle2D.Double(ix - it.w / 2.0 - 5, it.y - 5, it.w + 10, 38, 38, 38)); // glow
        g.setColor(c);
        g.fill(pill);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(pill);
        if (it.type == 0) { // little bug legs + antennae
            g.setColor(c.brighter());
            for (int k = -1; k <= 1; k += 2) {
                g.drawLine((int) ix + k * (it.w / 2 - 12), (int) it.y + 26, (int) ix + k * (it.w / 2 - 4), (int) it.y + 36);
                g.drawLine((int) ix + k * 8, (int) it.y, (int) ix + k * 16, (int) it.y - 10);
            }
        }
        g.setFont(itemFont);
        center(g, it.label, (int) ix, (int) it.y + 19, Color.WHITE);
    }

    void drawPlayer(Graphics2D g) {
        int x = (int) playerX, y = playerY;
        boolean aura = combo >= 10;
        if (state == State.PLAYING && (coffeeTicks > 0 || aura)) { // speed trail
            for (int i = trail.length - 1; i >= 1; i--) {
                g.setColor(aura ? new Color(255, 215, 80, 14 + (8 - i) * 4) : new Color(255, 170, 70, 14 + (8 - i) * 4));
                g.fillOval((int) trail[i] - 22, y - 12, 44, 44);
                g.fillRoundRect((int) trail[i] - 28, y + 22, 56, 36, 14, 14);
            }
        }
        if (aura && state == State.PLAYING) {
            g.setColor(new Color(255, 215, 80, 60 + (int) (30 * Math.sin(tick * 0.3))));
            g.fillOval(x - 70, y - 35, 140, 125);
        }
        if (shieldTicks > 0) {
            float a = shieldTicks < 90 && (tick / 4) % 2 == 0 ? 0.12f : 0.35f;
            g.setColor(new Color(0.3f, 0.8f, 1f, a));
            g.fillOval(x - 62, y - 28, 124, 110);
            g.setColor(new Color(120, 220, 255));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(x - 62, y - 28, 124, 110);
        }
        // body
        g.setColor(new Color(60, 70, 150));
        g.fillRoundRect(x - 28, y + 22, 56, 36, 14, 14);
        // laptop
        g.setColor(new Color(200, 205, 215));
        g.fillRoundRect(x - 40, y + 38, 80, 16, 6, 6);
        g.setColor(new Color(30, 30, 40));
        g.fillRect(x - 32, y + 40, 64, 8);
        // head
        g.setColor(new Color(255, 205, 160));
        g.fillOval(x - 22, y - 12, 44, 44);
        // hair
        g.setColor(new Color(50, 35, 25));
        g.fillArc(x - 23, y - 15, 46, 30, 0, 180);
        // sunglasses
        g.setColor(Color.BLACK);
        g.fillRoundRect(x - 19, y + 4, 17, 10, 6, 6);
        g.fillRoundRect(x + 2, y + 4, 17, 10, 6, 6);
        g.fillRect(x - 3, y + 7, 6, 3);
        g.setColor(new Color(255, 255, 255, 140));
        g.drawLine(x - 16, y + 6, x - 11, y + 6);
        g.drawLine(x + 5, y + 6, x + 10, y + 6);
        // mouth
        g.setColor(new Color(120, 40, 40));
        g.setStroke(new BasicStroke(2f));
        if (hurt > 0) g.drawOval(x - 5, y + 20, 10, 9);
        else g.drawArc(x - 8, y + 14, 16, 12, 200, 140);
        // coffee mug when boosted
        if (coffeeTicks > 0) {
            g.setColor(Color.WHITE);
            g.fillRoundRect(x + 30, y + 24, 16, 18, 4, 4);
            g.setColor(new Color(120, 70, 30));
            g.fillRect(x + 32, y + 26, 12, 5);
            g.setColor(Color.WHITE);
            g.drawArc(x + 42, y + 28, 8, 9, -90, 180);
        }
    }

    void drawHud(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        g.drawString("Score: " + score, 20, 34);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(new Color(255, 255, 255, 170));
        g.drawString("Best: " + best + "   Level " + level + "   Time " + (tick / 60) + "s", 20, 54);

        if (combo > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(combo >= 10 ? new Color(255, 215, 80) : new Color(255, 235, 140));
            g.drawString("COMBO x" + mult() + "  (" + combo + " close calls)", 20, 78);
        }

        for (int i = 0; i < lives; i++) heart(g, W - 40 - i * 32, 16, 24);

        int by = 52;
        if (coffeeTicks > 0) { bar(g, W - 190, by, 150, coffeeTicks / 360.0, new Color(200, 140, 70), "COFFEE"); by += 20; }
        if (shieldTicks > 0) { bar(g, W - 190, by, 150, shieldTicks / 360.0, new Color(80, 200, 255), "SHIELD"); by += 20; }
        if (slowTicks > 0) bar(g, W - 190, by, 150, slowTicks / 300.0, new Color(190, 130, 255), "SLOW-MO");

        if (bossActive) {
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(255, 120, 120));
            g.drawString("MANAGER'S PATIENCE", W / 2 - 210, H - 12);
            g.setColor(new Color(255, 255, 255, 40));
            g.fillRoundRect(W / 2 - 60, H - 22, 270, 10, 10, 10);
            g.setColor(new Color(255, 90, 90));
            g.fillRoundRect(W / 2 - 60, H - 22, (int) (270 * (1 - bossTicks / 720.0)), 10, 10, 10);
        } else {
            g.setFont(new Font("SansSerif", Font.ITALIC, 14));
            center(g, TICKER[(tick / 300) % TICKER.length], W / 2, H - 14, new Color(255, 255, 255, 150));
        }

        if (bannerTicks > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            int a = Math.min(255, bannerTicks * 8);
            center(g, banner, W / 2, 190, new Color(255, 235, 90, a));
        }

        if (toastTicks > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            int tw = g.getFontMetrics().stringWidth(toast) + 30;
            int a = Math.min(255, toastTicks * 6);
            g.setColor(new Color(20, 20, 30, Math.min(220, a)));
            g.fillRoundRect(20, 96, tw, 36, 16, 16);
            g.setColor(new Color(255, 215, 80, a));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(20, 96, tw, 36, 16, 16);
            g.drawString(toast, 35, 119);
        }
    }

    void heart(Graphics2D g, int x, int y, int s) {
        g.setColor(new Color(255, 70, 100));
        g.fillOval(x, y, s / 2 + 1, s / 2 + 1);
        g.fillOval(x + s / 2 - 1, y, s / 2 + 1, s / 2 + 1);
        g.fillPolygon(new int[]{x, x + s, x + s / 2}, new int[]{y + s / 3, y + s / 3, y + s}, 3);
    }

    void bar(Graphics2D g, int x, int y, int w, double frac, Color c, String label) {
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRoundRect(x, y, w, 12, 12, 12);
        g.setColor(c);
        g.fillRoundRect(x, y, (int) (w * frac), 12, 12, 12);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(Color.WHITE);
        g.drawString(label, x - 58, y + 10);
    }

    void drawMenu(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, W, H);
        double bob = Math.sin(System.currentTimeMillis() / 350.0) * 6;
        Color rainbow = Color.getHSBColor((System.currentTimeMillis() % 4000) / 4000f, 0.55f, 1f);
        g.setFont(new Font("SansSerif", Font.BOLD, 54));
        center(g, "FRIDAY 5:59 PM", W / 2, (int) (170 + bob), new Color(255, 235, 90));
        center(g, "DEPLOY SURVIVOR", W / 2, (int) (230 + bob), rainbow);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        center(g, "Dodge the bugs. Beat the Manager. Keep your job.", W / 2, 285, Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        center(g, "Move: Mouse or Arrow keys / A D", W / 2, 325, new Color(255, 255, 255, 190));
        center(g, "Near misses build combos.  P = screenshot   M = mute", W / 2, 350, new Color(255, 255, 255, 190));
        center(g, "Best score: " + best, W / 2, 385, new Color(255, 215, 80));
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            center(g, "Click or press ENTER to deploy to production", W / 2, 440, new Color(120, 255, 160));
        }
        g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        center(g, AUTHOR, W / 2, H - 30, new Color(255, 255, 255, 140));
    }

    void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, 0, W, H);
        g.setFont(new Font("SansSerif", Font.BOLD, 64));
        center(g, "YOU'RE FIRED", W / 2, 170, new Color(255, 80, 90));
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        center(g, firedMsg, W / 2, 222, Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        center(g, "Score: " + score + (score >= best && score > 0 ? "   NEW BEST!" : "     Best: " + best),
                W / 2, 290, new Color(255, 235, 90));
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        center(g, "Survived " + (tick / 60) + "s   |   Best combo x" + bestCombo + "   |   Managers beaten: " + bossesBeaten,
                W / 2, 330, new Color(255, 255, 255, 200));
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            center(g, "Press ENTER to beg for your job back", W / 2, 400, new Color(120, 255, 160));
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        center(g, "P = save screenshot     C = copy LinkedIn share text", W / 2, 440, new Color(255, 255, 255, 170));
    }

    void center(Graphics2D g, String s, int cx, int y, Color c) {
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    // ====================== SHARE + SCREENSHOT ======================

    void copyShareText() {
        String text = "I survived " + (tick / 60) + "s and scored " + score
                + " in Friday 5:59 PM Deploy Survivor, a game I built in pure Java. "
                + "Best combo: x" + bestCombo + ". Can you beat me? #Java #BuildInPublic";
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            pop("Share text copied! Paste it in LinkedIn", W / 2.0, H / 2.0 + 120, new Color(120, 255, 160));
        } catch (Exception ex) {
            pop("Could not access clipboard", W / 2.0, H / 2.0 + 120, Color.RED);
        }
    }

    void screenshot() {
        try {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            paint(g);
            g.dispose();
            File f = new File("screenshot_" + System.currentTimeMillis() + ".png");
            ImageIO.write(img, "png", f);
            pop("Screenshot saved: " + f.getName(), W / 2.0, H / 2.0 + 150, Color.WHITE);
        } catch (Exception ex) {
            pop("Screenshot failed :(", W / 2.0, H / 2.0, Color.RED);
        }
    }

    // ====================== INPUT ======================

    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = true;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = true;
        if ((k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) && state != State.PLAYING) startGame();
        if (k == KeyEvent.VK_P) screenshot();
        if (k == KeyEvent.VK_M) Sfx.muted = !Sfx.muted;
        if (k == KeyEvent.VK_C && state == State.OVER) copyShareText();
    }

    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = false;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = false;
    }

    @Override public void mouseMoved(MouseEvent e) { targetX = e.getX(); }
    @Override public void mouseDragged(MouseEvent e) { targetX = e.getX(); }
    @Override public void mousePressed(MouseEvent e) { if (state != State.PLAYING) startGame(); }
    @Override public void keyTyped(KeyEvent e) { }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Friday Deploy Survivor");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            FridayDeploySurvivor game = new FridayDeploySurvivor();
            f.setContentPane(game);
            f.pack();
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
