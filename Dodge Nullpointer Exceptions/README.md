# Friday 5:59 PM Deploy Survivor (v2)

A funny Java Swing game. You are a developer deploying on a Friday. Dodge bugs, beat THE MANAGER, chain close calls for huge combos.

## Run

```bash
javac FridayDeploySurvivor.java
java FridayDeploySurvivor
```

Needs JDK 11 or newer. No libraries, one file.

## Controls

| Key | Action |
|-----|--------|
| Mouse or Left/Right (A/D) | Move |
| Enter / Click | Start / retry |
| P | Save a screenshot (PNG) in the project folder |
| C | (Game over screen) copy a ready-made LinkedIn share text |
| M | Mute / unmute sound |

## What's new in v2

- **Boss fight**: THE MANAGER drops in every ~50 seconds and throws "Small feature", "Quick call?", "Per my last email" at you. Survive his patience bar for +200.
- **Combo system**: pass a bug by a hair (close call) to build a multiplier up to x8. Get hit and it resets.
- **Sprint Planning Wave**: a wall of bugs with a gap. Find the gap.
- **URGENT!!! bugs** from level 3: fast, orange, straight down.
- **git revert HEAD** power-up: slow motion for 5 seconds.
- **Achievements** pop up (Welcome to Production, Caffeinated, Untouchable, Flow State, Boss Slayer).
- **Sound effects** generated in code (no files). Silent automatically if no audio device.
- **Best score saved** to `highscore.txt`.

## Pickups

- **Coffee**: speed boost and double passive score
- **Copied from StackOverflow**: shield that blocks bugs
- **LGTM! (Code Review)**: +1 life
- **git revert HEAD**: slow motion

## Before you record your video

1. Change `AUTHOR` at the top of the file to your name.
2. Record 30-60 seconds. Let the boss fight and one hit (screen shake + red flash) happen on camera.
3. On the game over screen press **P** for a thumbnail and **C** for caption text.

## LinkedIn post idea

> I built a game where you dodge NullPointerExceptions while deploying on a Friday.
> Pure Java, no libraries, one file. Level 2 introduces the Manager boss, and he says "Can we add one small feature?"
> What's your high score? 👇
> #Java #BuildInPublic #Programming
