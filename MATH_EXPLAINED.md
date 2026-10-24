# An explainer of the math used in Tideo

Tideo exposes a lot of numeric settings, most of them are coefficients in formulas. This guide
explains what each one does, how it changes your screen's behaviour, and how to tune it without
requiring that you become a math wizard.

You don't have to read the formulas. Every section starts with a simple English explanation,
followed by a table showing what changes when you turn the setting up or down. The formulas are
in grey boxes for anyone who wants them.

Unless an example says otherwise, its numbers are calculated from Tideo's code (version 1.15.0)
with the app's initial settings. The app rounds some intermediate results, so a simplified
formula here can differ from Live Debug in the last digit.

**Where to start.** First decide which kind of problem you have:

- **The brightness is wrong.** Once the light has been steady for a while and the screen has
  stopped changing, it's too bright or too dim. Change the curve or its limits (§3–§4).
- **The brightness is right, but the changes are annoying.** The screen ends up at the correct
  level, but its reacts either too often or too little, takes long to get tot the target brightness, 
  or the animations look jumpy. Change reactivity, smoothing or animation (§5–§7).

The [cheat sheet](#13-symptom--setting-cheat-sheet) maps common issues to the settings that can be tweaked.

> The examples use the app's initial settings, which since 1.14.0 are exactly the built-in *Default* 
> profile: Scale 1, Offset 0, Min 10, Max 255, circadian scaling, software dimming and 
> the bright-light hand-off off, Curve mid 4, 50 animation steps with 5–30 ms waits. 

---

## Contents

1. [From light sensor to screen, step by step](#1-from-light-sensor-to-screen-step-by-step)
2. [Three ideas from vision science](#2-three-ideas-from-vision-science)
3. [The brightness curve: the three-zone model](#3-the-brightness-curve-the-three-zone-model)
4. [Final adjustments: Scale, Offset, Min and Max](#4-final-adjustments-scale-offset-min-and-max)
5. [Reactivity: the dead zone](#5-reactivity-the-dead-zone)
6. [Smoothing: the adaptive EMA and Smoothing Δ](#6-smoothing-the-adaptive-ema-and-smoothing-δ)
7. [Animation and throttle](#7-animation-and-throttle)
8. [Circadian scaling](#8-circadian-scaling)
9. [Super dimming and PWM mode](#9-super-dimming-and-pwm-mode)
10. [Bright-light hand-off to Android](#10-bright-light-hand-off-to-android)
11. [Manual overrides and the curve wizard](#11-manual-overrides-and-the-curve-wizard)
12. [Built-in profiles](#12-built-in-profiles)
13. [Symptom → setting cheat sheet](#13-symptom--setting-cheat-sheet)
14. [Reference table of all settings](#14-reference-table-of-all-settings)
15. [Where to find it in the code](#15-where-to-find-it-in-the-code)

---

## 1. Steps from ambient light sensor to screen brightness

Every reading from the light sensor goes through the same chain of steps, and each step has its
own settings screen:

```
 light sensor (raw lux)
      │
      ▼
 ① Admission — is the reading outside the stored lux band?     (Reactivity screen)
      │  no → skip it; nothing is recalculated
      ▼  yes
 ② Reactivity check — big enough change vs smoothed lux?       (Reactivity screen)
      │  no → keep the current brightness (the band is refreshed)
      ▼  yes
 ③ Smoothing — how far towards the new reading do we move?     (Reactivity screen, "Smoothing Δ")
      │
      ▼  "smoothed lux"
 ④ Hand-off (off by default) — bright enough for Android?      (Hand-off Behavior screen)
      │  yes → Android's Adaptive Brightness sets the screen; Tideo writes nothing
      ▼  no
 ⑤ Brightness curve — lux → brightness (three zones)           (Curve & Brightness screen)
      │
      ▼
 ⑥ Scaling — Scale/Offset, or circadian scaling + taper        (Misc / Circadian screens)
      │
      ▼
 ⑦ Round to a whole number, clamp to Min … Max brightness      (Misc screen)
      │
      ▼
 ⑧ Animate from the old brightness to the new one              (Misc screen)
      │
      ▼
 ⑨ Below the threshold: super dimming / PWM software dimming   (Super Dimming screen)
```

Three values appear throughout this document, good to know what’s what:

- **Raw lux** is the latest light level the ambient light sensor reported. Rough examples: a dark bedroom is
  0–5 lux, an evening living room 50–150, an office 300–500, an overcast day 1,000–10,000, and
  direct sun 30,000–100,000 or more. Sensor position, shadows and the phone's calibration all
  shift these numbers, so treat them as examples rather than boundaries.
- **Smoothed lux** is the light level Tideo actually feeds into the curve. It can lag behind raw
  lux while Tideo follows a change (§6).
- **Brightness** is Tideo's internal control value from **0 to 255**. It isn't a percentage of
  your screen's light output or a value in nits. Your display decides how much light each value
  produces, and Android's slider and stored brightness setting may use a different scale. The 
  values can only go above 255 when Android is in control (§10).

Some events take a different path: start-up, resume, applying settings, and a manual override.
While a brightness transition is running, Tideo doesn't start a second one alongside it. 
It keeps only the **newest** reading waiting and checks that reading once the transition and its cooldown end
(§7). If lux smoothing hasn't finished, Tideo keeps stepping towards the latest reading even when the
sensor stops reporting (§6.4).

---

## 2. Three ideas from vision science

Tideo's formulas follow three rules about light perception. These explain why
the settings work in percentages, on logarithmic scales and with (cubic-ish) roots, instead of using simple
straight lines.

### 2.1 Weber's law: we notice *relative* changes, not absolute ones

Ernst Weber found that the smallest change in a stimulus that a person can perceive is
a **fixed fraction** of the stimulus itself. Going from 10 to 20 lux is a very noticable change. The
same +10 lux increase at 10,000 lux not so much. What matters is the **fold-change**.

**Where Tideo uses it:** all reactivity thresholds are percentages, known as *Weber fractions*.
Tideo measures a light change relative to the current smoothed lux level:

```
relative change = |new lux − smoothed lux| / (smoothed lux + 1)
```

The `+ 1` prevents division by 0 in absolute darkness. 

The human eye can easily detect differences in light intensity of a few percent. Tideo's default thresholds (8–30 %) are 
much larger on purpose: the goal is to ignore sensor noise and react only to changes that are worth adjusting
the screen for.

### 2.2 Fechner's law: perception follows a logarithmic scale

Gustav Fechner built on Weber's result and concluded that perceived intensity grows with the
**logarithm** of the physical intensity. Each *multiplication* of the light, such as ×10, feels
like a similar-sized step. So 1 → 10 → 100 → 1,000 → 10,000 lux feel like roughly evenly spaced
steps, even though each one is ten times the previous.

**Where Tideo uses it:**

- All *graphs* use a logarithmic x-axis to transform the lux scaling.  
- **Curve mid (log lux)** on the Reactivity screen uses a logarithmic scale: 2 = 100 lux,
  3 = 1,000 lux, 4 = 10,000 lux, 5 = 100,000 lux.
- The reactivity threshold changes smoothly from "dim" to "bright" along this log scale (see §5).
- The curve wizard sorts your data into lux **decades** (<10, 10–100, 100–1k, 1k–10k, ≥10k) so
  that every range of light is represented (see §11).

### 2.3 Stevens' power law: perceived brightness ≈ light^exponent

In the 1950s S. S. Stevens replaced Fechner's logarithm with a **power law**:

```
perceived magnitude ∝ (physical intensity) ^ a
```

For brightness, Stevens measured an exponent of about **0.33** (roughly a cube root) for a large
patch of light viewed in the dark, and about **0.5** (a square root) for a point of light or a
brief flash. With either exponent, perception is strongly *compressive*: you need about 8× more
light (cube root) or 4× more light (square root) before something looks twice as bright.

**Where Tideo uses it:** the brightness curve uses the same two exponents:

| Zone | Light range (defaults) | Shape                          | Exponent |
|------|------------------------|--------------------------------|----------|
| 1    | 0 – 35 lux             | **square root** (√lux)         | 0.5      |
| 2    | 35 – 10,000 lux        | **≈ cube root** (lux^0.33)     | 0.33     |
| 3    | above 10,000 lux       | smooth approach to Max         | —        |

These exponents weren't copied from the literature. They were found while hands-on testing during the
early development of AAB (the Tasker project Tideo recreates) and only later turned out to match
the values Stevens measured; which in a **very non-scientific manner** validates that work. 

Stevens' question was slightly different from Tideo's. He wanted to know how bright a light *looks*.
Tideo wants to know which screen setting looks right *for the light around you*. The curve's output is a
control value, not perceived brightness, and every display turns those values into light in its
own way. The fitted exponents therefore combine three things: how perception compresses light,
how your eyes adapt to the room, and how the panel responds. 

> These laws approximate human vision, and Tideo doesn't try to model your eyes. It uses their
> *shapes* because those shapes feel natural, and the settings let you bend them to suit your
> eyes and your display.

---

## 3. The brightness curve: the three-zone model

*Screen: **Curve & Brightness***

The curve converts **smoothed lux** into a **base brightness**. It has three pieces that join
without gaps. In mathematical terms the curve is *C0-continuous*: the end of one zone is exactly the start
of the next. The pieces can still have different *slopes*, which shows as a kink in the graph (i.e. the graphs are not C1-continuous).

### 3.1 The formulas

```
Zone 1   (lux < Zone 1 end):
    brightness = form1A × √lux

Zone 2   (Zone 1 end ≤ lux < Zone 2 end):
    brightness = form2A + form2B × [ (lux − form2C)^0.33 − (Zone1End − form2C)^0.33 ]

Zone 3   (lux ≥ Zone 2 end):
    brightness = Max × (1 − form3A / lux)
```

`form2A` and `form3A` aren’t user settings. Tideo calculates them so the zones join exactly:

```
form2A = form1A × √Zone1End                                ← zone 2 starts where zone 1 ends
form3A = Zone2End × (Max − brightness at Zone2End) / Max   ← zone 3 starts where zone 2 ends
```

They are shown under **Derived (continuity)** as *Zone 2 alignment (form2A)* and *Zone 3
alignment (form3A)*. You can't edit them, but they turn red when your other settings are
inconsistent (see §3.6).

The exponent is exactly **0.33**, not one third, which reproduces the original Tasker project. A
true cube root gives slightly different numbers. At exactly 0 lux the curve gives 0 regardless of
form1A’s value.

### 3.2 What the default curve looks like

| Lux (example)            | Zone | Base brightness | Final target (app defaults) |
|--------------------------|:----:|----------------:|----------------------------:|
| 0                        | 1    | 0.0             | 10                          |
| 1 (very dark room)       | 1    | 5.0             | 10                          |
| 4                        | 1    | 10.0            | 10                          |
| 10 (night light)         | 1    | 15.8            | 16                          |
| 35 (= Zone 1 end)        | 1→2  | 29.6            | 30                          |
| 100 (living room)        | 2    | 44.8            | 45                          |
| 300                      | 2    | 63.8            | 64                          |
| 1,000 (bright office)    | 2    | 92.6            | 93                          |
| 3,000                    | 2    | 130.5           | 130                         |
| 10,000 (= Zone 2 end)    | 2→3  | 190.9           | 191                         |
| 20,000                   | 3    | 223.0           | 223                         |
| 50,000                   | 3    | 242.2           | 242                         |
| 100,000 (direct sun)     | 3    | 248.6           | 249                         |

With the default Min(imum brightness) of 10, everything below about 4 lux ends up at 10. At those levels the minimum
hides the curve, so lowering form1A can make no visible difference.

### 3.3 Zone 1: the dark (square root)

| Setting | Default | What it does |
|---|---|---|
| **Zone 1 scaling (form1A)** | 5.0 | The multiplier for the square root. Brightness in the dark is `form1A × √lux`. |
| **Zone 1 end (lux)** | 35 | The light level where "dark" ends and zone 2 begins. |

**How to think about form1A:** it is the brightness at exactly 1 lux. At 4 lux the brightness
is 2 × form1A, at 9 lux 3 × form1A, at 16 lux 4 × form1A, et cetera, because of the square root.

| form1A | 1 lux | 10 lux | 35 lux | 100 lux | 1,000 lux |
|-------:|------:|-------:|-------:|--------:|----------:|
| 3      | 3.0   | 9.5    | 17.7   | 33.0    | 80.8      |
| **5**  | **5.0** | **15.8** | **29.6** | **44.8** | **92.6** |
| 8      | 8.0   | 25.3   | 47.3   | 62.6    | 110.4     |

Zone 2 starts at the y-value where zone 1 ends, **changing form1A shifts the whole of zone 2
by the same number of brightness steps**. Going from 3 to 5 adds 11.8 steps at 35 lux, at 100 lux
and at 1,000 lux alike. The shift only fades in zone 3, where every curve is pulled towards Max.
form1A therefore affects all zones. After changing it, check your indoor
brightness too, and if indoor light is now wrong, correct it with form2B.

**Tuning:** the in-app recommendation is 1–5. Deviate if your phone screen feels too dim or too bright (the
*Outdoors* profile uses 8 for this parameter). Lower form1A if the screen is too bright in a dim room, and raise it if
the screen is hard to read there. If a dark room is too bright because the target already sits
at Min(imum brightness), lower **Min** instead.

**Zone 1 end:** raising it extends the square-root part further into the low-light range. Because
it also moves where the later zones attach, there is no simple "higher means brighter" rule.
Change it while watching the graph at the light levels you care about.

### 3.4 Zone 2: indoor light (cube root)

| Setting | Default | What it does |
|---|---|---|
| **Zone 2 scaling (form2B)** | 8.8 | How strongly brightness rises across the indoor range. |
| **Zone 2 offset (form2C)** | 18 | Where the cube root is anchored. Shapes the start of zone 2. |
| **Zone 2 end (lux)** | 10,000 | Where indoor light ends and bright outdoor light (zone 3) begins. |

**form2B** works like form1A for the middle of the curve: a higher value means a steeper climb
from dim indoor light to daylight. Its effect grows the further
you go into zone 2. The in-app recommendation is 3–10. Wider values are accepted, but a negative
form2B would make zone 2 *fall* as the light rises.

| form2B | 100 lux | 1,000 lux | 10,000 lux |
|-------:|--------:|----------:|-----------:|
| 6      | 40.0    | 72.6      | 139.6      |
| **8.8** | **44.8** | **92.6** | **190.9**  |

**form2C** is the least intuitive setting to understand. The cube root is calculated from
`lux − form2C`, so form2C chooses the point the cube root "starts" from. Zone 2 always begins at
the height where zone 1 ends, so form2C never causes a jump. What it changes is the shape:

- **Raising form2C towards Zone 1 end** (for example 30 when Zone 1 end is 35) makes zone 2 rise
  *steeply* right after Zone 1 end, and increases all of zone 2. At form2C = 30, 100 lux gives 50.4
  instead of 44.8.
- **Lowering form2C** (for example 0, −100 or −300) makes zone 2 start *gently* and sit lower. At
  form2C = −100, 100 lux gives 35.7 instead of 44.8, and 1,000 lux gives 73.9 instead of 92.6.

form2C **can't be greater than Zone 1 end**; Apply is blocked if it is. Equal is allowed but
cause a very steep start of Zone 2, i.e. by making C1-continuity worse due to slope differences of the two zones. Negative values are fine, because they shift the formula, not the
measured lux. The in-app recommendation is −300 up to Zone 1 end. Reach for form2C after form2B,
when the *shape* of the dark-to-indoor transition still feels wrong.

> **For the curious:** The zones always meet at the same height, but their
> *slopes* can differ, which shows as a kink in the graph at Zone 1 end. The join is
> perfectly smooth when
> `form1A / (2·√Zone1End) = 0.33 · form2B · (Zone1End − form2C)^(−0.67)`.
> The defaults nearly match (0.42 against 0.44, so not quite C1-continuous). If you see a kink you dislike after changing
> form1A or form2B, lower form2C if zone 2 starts too steep, or raise it if zone 2 starts too flat.
> A perfect match is optional: comfort at the light levels you actually use matters more.

**Zone 2 end** chooses where the cube root hands over to the approach towards Max. Moving it
changes both the joining point and the shape above it, and **lowering it is not a reliable
"brighter outdoors" control**. With every other setting at its default, the brightness at
20,000 lux comes out as:

| Zone 2 end | Brightness at 20,000 lux |
|---:|---:|
| 5,000 | 229.6 |
| **10,000** | **223.0** |
| 15,000 | 226.7 |

Both a lower and a higher Zone 2 end come out brighter at 20,000 lux, so check the graph rather
than assuming a direction. The *Outdoors* profile uses 18,000.

### 3.5 Zone 3: bright daylight (approaching Max)

```
brightness = Max × (1 − form3A / lux)
```

Zone 3 has no settings of its own. It starts where Zone 2 ends and asymptotically tends towards
**Max brightness** as lux tends towards positive infinity. Every time the light **doubles**, the remaining gap to Max **halves**. With
the defaults, the gap is 64 at 10,000 lux, 32 at 20,000 lux and 16 at 40,000 lux. If zone 2 ends
exactly at Max, zone 3 is flat at Max.

This means the brightest part of the curve rises steadily instead of sharply. However,
if zone 2 already ends *above* Max, the zone 3 formula tends towards Max **from above**. The final
clamp to Max hides this, but it means the curve has no rising bright end. That is the red form3A
warning and why Tideo doesn’t allow negative form3A values.

### 3.6 Validation: what the red fields mean

| Warning | Meaning | Fix |
|---|---|---|
| **Zone 2 offset must be ≤ zone1End** (blocks Apply) | form2C > Zone 1 end makes the zone 2 formula invalid. | Lower form2C or raise Zone 1 end. |
| **Zone 3 alignment (form3A) red / "Max brightness is too low for this curve"** | Zone 2 already reaches more than Max at Zone 2 end, so zone 3 has no room left. | **Apply on the Misc screen** raises Max to the whole number needed, up to 255. Apply on Curve & Brightness doesn't. If the curve needs more than 255, lower form2B or Zone 2 end. |
| **Zone 2 end must be ≥ zone 1 end** | The zones would overlap. Equal ends leave no zone 2. | Fix the order. |
| **Safety warning: brightness too low at 1000 lux** | The unscaled curve yields less than 25 brightness at 1,000 lux. This is not a measured readability limit for your display, but a “rather safe than sorry” limit . | Raise form1A or form2B if the screen really is too dim indoors. |
| **Scale is very low** | Scale is below 0.5. | Check the screen is still usable, or raise Scale. |

If no warning shows, it doesn't guarantee a curve that makes sense, so check the graph after changing it.

---

## 4. Final adjustments: Scale, Offset, Min and Max

*Screen: **Misc***

After the curve’s base brightness is calculated, it's adjusted and limited in this order:

```
1. Scaling     circadian scaling OFF:  brightness = curve × Scale + Offset
               circadian scaling ON:   brightness = curve × (circadian scale, tapered) + Offset   (see §8)
2. Round       to the nearest whole number
3. Limit       brightness = clamp(brightness, Min, Max)
```

With circadian scaling on, the scaled value is first rounded to one decimal place. A base of
exactly 0 skips scaling entirely and returns Min, so Offset isn't added in that case.

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Scale** | 1.0 | 0.1 – 10 | Multiplies the whole curve: 0.8 gives 20 % lower control values everywhere and 1.15 gives 15 % higher ones (control values, not perceived brightness, §1). Because it multiplies, it moves bright levels by more steps than dark ones. **Not used while circadian scaling is on**: the field then shows "Scale (auto — dynamic)". |
| **Offset** | 0 | −255 – 255 | Adds the same number of brightness steps at every light level. +15 affects the dark screen perception more (10 → 25) and bright light perception less (240 → 255). It applies with or without circadian scaling. |
| **Min brightness** | 10 | 0 – 255 | The lowest target Tideo sets. Below it, differences in the curve disappear. 0 means the lowest control value, not necessarily a black screen. |
| **Max brightness** | 255 | Min – 255 | The highest target Tideo sets. It's also the value zone 3 approaches when lux goes towards infinity, so changing it reshapes the bright end of the curve too. |

**Worked example:** at 100 lux the default curve yields 44.8. With Scale 0.8 and Offset +5 that
becomes 40.8, rounded to **41**. If Min(imum brightness) were 50, the target would be **50** instead. This is why
Scale can seem to do nothing at either end: Min or Max is deciding the result.

**Scale or Offset?** Use **Scale** to make everything proportionally brighter or dimmer. Use
**Offset** to give the dark end a fixed boost (or cut) without changing sunlight much. Neither
changes how sensitive Tideo is to light changes; that is §5.

Min and Max limit Tideo's *target*. In PWM mode the hardware brightness can then be raised to the
PWM threshold, and Extra Dim can make a target of 10 look darker than hardware brightness 10
would on its own (§9).

---

## 5. Reactivity: the dead zone

*Screen: **Reactivity**, "Reactivity thresholds"*

Light sensors are noisy. For each of sensor event, new readings come in: a hand casts a shadow, a lamp flickers or a cloud passes. If Tideo
reacted to every small change, the screen brightness would keep jumping up and down. The **dead zone**
filters this out: Tideo only reacts when the light changes by more than a set **percentage**.
This is Weber's law from §2.1.

### 5.1 Two checks

A sensor reading has to pass two related checks before brightness can change:

1. **The stored lux band** (shown in Live Debug, see §5.4). A reading inside the band is skipped
   without any calculation.
2. **The calculation check.** A reading outside the band is compared with the previous *smoothed*
   lux, and is still rejected if the relative change is below the threshold.

Small changes that pass gate 1 (the band) don't necessarily move the screen. These can still fail the
second check, round to the same brightness, or land on Min or Max.

### 5.2 How the threshold is chosen

The threshold isn't one fixed number. It depends on how bright your surroundings currently are,
and it's built from five settings. The fields take **fractions**: enter 0.25 for 25 %, not 25.

| Setting | Default | Meaning |
|---|---|---|
| **Dark threshold** | 0.30 (= 30 %) | The threshold at 0 lux, in complete darkness. |
| **Dim threshold** | 0.25 (= 25 %) | The threshold at Zone 1 end and through ordinary indoor light. |
| **Bright threshold** | 0.08 (= 8 %) | The threshold that strong daylight approaches. |
| **Curve mid (log lux)** | 4.0 (= 10,000 lux) | The light level halfway between Dim and Bright, on a log scale. |
| **Curve slope** | 2.1 | How abruptly the threshold changes from Dim to Bright around the midpoint. |

```
Below Zone 1 end:   threshold goes linearly (straight line) from Dark (at 0 lux) to Dim (at Zone 1 end)

Above Zone 1 end:   threshold = Dim + (Bright − Dim) / (1 + e^(−slope × (log10(lux+1) − mid)))
                    a sigmoid (S-shaped curve) from Dim to Bright, centred on "Curve mid"

Reject if:          relative change < threshold           (relative change from §2.1)
```

The threshold and the relative change are both rounded to three decimal places. The exact centre
of the S-curve is 10^mid − 1 lux. The two pieces don't quite meet at Zone 1 end: at 35 lux the
S-curve already gives 24.9 % rather than 25 %.

The resulting threshold at different light levels:

| Smoothed lux | Curve mid = 4 (default) | Curve mid = 3 |
|---:|---:|---:|
| 0 | 30.0 % | 30.0 % |
| 10 | 28.6 % | 28.6 % |
| 35 | 24.9 % | 24.2 % |
| 100 | 24.7 % | 23.1 % |
| 1,000 | 23.1 % | 16.5 % |
| 3,000 | 20.7 % | 12.6 % |
| 10,000 | 16.5 % | 9.9 % |
| 100,000 | 9.9 % | 8.3 % |

So with the defaults, ordinary indoor light needs about a **25 %** change before Tideo reacts,
and bright sunlight only about **8–10 %**. Near darkness the `+ 1` in the relative change takes
over. At 0 smoothed lux a 30 % threshold means a change of about **0.3 lux**, instead of 30 % of 0 (which would be still 0).

### 5.3 Tuning the thresholds

| If… | Change |
|---|---|
| Brightness keeps changing in a dark room (sensor noise at very low lux) | Raise **Dark threshold** *or* **Raise Min and add Super Dimming / PWM Sensitive** with a higher exponent to compensate |
| Brightness changes too often indoors, or ignores real small changes | Raise or lower **Dim threshold** |
| Outdoors, shadows from your hand or head cause brightness dips | Raise **Bright threshold** |
| The switch from "indoor" to "outdoor" behaviour happens at the wrong light level | Move **Curve mid**: 3 = 1,000 lux, 4 = 10,000 lux |
| The switch is too gradual or too sudden | **Curve slope**: higher = more sudden. At 2.1, most of the change happens within one decade (×10 lux) either side of the midpoint. |

With the conventional setup (Bright lower than Dim), lowering **Curve mid** makes Tideo more sensitive
from a lower light level upwards. The built-in profiles put Curve mid at their Zone 2 end on the
log scale: 4.0 (10,000 lux), or 4.255 (about 18,000 lux) in *Outdoors*. Tideo doesn't move
Curve mid for you when you change Zone 2 end. If you set Bright *higher* than Dim, that
Flips the curve. If they are equal, Curve mid and Curve slope have no effect.

### 5.4 The band you see in Live Debug

When a reading is processed, Tideo stores a lux band around that **raw** sensor reading. For example,
200 lux with a 25 % threshold gives `150 – 250`. Readings inside the band (i.e. the sensor dead zone) are skipped without any
calculation. That saves work, but it doesn't stop the sensor from reporting.

- The sensor dead zone is refreshed whenever a sensor reading is processed, even when the calculation check then
  rejects it.
- Below 10 lux the dead zone’s edges are rounded to two decimals; otherwise to whole lux.
- Below **0.2 lux** a special case applies: the band is **0 – 0.1 lux** and the stored threshold
  is **1 %**.
- While smoothing is still settling (§6.4), readings can bypass the band so the response can
  finish.

**Trust low-accuracy sensor** (off by default): Android can mark a sensor reading as
"unreliable". Tideo ignores those readings unless this is turned on. Turning it on doesn't make
the readings more accurate or more frequent. Only use it if diagnostics show the filter is
throwing away the readings Tideo needs.

---

## 6. Smoothing: the adaptive EMA and Smoothing Δ

*Screen: **Reactivity**, "Sensor smoothing" → **Smoothing Δ***

### 6.1 What an EMA is

An **exponential moving average (EMA)** is a simple and fast way to smooth a jumpy signal (e.g. compared to a moving average which needs to sample more data points). Each time a
new reading comes in, you move only part of the way from your current value towards it:

```
new smoothed lux = α × sensor reading + (1 − α) × old smoothed lux
```

**α (alpha)** is a number between 0 and 1:

- **α = 1**: jump straight to the new reading (no smoothing at all).
- **α = 0.5**: move halfway towards the new reading.
- **α = 0.1**: move only 10 % of the way, which is very smooth but slow.

A conventional EMA uses a fixed α, which forces a trade-off. Small α’s ignores noise, but
reacts slowly when you walk into a bright room. Large α’s react quickly to light changes, but let noisy readings through.

### 6.2 Tideo's adaptive α

Tideo calculates a **new α for every sensor reading** based on the fold-changes:

```
α = 1 − e^( −SmoothingΔ × (relative change − stored threshold) )
```

In words:

1. Calculate how much the light changed as a percentage (the *relative change* from §2.1).
2. Subtract the dead zone threshold, so only the part of the change *beyond* the dead zone counts.
3. Turn that excess into an α between 0 and 1. A small excess yields a small α (moves smoothed lux a little),
   and a large excess gives an α close to 1 (moves smoothed lux almost all the way).

**Smoothing Δ** (internally *DeltaFactor*, default **1.8**) controls how quickly α rises as the
excess grows. **Higher means faster response and lower means more smoothing.** 

> **Exact details.** The threshold subtracted here is the one *stored* by the previous
> calculation, rounded to a whole percent above 10 lux. It isn't necessarily the threshold the
> calculation check just used. Relative change, excess and α are each rounded to three decimals.
> While the proximity sensor reads "near", α is then multiplied by 0.1 and rounded again (§6.5).
> Smoothed lux is rounded to two decimals below Zone 1 end and to whole lux above. α isn't clamped,
> which matches the Tasker AAB project.

### 6.3 What Smoothing Δ does in numbers

Starting at 100 lux with a 25 % threshold, this is how much of the way Tideo moves (α) it’s smoothed lux when the
light suddenly changes:

| New light | Smoothing Δ 0.5 | 0.8 | **1.8 (default)** | 2.8 | 4.0 |
|---|---:|---:|---:|---:|---:|
| ×1.3 (130 lux) | 0.02 | 0.04 | **0.08** | 0.12 | 0.17 |
| ×1.5 (150 lux) | 0.12 | 0.18 | **0.36** | 0.50 | 0.62 |
| ×2 (200 lux) | 0.31 | 0.45 | **0.74** | 0.87 | 0.95 |
| ×3 (300 lux) | 0.58 | 0.75 | **0.96** | 0.99 | 1.00 |
| ×10 (1,000 lux) | 0.99 | 1.00 | **1.00** | 1.00 | 1.00 |

For the jump to 200 lux at the default, the new smoothed lux value is about **174 lux**, not 200, and
the curve is evaluated at 174 lux.

- **Higher Smoothing Δ** means Tideo follows changes faster. It feels more
  responsive, but can also be more jittery.
- **Lower Smoothing Δ** means Tideo moves in small steps. It feels calmer, but can also be
  laggy/unresponsive.

Very large changes in sensor readings, such as walking from indoors into sunlight, almost always produce α ≈ 1. Smoothing Δ mostly affects **medium-sized** changes, unless you pick extreme values. 

### 6.4 Why brightening is fast and dimming takes several steps

The relative change is measured against the *current* level, so increases and decreases behave
differently:

- Light **rising** has no upper limit on the fold-change. 2 → 300 lux is a change of nearly 10,000 %, so α = 1 and
  Tideo goes there in a single step.
- Light **falling** can never be more than 100 %. So α has a ceiling that depends on the
  threshold. With a 25 % threshold it's about **0.74** at the default Smoothing Δ, and about 0.31
  at Smoothing Δ 0.5.

So when you switch off the lights, Tideo goes down in **several steps**, each one a fraction of
the remaining distance. Here is what happens with the default settings, going from 300 lux to 2 lux:

```
smoothed lux:  300 → 79 → 22.9 → 8.8 → 5.2 → 4.1 → … creeps towards ~3.3 → placed at ~2.6
brightness:     64 → 41 →   24 →  15 →  11 →  10 (Min) …
```

Each step is its own animated transition. Tideo keeps taking these **settling steps** even if
the sensor stops reporting new values, which can happen in a dark room. It stops once smoothed lux
is inside the band around the actual reading. If progress stalls before then, or after 20 steps,
Tideo places it on the nearest edge of that band. That is the "placed at ~2.6" above: the steps
had shrunk to almost nothing at 3.3 lux. Brightness had already reached Min at the fifth step.

The total response time therefore depends on smoothing, animation (§7) and throttle together. If
each animation is quick, but the total time to reach the target brightness is long: raise Smoothing Δ before adding animation steps.

### 6.5 Thresholds or Smoothing Δ?

| Setting | Answers the question… |
|---|---|
| Thresholds (§5) | **Whether** to react to a change |
| Smoothing Δ | **How far** to move once it reacts |

A lower Smoothing Δ can't validate a sensor reading the threshold gate has already rejected, and a higher one
can't make Tideo react to it either.

| Symptom | Try |
|---|---|
| Brightness "hunts" or overshoots under flickering or changing light | Lower Smoothing Δ (1.0–1.5), and/or raise the thresholds |
| Tideo reacts, but takes several noticeable steps to get there | Raise Smoothing Δ (2.5–4) |
| Small light changes trigger constant tiny adjustments | Raise the thresholds rather than Smoothing Δ |
| Watching video and you want brightness to stay put | Low Smoothing Δ (0.5) plus higher thresholds, as in the *Video Streaming* profile |

> **Proximity sensor:** while the proximity sensor reads "near" (for example in a pocket, an accidental pass over the sensor in bright light, or during
> a call), Tideo divides α by 10 before smoothing. Smoothed lux then moves only a tenth as far
> for each sensor reading, and the brightness and its animation follow from that: when the light drops
> from 300 to 2 lux, smoothed lux goes to about 278 instead of 79. Settling steps (§6.4) continue
> in these smaller steps. The **Smoothing α** in Live Debug shows the reduced value that is
> actually used. Once the sensor is uncovered, Tideo checks the latest reading again at the
> normal α.

---

## 7. Animation and throttle

*Screen: **Misc**, "Animation"*

**Smoothing determines the next brightness target; animation decides how to get the screen there.** Tideo
normally moves to a new brightness in evenly spaced **steps**, with a short **wait** after each.
The α from §6 decides how many steps and how long each wait is:

```
steps = round(1 + α × (Animation steps − 1)),  at least 1
wait  = round(Max wait − α × (Max wait − Min wait))        (milliseconds)
animation time  ≈ steps × wait
```

- **Large changes** (α close to 1) use many steps with **short** waits (Min wait).
- **Small changes** (α close to 0) use very few steps with **long** waits (Max wait). For a tiny
  adjustment this is effectively one gentle nudge.
- With **Animation steps** at 0 or 1, every change is a single direct write.

| α | Defaults (50 steps, 5–30 ms) | 20 steps, 25–65 ms (old defaults before 1.14.0) |
|---:|---|---|
| 0 | 1 × 30 ms = 30 ms | 1 × 65 ms = 65 ms |
| 0.25 | 13 × 24 ms = 312 ms | 6 × 55 ms = 330 ms |
| 0.5 | 26 × 18 ms = 468 ms | 11 × 45 ms = 495 ms |
| 1.0 | 50 × 5 ms = 250 ms | 20 × 25 ms = 500 ms |

These are nominal waits. Writing to the display and scheduling add latency. A higher α
doesn't always mean a longer animation: with the defaults, α 1.0 is quicker than α 0.5. Extra
steps can't create brightness levels that don't exist either. A move from 40 to 42 spread over
20 steps just repeats values.

| Setting | Default | What it does |
|---|---|---|
| **Animation steps** | 50 | The most steps a brightness transition can use. More steps can look smoother but take longer at the same wait. 0 or 1 means always write directly (the *Battery Saver* profile uses 1). |
| **Min wait (ms)** | 5 | The wait between steps for the biggest changes. Lower = big changes complete faster. |
| **Max wait (ms)** | 30 | The wait between steps for the smallest changes. Lower = subtle changes complete faster. Must be at least Min wait. |

**Throttle (derived):** after a calculation starts, new sensor readings can't start another one
until the throttle time has passed. The throttle is `steps × wait + 10 ms`, plus the time the
previous cycle actually took if that was 2 seconds or less. When nothing has changed for a while,
it rises to the ceiling, `Animation steps × Max wait + 10 ms`. In practice Tideo waits for the
current brightness transition to finish. Only the **newest** sensor reading is kept, and it's checked
against the current state as soon as the throttle ends. Nothing piles up and the screen doesn't
stutter or “chase ghosts” which would happen due to queuing sensor events. Long animations also make Tideo react to the *next* change a bit later.

---

## 8. Circadian scaling

*Screen: **Circadian***

Circadian scaling makes the screen a little brighter during the day and a little dimmer at
night, using the real sunrise and sunset times for your location. It **replaces** the regular
Scale with a multiplier that changes through the day. Offset
still applies, and Min and Max still limit the result.

### 8.1 Step 1: where are we in the day? ("progress")

Tideo gets dawn, sunrise, sunset and dusk from your location. *Dawn* and *dusk* are civil
twilight, when the sun is 6° below the horizon. From these it builds two transition windows,
widened by the **Transition factor** (default 0.1):

```
day length   = sunset − sunrise
night length = 24 h − day length

morning ramp:  from  dawn    − (night length × factor)   to   sunrise + (day length × factor)
evening ramp:  from  sunset  − (day length × factor)     to   dusk    + (night length × factor)
```

*Progress* is **0 at night**, **1 during the day**, and rises or falls linearly during
the ramps.

**Example:** dawn 05:30, sunrise 06:00, sunset 18:00, dusk 18:30. That's 12 hours of day and
12 hours of night, so factor 0.1 gives 72 minutes on each side:

- the morning ramp runs from **04:18 to 07:12**
- the evening ramp runs from **16:48 to 19:42**

| Transition factor | Effect |
|---|---|
| 0 | The ramps cover only twilight (dawn→sunrise, sunset→dusk). That's quick but still gradual, not a switch. |
| **0.1** | About an hour extra on each side (default). |
| 0.3 | Long, slow ramps that take up much of the morning and evening. |
| > 0.5 | The morning and evening ramps overlap across the middle of the day, and the app warns you. |

The ramps can also meet **overnight** before 0.5, because twilight eats into the night. How soon
depends on the season and your latitude, so check the chart rather than assuming every value up
to 0.5 is safe all year. Near the polar regions, when the sun doesn't rise or set, progress is set to 1
(more than 23 hours of sunlight) or 0 (less than 1 hour of sunlight).

### 8.2 Step 2: shape the ramp into an S-curve ("Scale steepness")

A straight-line ramp would feel mechanical, so Tideo bends it into an S-shape:

```
modifier = tanh((progress − 0.5) × steepness) / tanh(steepness / 2)        → −1 (night) … +1 (day)
```

| Progress through the ramp → | 0 | 10 % | 25 % | 50 % | 75 % | 90 % | 100 % |
|---|---:|---:|---:|---:|---:|---:|---:|
| Steepness 2 (almost a straight line) | −1.00 | −0.87 | −0.61 | 0 | +0.61 | +0.87 | +1.00 |
| **Steepness 6 (default)** | −1.00 | −0.99 | −0.91 | 0 | +0.91 | +0.99 | +1.00 |
| Steepness 12 (almost a switch) | −1.00 | −1.00 | −1.00 | 0 | +1.00 | +1.00 | +1.00 |

**Higher steepness** means Tideo stays at the full night or day value for longer and changes
quickly in the middle of the ramp. **Lower steepness** spreads the change evenly across the
whole ramp.

### 8.3 Step 3: turn it into a brightness multiplier ("Scale spread")

```
circadian scale = 1 + (Scale spread / 100) × modifier
```

With the default **Scale spread of 15 %**, the multiplier is **1.15** in the day, **0.85** at
night and 1.00 halfway through each ramp. Live Debug and the Circadian screen's live card show
this *nominal* multiplier as the **Uncompressed scale**; the taper below decides how much of it a
given brightness gets. The Dashboard's figure (for example "1.15×") is the scale *after* the
taper.

During the morning and evening ramps the Uncompressed scale refreshes every 2 minutes, but the
screen only uses up a new multiplier when Tideo next recalculates brightness (§1), in steady
light it might display values that don’t mach the ramp every minute.

### 8.4 Step 4: the compression taper ("Taper midpoint" and "Taper steepness")

Applying the multiplier evenly everywhere would cause two problems. Bright daytime levels would
be pushed against or past Max, and on a bright sunny screen you would barely notice the
multiplier anyway. So the **taper** gradually reduces the multiplier's effect as the base
brightness gets higher:

```
effect        = 1 / (1 + e^(Taper steepness × (base brightness − Taper midpoint)))   → 1 for dim, 0 for bright
applied scale = 1 + (circadian scale − 1) × effect

then, by day:    applied scale = min(applied scale, Max / base)        never push past Max
     by night:   applied scale = max(applied scale, 2 − Max / base)    the mirror-image limit
```

The night limit is symmetric with the day one: near Max(imum brightness) it also shrinks the night-time cut.
Several intermediate values are rounded to three decimals.

With the defaults (midpoint 190, steepness 0.075), before Offset, rounding and Min/Max:

| Base brightness | Daytime (×1.15) becomes | Night (×0.85) becomes |
|---:|---|---|
| 30 | ×1.150 → 34.5 | ×0.850 → 25.5 |
| 90 | ×1.150 → 103.5 | ×0.850 → 76.5 |
| 150 | ×1.143 → 171.4 | ×0.857 → 128.6 |
| 190 (midpoint) | ×1.075 → 204.3 (half effect) | ×0.925 → 175.8 |
| 230 | ×1.007 → 231.6 | ×0.993 → 228.4 |

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Enable dynamic scaling** | off | — | Turns circadian scaling on. While it's on, the regular **Scale** setting is ignored. |
| **Scale spread** | 15 | 1 – 100 % | Extent of the day/night difference. 15 = ±15 %. Lower it if evening dimming is too strong. |
| **Scale steepness** | 6 | 1 – 20 | How S-shaped the dawn and dusk transitions are (see §8.2). |
| **Transition factor** | 0.1 | 0 – 1 (warns above 0.5) | How far the ramps extend beyond twilight (see §8.1). |
| **Taper midpoint** | 190 | 130 – 240 | The base brightness at which the circadian effect is halved. Flagged if it's above Max while scaling is on. |
| **Taper steepness** | 0.075 | 0.001 – 1 | How quickly the effect fades around the midpoint. At 0.075 it fades over roughly ±40 brightness steps. Very low values spread the fade over the whole range, which weakens the effect even at low brightness. |

**Strength or timing?** Scale spread changes *how much*. Transition factor and Scale steepness
change *when and how gradually*. The taper settings decide *at which brightness* the effect
applies.

**Night Light:** if circadian Night Light is enabled under Privileged Display, its color
temperature follows the same day/night modifier. Color temperature and brightness are separate
outputs.

---

## 9. Super dimming and PWM mode

*Screen: **Super Dimming** (needs elevated access, WRITE_SECURE_SETTINGS)*

Most phones can't get dim enough at their lowest brightness setting. Super dimming uses
Android's **Extra Dim** (also known as Reduce Bright Colors) to darken the image further once the target falls below a threshold.
The Extra Dim level is an Android control value, not a calibrated "amount of light removed":
how dark a given level looks depends on the device. There are two dimming modes.

### 9.1 Super dimming

Once the target falls below the **SD threshold**, Tideo turns Extra Dim on. It's at 0 right at the
threshold and reaches full strength at Min brightness:

```
progress   = (1 − (target − Min) / (SD threshold − Min)) ^ SD exponent         (0 … 1)
strength   = clamp(Strength setpoint × circadian factor, 0, 65)
Extra Dim  = round(strength × progress)                                      (0 = off)
```

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Use super dimming** | off | — | Turns the feature on. |
| **SD threshold** | 15 | 0 – 255 | The brightness below which super dimming starts. It must be **above** Min brightness to do anything. The app only warns when it's *below* Min; equal to Min never engages either, without a warning. |
| **Strength setpoint** | 25 | 0 – 65 | Extra Dim level at Min brightness. Tideo caps it, and its circadian scaled value, at 65. |
| **SD exponent** | 2.5 | 0.5 – 5 | The shape of the fade-in. 1 = straight line. Above 1 = gentle start, with most of the dimming close to Min. Below 1 = more dimming soon after crossing the threshold. |
| **Spread (circadian)** | 100 | −100 – 100 | Only with circadian scaling: how much the time of day changes the strength (see below). |

Example with Min 10, threshold 15 and strength 25, circadian scaling off. These are the Extra Dim levels
Tideo requests after rounding:

| Brightness | 15 | 14 | 13 | 12 | 11 | 10 |
|---|---:|---:|---:|---:|---:|---:|
| Exponent 1 | 0 | 5 | 10 | 15 | 20 | 25 |
| **Exponent 2.5** | **0** | **0** | **3** | **7** | **14** | **25** |
| Exponent 4 | 0 | 0 | 1 | 3 | 10 | 25 |

The lowest values round to 0 and therefore crossing the superdimming threshold doesn't always produce a visible increase in dimming.
A higher exponent makes the middle of the dimming curve *less* dim but keeps the endpoint
the same.

**Spread (circadian)** multiplies the strength by a factor that depends on the time of day:

```
circadian factor = 1 − (Spread / 100) × modifier          (modifier: −1 night … +1 day, see §8.2)
```

- **100 (default):** the factor is 0 in the day, which means *no* super dimming in daylight. At
  night it's 2, which **doubles** the strength (still capped at 65): 25 becomes 50.
- **0:** the time of day has no effect.
- **Negative values:** more super dimming in the day and less at night.

### 9.2 PWM sensitive mode (software dimming)

Many OLED screens **flicker** at low hardware brightness, because they use Pulse Width Modulation (PWM): dim the screen by
cycling on/ off rapidly. Some people get headaches or eye strains from it. PWM mode keeps
the hardware brightness **at or above a floor you choose** and does all the dimming below that
floor with Extra Dim instead. Above the floor, hardware brightness follows the target as usual.

Tideo doesn't measure your screen's PWM. Whether a
higher hardware brightness actually stops the flicker depends on your display, so choose the
hardware brightness floor by looking at the screen.

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Use software dimming (PWM-sensitive)** | off | — | Turns PWM mode on. If super dimming is also on, PWM mode takes priority below the threshold. |
| **PWM threshold** | 15 | 0 – 255 | The hardware floor. This is the **same stored number** as SD threshold (the label changes with the toggled dimming mode), so the two modes can't have different thresholds in the same profile |
| **Software exponent (PWM)** | 0.8 | 0.1 – 3 | How strongly Extra Dim darkens the screen as the target drops below the floor. **Higher = darker** at every target below it. |

In PWM mode, Strength setpoint, SD exponent and Spread (circadian) play no part in the dimming.
Circadian *brightness* scaling can still change the target fed into it.

> **No elevated access?** The hardware floor still applies, but Extra Dim can't be switched on.
> The screen then sits at the floor without getting any darker. If that happens, check the
> WRITE_SECURE_SETTINGS grant before you edit the curve.

```
p     = Software exponent,  T = max(PWM threshold, 1)
k     = 0.05 ^ (1 / p)
bias  = max(k × T / (1 − k), 10)
ratio = min((target + bias) / (T + bias), 1)
Extra Dim = round(99 × (1 − ratio ^ p))
```

The bias depends on both the exponent and the threshold. It keeps the darkest result at or below
99 × 0.95 ≈ **94**, so Tideo never asks for full Extra Dim. Again: better safe than sorry, not a guarantee that your screen will be readable. 

Extra Dim levels for a PWM threshold of 30:

| Target brightness | 0 | 10 | 15 | 20 | 30 |
|---|---:|---:|---:|---:|---:|
| Exponent 0.5 | 50 | 29 | 21 | 13 | 0 |
| **Exponent 0.8** | **66** | **42** | **31** | **20** | **0** |
| Exponent 1.5 | 87 | 64 | 50 | 35 | 0 |
| Exponent 3.0 | 94 | 80 | 67 | 50 | 0 |

Targets below Min only happen if you lower Min. **Set the floor as low as still works for your
eyes:** a higher floor keeps the panel driven harder than the image shows.

---

## 10. Bright light hand-off to Android

*Screen: **Hand-off Behavior** (in the Menu, after Curve & Brightness)*

Many phones can go beyond the normal maximum brightness in direct sunlight, often called *High
Brightness Mode*, but only while Android's own **Adaptive Brightness** is in charge. Tideo's Max
brightness of 255 is the normal, manual maximum, so Tideo on never gets that boost. With **Hand
off to Adaptive Brightness** on, Tideo switches Adaptive Brightness on once the light is bright
enough, stops writing brightness, and takes control back when the light falls again. The hand-off
is off by default and in every built-in profile. Each profile keeps its own hand-off settings, so
a context rule can turn it on outdoors only.

### 10.1 When it hands off and when it takes back control

```
hand off to Android:   smoothed lux > hand-off lux
take back control:     smoothed lux < hand-off lux × hand-back level
```

Both comparisons use **smoothed lux** (§6). Tideo makes this decision only when it reacts to a
reading (§5) or recalculates brightness, for example after Apply. A reading inside the dead zone
decides nothing, so reactivity and smoothing also set how quickly the hand-off happens.

The **hand-back level** leaves a gap between the two points, so light hovering around the
hand-off lux doesn't flip control back and forth. A lower level waits for more darkness; 100 %
takes back control as soon as smoothed lux drops below the hand-off lux. With the default
hand-off lux:

| Hand-back level | Tideo takes back control below |
|---:|---:|
| 50 % | 12,566 lux |
| **80 % (default)** | **20,105.6 lux** |
| 100 % | 25,132 lux |

The Hand-off Behavior screen shows the point where control is taken back rounded down to whole lux.

### 10.2 The lowest valid hand-off lux

Tideo only hands off control at the point where its own curve is already close to the top. The base curve (§3, before
Scale, Offset or circadian scaling) must give at least **90 % of Max brightness** at the hand-off
lux and at every light level above it, up to 100,000 lux:

```
lowest valid hand-off lux = lowest whole lux from which  base brightness ≥ 0.9 × Max  up to 100,000 lux
hand-off lux used         = max(your Hand-off lux, lowest valid hand-off lux)
```

With the default curve, 0.9 × 255 = 229.5, which zone 3 reaches at 10 × form3A = 25,131.5 lux.
That's where the default Hand-off lux of 25,132 comes from. A curve that climbs faster, or has a lower
Max(imum brightness), the minimum hand-off lux is lower: the *Outdoors* curve from 12,756 lux, and *Battery Saver*'s from 8,310 lux
(because 90 % of its Max of 200 is only 180). 

While the hand-off is on, the Hand-off Behavior screen shows the lowest valid value for the
current curve, and the Curve & Brightness graph marks the hand-off point. If your Hand-off lux is
lower, Tideo uses the lowest valid value instead without changing your setting, so your own value
comes back if the curve changes back. If the brightness curve reaches below 90 % of Max at 100,000 lux,
Tideo never hands off.

### 10.3 While Android is in control

- Tideo writes no brightness. Android's own splines decide, so Min, Max,
  Scale, Offset and circadian scaling don't apply. Super dimming and PWM software dimming are
  switched off.
- A brightness change, including your own, isn't treated as an override and is not recorded as a training
  point (§11).
- Tideo keeps measuring light. The Dashboard, notification, widget and Quick Settings tile say
  "Android", and Live Debug keeps showing the target Tideo's own curve would set.
- A message appears at each hand-off and take-back. Pausing Tideo takes control back without one,
  and turning Tideo off restores the brightness mode you had before it started.

**Display range calibration (optional and experimental).** Without it, Tideo shows Android's brightness setting
while Android is in control, and the range stops at 255. The calibration card on the same screen steps
the screen through 13 levels (1, 5, 10, 25, 50, 75, 100, 128, 150, 175, 200, 225 and 255) and
attempts to read, through `dumpsys display`, the hardware value the panel reports for each level. Tideo then turns
a hardware reading back into its own scale:

```
between two measured levels:  a straight line between them
below the first level:        1
above the 255 level:          the last segment's slope, continued past 255
then rounded to a whole number
```

This means a measured value above 255, post-calibration, signals that the phone's High Brightness Mode is pushing the screen past its normal
Maximum; Tideo flashes a toast when that happens. For example, if the calibration
runs in a straight line from hardware 16 at level 1 to 4,095 at level 255, a reading of 4,171
shows as **260**. Calibration needs at least the DUMP permission granted 
and might need to be repeated after a system update.

| Setting | Default | Range | What it does |
|---|---|---|---|
| **Hand off to Adaptive Brightness** | off | — | Turns the hand-off on for this profile. |
| **Hand-off lux** | 25,132 | 1 – 100,000 | Smoothed lux above which Android takes over. While it's below the curve's lowest valid lux, Tideo uses that instead (§10.2). |
| **Hand-back level (percent of hand-off lux)** | 80 | 50 – 100 % | Tideo takes back control once smoothed lux falls below this percentage of the hand-off lux. |

**When to use it:** the screen is already at Max in direct sunlight and still hard to read, and
your phone can get even brighter in sunlight by using Adaptive Brightness. If control
keeps flip flopping between Tideo and Android, reduce the hand-back percentage.

---

## 11. Manual overrides and the curve wizard

*Screens: **Reactivity** ("Use override detection"), **Tools** (curve wizard) and **Curve & Brightness***

The wizard learns from manual overrides of the brightness you prefer for a given smoothed lux level. Recording an override doesn’t automatically change the curve by itself as that would be very opaque. It only adds a data point which the wizard can use when you ask it for a
suggestion.

### 11.1 What gets recorded

With **Use override detection** on, Tideo notices when *something different* than Tideo changes the brightness,
for example manually moving the system slider (except while Android is in control (§10)). It filters
out its own writes and ignores a lasting difference of one step, which can just be device
rounding. When it detects a change, it pauses itself and saves a **training point**:

- **lux** = the *smoothed* lux at that moment
- **brightness** = the value you chose. If circadian scaling was on, it's divided by the last
  *effective* (tapered) circadian scale, so points recorded at different times of day describe
  the same underlying curve. It's capped at 255.

Tideo keeps 50 override points as first-in/first-out, which appear as blue dots on the Curve graph. The override
notification's **Discard** removes the last point that the specific override recorded and resumes automatic
control.

> **Tips for good points.** Only the circadian scale is taken out of the recorded value. **Scale**
> and **Offset** are not. If you collect points while Scale ≠ 1.0 or Offset ≠ 0, the fitted curve
> already includes that adjustment, and it gets applied a second time on top. Keep Scale = 1.0 and
> Offset = 0 while collecting or use circadian scale. Also be precise with the slider, the point is recorded when you
let go of the system slider and no sooner.

### 11.2 How the wizard fits a curve

The wizard searches for the zone boundaries and form1A, form2B and form2C that best match your
points. Then it recalculates form2A and form3A so the zones still join.

- It sorts points into the five lux decades (<10, 10–100, 100–1k, 1k–10k, ≥10k). For each decade
  with *no* real point, it adds a **ghost point** taken from your current curve, at 3, 31, 316,
  3,162 or 15,000 lux. A ghost carries between 0.1 and 0.5 of a real point's weight, rising as you
  collect more points. Ghosts stop the fit going wild there’s no data. The absence of override points
  does not mean it matches your preferences. 
.
- The wizard needs **at least 9 real data points**, preferably spaced across the lux decades. 
- Points in **dimmer** ambient light count more than points in bright light. This is Weber again: a few
  brightness steps make a big difference in a dark room and hardly any in sunlight.
- It tries to keep the curve below Max at Zone 2 end, reducing form2B if necessary to leave room
  for zone 3.

**Best results:** record points in varied situations, with at least one or two in every
decade from dark bedroom to direct sunlight. Compare the suggestion with your dots on the graph before
you apply it and remove outliers by tapping them on the graph prior to running the wizard.

### 11.3 Reading the quality report

The report measures how well the suggested curve matches the data it was fitted to, ghost points
included. It says nothing about light levels you haven't recorded or about comfort on your
display.

| Metric | Meaning | Scale |
|---|---|---|
| **Error (%)** per zone | Weighted root-mean-square distance between the curve and the points, as a percentage of Max (NRMSE). Big misses count extra. 5 % with Max 255 is about 13 brightness steps. | < 1 Excellent, < 3 Very Good, < 7 Good, < 15 Fair, else Poor |
| **Overall Fit** | The average of the three zones' Error (%). | < 2 Excellent, < 4 Very Good, < 8 Good, < 15 Fair, else Poor |
| **Shape (R²)** | How much better the curve does than a flat line at the zone's average. 1.0 = perfect, 0 = no better, and it can go negative. Unreliable with few or very similar points. | ≥ 0.9 Excellent, ≥ 0.75 Good, ≥ 0.4 Moderate, else Poor |
| **Bias** "brighter / dimmer" | The weighted average of (curve − your choice), in brightness steps. Positive = the curve is brighter than your preference. Errors in opposite directions cancel, so low bias alone doesn't mean a good fit. | < 0.5 Minimal, < 2 Slight, < 5 Moderate, else Strong |
| **Fit stability** | The largest share of the total weighted error that comes from a single point. It doesn't measure how far the curve would move without that point. | < 30 % High, < 60 % Moderate, else "Low (Outlier Driven)" |

If the result is "Outlier Driven", look at that point in context. Delete it if it was an
accident. If it's a real preference, keep it even if the score gets worse, and collect more
points around it. A good score never replaces checking the graph and the screen at the light
levels that matter to you.

---

## 12. Built-in profiles

A profile is a bunch of saved settings. Since 1.14.0 the profile called
*Default* exactly matches the app's initial settings.

Listed below are the settings each built-in profile changes compared to the Default profile.

| Setting | Default (app defaults) | Battery Saver | Video Streaming | Outdoors | Night Reading |
|---|---|---|---|---|---|
| Min / Max brightness | 10 / 255 | **1 / 200** | **20** / 255 | **25** / 255 | **1** / 255 |
| Scale / Offset | 1.0 / 0 | **0.8** / 0 | 1.0 / 0 | **1.15 / 15** | 1.0 / 0 |
| Zone 1 scaling (form1A) | 5 | 5 | **6** | **8** | 5 |
| Zone 1 / Zone 2 end | 35 / 10,000 | same | same | **55 / 18,000** | same |
| Thresholds dark / dim / bright | 0.30 / 0.25 / 0.08 | **0.5 / 0.5 / 0.5** | **0.4** / 0.25 / **0.3** | same | **0.6** / 0.25 / 0.08 |
| Curve mid | 4.0 | same | same | **4.255** | same |
| Smoothing Δ | 1.8 | **2.8** | **0.5** | **4.0** | **0.8** |
| Anim. steps / min / max wait | 50 / 5 / 30 | **1** / 5 / 30 | 50 / **50 / 100** | **10 / 10** / 30 | 50 / **60 / 120** |
| Super dimming / PWM | off / off | off / off | **on** (threshold **20**) / off | off / off | off / **on** (floor 15) |

What each profile is for and their behavioral constraints:

- **Battery Saver:** very wide dead zones (50 %), so Tideo rarely updates. When it does, it moves
  quickly and without animation. The screen is dimmer overall and limited to 200. The name
  describes the intent, not a measured saving, which depends on your device.
- **Video Streaming:** steady brightness with slow, gentle transitions, and brighter in the dark.
  It follows the room's light, not the video's content. Its super dimming is switched on, but its
  threshold (20) equals its Min (20), so it can never engage until you change one of those settings.
- **Outdoors:** fast reactions, a brighter curve overall (+15 and ×1.15), and a Zone 3 that
  starts later. That later Zone 2 end is part of this preset, not a general recipe for more
  outdoor brightness (§3.4). Its Curve mid of 4.255 is set to match that Zone 2 end (§5.3).
- **Night Reading:** a very low minimum, PWM mode, a large dark threshold and slow, calm
  transitions. Its floor of 15 is a starting point, not a verified flicker-free level for your
  phone.

---

## 13. Symptom → setting cheat sheet

First determine the following: is the **brightness set wrong** once the screen has stopped
changing, or is it right and only the **changes** are annoying (too frequent, too rare, too slow
or jumpy)? Fixes for wrong brightness are in §3–§4, fixes for annoying changes in §5–§7.

**How to test a change:** note the current value, change **one** setting, press **Apply**, and
compare under similar light. Use Tideo's lux reading rather than guessing what "the living room"
measures, keep the sensor uncovered, and give the response time to settle. If Contexts can switch
your profile, check which profile is active while you compare.

| Symptom | First try | Then |
|---|---|---|
| Too bright in a dark bedroom | Lower **Min brightness** if the target already sits at Min, otherwise lower **form1A** | Turn on **super dimming** |
| Too dim in a dark room | Raise **Min brightness** for a floor, or **form1A** for the low-light curve | Raise **Offset** |
| Dark rooms are fine, but the living room or office is too dim/bright | Raise/lower **Zone 2 scaling (form2B)** | Adjust **Zone 2 offset (form2C)** |
| Most light levels are too dim or bright | **Scale** (or **Scale spread** if circadian scaling is on) | **Offset** for a fixed shift |
| A sharp rise just after the lights go on (around Zone 1 end) | Lower **form2C** and check the graph for a kink | Move **Zone 1 end** |
| Not bright enough outdoors | Check **Max brightness**, then **Scale** and the curve at the lux you measured | Raise **form2B**. Zone 2 end is not a reliable outdoor boost (§3.4) |
| Already at Max in direct sunlight and still hard to read | Turn on the **hand-off** so the phone's own sunlight boost can work (§10) | **Calibrate the display** to see whether the boost engages (a reading above 255) |
| Control keeps switching between Tideo and Android in bright light | Lower the **Hand-back level** | Raise **Hand-off lux** so the switch happens only in stronger light |
| Brightness flickers up and down under constant light | Raise the relevant **threshold** | Lower **Smoothing Δ** |
| A real light change is ignored | Lower the relevant **threshold** | — (Smoothing Δ can't rescue a rejected reading) |
| Hand or head shadows cause dips outdoors | Raise **Bright threshold** | Move **Curve mid** higher |
| Responds, but takes too long to reach a useful level | Raise **Smoothing Δ** | Lower **Min/Max wait** |
| Each animation is too slow | Fewer **Animation steps** or shorter **waits** | — |
| Animations appear step-wise | Raise **Animation steps** a little | Lower **Min wait** to keep the duration |
| Evening dimming is too strong or too weak | Change **Scale spread** | **Taper midpoint** / **Transition factor** |
| Dawn/dusk change is too sudden | Raise **Transition factor** (≤ 0.5) | Lower **Scale steepness** |
| Visible flicker at low brightness | Turn on **PWM-sensitive mode** and set the **PWM threshold** as low as works without flicker | Raise **Software exponent** for darker results |
| A setting seems to do nothing | Check whether **Min/Max** is deciding the target, whether circadian scaling has replaced Scale, which profile is active, whether Tideo is paused, and whether Android is in control (§10) | — |
| Curve wizard suggestion looks strange | Add points in empty lux decades | Delete outliers, and make sure Scale = 1 and Offset = 0 when recording |

---

## 14. Reference table of all settings

"Range" is what Tideo enforces when it saves a value. A "recommended" range is advice only: wider
values are accepted, but the curve may stop making sense, so check the graph. Since v1.12.0 the
curve coefficients are stored without clamping, so form1A has no upper limit and form2B accepts
any finite number.

| On-screen name | Internal name | Screen | Default | Range |
|---|---|---|---|---|
| Zone 1 scaling (form1A) | `form1A` | Curve & Brightness | 5.0 | ≥ 0 (recommended 1–5) |
| Zone 1 end (lux) | `zone1End` | Curve & Brightness | 35 | ≥ 1 |
| Zone 2 scaling (form2B) | `form2B` | Curve & Brightness | 8.8 | any finite value (recommended 3–10) |
| Zone 2 offset (form2C) | `form2C` | Curve & Brightness | 18 | ≤ Zone 1 end (recommended −300 … Zone 1 end) |
| Zone 2 end (lux) | `zone2End` | Curve & Brightness | 10,000 | ≥ Zone 1 end |
| Zone 2 alignment (form2A) | derived | Curve & Brightness | 29.58 | read-only |
| Zone 3 alignment (form3A) | derived | Curve & Brightness | 2,513.15 | read-only |
| Min brightness | `minBrightness` | Misc | 10 | 0 – 255 |
| Max brightness | `maxBrightness` | Misc | 255 | Min – 255 |
| Offset | `offset` | Misc | 0 | −255 – 255 |
| Scale | `scale` | Misc | 1.0 | 0.1 – 10 (ignored while circadian scaling is on) |
| Animation steps | `animSteps` | Misc | 50 | 0 – 100 |
| Min wait (ms) | `minWaitMs` | Misc | 5 | 1 – 5,000 |
| Max wait (ms) | `maxWaitMs` | Misc | 30 | Min wait – 5,000 |
| Dark threshold | `thresholdDark` | Reactivity | 0.30 | 0 – 1 |
| Dim threshold | `thresholdDim` | Reactivity | 0.25 | 0 – 1 |
| Bright threshold | `thresholdBright` | Reactivity | 0.08 | 0 – 1 |
| Curve slope | `thresholdSteepness` | Reactivity | 2.1 | 0.1 – 10 |
| Curve mid (log lux) | `thresholdMidpoint` | Reactivity | 4.0 | 0 – 6 |
| Smoothing Δ | `deltaFactor` | Reactivity | 1.8 | 0.1 – 10 |
| Use override detection | `detectOverrides` | Reactivity | off | — |
| Trust low-accuracy sensor | `trustUnreliableSensor` | Reactivity | off | — |
| Enable dynamic scaling | `scalingEnabled` | Circadian | off | — |
| Scale spread | `scaleSpread` | Circadian | 15 | 1 – 100 |
| Scale steepness | `scaleSteepness` | Circadian | 6 | 1 – 20 |
| Transition factor | `scaleTransitionFactor` | Circadian | 0.1 | 0 – 1 (warns above 0.5) |
| Taper midpoint | `scaleTaperMidpoint` | Circadian | 190 | 130 – 240 (flagged above Max while scaling is on) |
| Taper steepness | `scaleTaperSteepness` | Circadian | 0.075 | 0.001 – 1 |
| Use super dimming | `dimmingEnabled` | Super Dimming | off | — |
| Strength setpoint | `dimmingStrength` | Super Dimming | 25 | 0 – 65 |
| SD exponent | `dimmingExponent` | Super Dimming | 2.5 | 0.5 – 5 |
| SD threshold / PWM threshold | `dimmingThreshold` | Super Dimming | 15 | 0 – 255 (must be above Min to engage) |
| Spread (circadian) | `dimSpread` | Super Dimming | 100 | −100 – 100 |
| Use software dimming (PWM-sensitive) | `pwmSensitive` | Super Dimming | off | — |
| Software exponent (PWM) | `pwmExponent` | Super Dimming | 0.8 | 0.1 – 3 |
| Hand off to Adaptive Brightness | `handoffEnabled` | Hand-off Behavior | off | — |
| Hand-off lux | `handoffLux` | Hand-off Behavior | 25,132 | 1 – 100,000 (the curve's lowest valid lux is used while that is higher) |
| Hand-back level (percent of hand-off lux) | `handoffHysteresis` | Hand-off Behavior | 80 % (stored as 0.8) | 50 – 100 % |
| Panic sensitivity | `panicSensitivity` | Live Debug | 8 | 0 (no shake needed) – 10 (long, vigorous shake) |

Values outside a range are clamped when saved, except the hand-back level, which
goes back to 80 %. Cross-setting conditions are not clamped, but handled differently. Some block Apply (form2C
above Zone 1 end). Others only warn: Taper midpoint above Max, or a dimming threshold below Min.
These values are not changed automatically. The one automatic correction is Max being raised by Apply on
the Misc screen (§3.6).

---

## 15. Where to find it in the code

These point at **v1.15.0**, the version this guide was checked against. Use them when you need the
exact rounding rather than the simplified formula.

| Topic | Source |
|---|---|
| Curve, thresholds, smoothing, taper and animation timing | [BrightnessEngine.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt) |
| Continuity coefficients (form2A, form3A) | [BrightnessFormulae.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessFormulae.kt) |
| The stored band, pending reading and settling | [LightAdmission.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/LightAdmission.kt) |
| Applying targets, the PWM floor and recording overrides | [PipelineCycleRunner.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/PipelineCycleRunner.kt) |
| Animation writes and the throttle | [AnimationRunner.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/AnimationRunner.kt), [ThrottleController.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/ThrottleController.kt) |
| Defaults, saved ranges and warnings | [AabSettings.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettings.kt), [AabSettingsMapper.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/settings/AabSettingsMapper.kt), [SettingsValidator.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/settings/SettingsValidator.kt) |
| Built-in profiles | [DefaultProfiles.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/settings/DefaultProfiles.kt) |
| Solar windows and the day/night modifier | [SolarTimes.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/circadian/SolarTimes.kt), [DynamicScaleEngine.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/circadian/DynamicScaleEngine.kt), [CircadianScaleRefresh.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/CircadianScaleRefresh.kt) |
| Super dimming and PWM formulas, and which mode runs | [SoftwareDimming.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/SoftwareDimming.kt), [SuperDimmingCoordinator.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/SuperDimmingCoordinator.kt) |
| Bright-light hand-off and the display calibration | [HandoffRules.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/HandoffRules.kt), [HandoffCoordinator.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/app/src/main/kotlin/com/tideo/autobrightness/app/runtime/HandoffCoordinator.kt), [DisplayCalibration.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/DisplayCalibration.kt) |
| Override detection and point normalisation | [OverrideRules.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/brightness/OverrideRules.kt) |
| Curve wizard and its quality report | [CurveSuggestionEngine.kt](https://github.com/faded-penguin021/Tideo-Auto-Brightness/blob/v1.15.0/domain/src/main/kotlin/com/tideo/autobrightness/domain/wizard/CurveSuggestionEngine.kt) |

