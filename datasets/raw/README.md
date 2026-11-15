---
license: other
task_categories:
- object-detection
tags:
- clash-royale
- yolo
- games
pretty_name: Clash Royale arena detection
configs:
- config_name: crops
  default: true
  data_files:
  - split: train
    path: crops/train-*
- config_name: crops_predicted
  data_files:
  - split: train
    path: crops_predicted/train-*
- config_name: synthetic_arena
  data_files:
  - split: train
    path: synthetic_arena/train-*
dataset_info:
- config_name: crops
  features:
  - name: image
    dtype: image
  - name: label
    dtype:
      class_label:
        names:
          '0': archer
          '1': archer-evolution
          '2': archer-queen
          '3': arrows
          '4': axe
          '5': baby-dragon
          '6': balloon
          '7': bandit
          '8': barbarian
          '9': barbarian-barrel
          '10': barbarian-evolution
          '11': barbarian-hut
          '12': bat
          '13': bat-evolution
          '14': battle-healer
          '15': battle-ram
          '16': battle-ram-evolution
          '17': bomb
          '18': bomb-tower
          '19': bomber
          '20': bomber-evolution
          '21': bowler
          '22': cannon
          '23': cannon-cart
          '24': cannoneer-tower
          '25': clock
          '26': clone
          '27': dagger-duchess-tower
          '28': dark-prince
          '29': dart-goblin
          '30': dirt
          '31': earthquake
          '32': electro-dragon
          '33': electro-giant
          '34': electro-spirit
          '35': electro-wizard
          '36': elite-barbarian
          '37': elixir
          '38': elixir-collector
          '39': elixir-golem-big
          '40': elixir-golem-mid
          '41': elixir-golem-small
          '42': executioner
          '43': fire-spirit
          '44': fireball
          '45': firecracker
          '46': firecracker-evolution
          '47': fisherman
          '48': flying-machine
          '49': freeze
          '50': furnace
          '51': giant
          '52': giant-skeleton
          '53': giant-snowball
          '54': goblin
          '55': goblin-ball
          '56': goblin-barrel
          '57': goblin-brawler
          '58': goblin-cage
          '59': goblin-drill
          '60': goblin-giant
          '61': goblin-hut
          '62': golden-knight
          '63': golem
          '64': golemite
          '65': graveyard
          '66': guard
          '67': heal-spirit
          '68': hog
          '69': hog-rider
          '70': hunter
          '71': ice-golem
          '72': ice-spirit
          '73': ice-spirit-evolution
          '74': ice-wizard
          '75': inferno-dragon
          '76': inferno-tower
          '77': king-tower
          '78': knight
          '79': knight-evolution
          '80': lava-hound
          '81': lava-pup
          '82': lightning
          '83': little-prince
          '84': lumberjack
          '85': magic-archer
          '86': mega-knight
          '87': mega-minion
          '88': mighty-miner
          '89': miner
          '90': mini-pekka
          '91': minion
          '92': monk
          '93': mortar
          '94': mortar-evolution
          '95': mother-witch
          '96': musketeer
          '97': night-witch
          '98': pekka
          '99': phoenix-big
          '100': phoenix-egg
          '101': phoenix-small
          '102': poison
          '103': prince
          '104': princess
          '105': queen-tower
          '106': rage
          '107': ram-rider
          '108': rascal-boy
          '109': rascal-girl
          '110': rocket
          '111': royal-delivery
          '112': royal-ghost
          '113': royal-giant
          '114': royal-giant-evolution
          '115': royal-guardian
          '116': royal-hog
          '117': royal-recruit
          '118': royal-recruit-evolution
          '119': skeleton
          '120': skeleton-barrel
          '121': skeleton-dragon
          '122': skeleton-evolution
          '123': skeleton-king
          '124': skeleton-king-skill
          '125': sparky
          '126': spear-goblin
          '127': tesla
          '128': tesla-evolution
          '129': the-log
          '130': tombstone
          '131': tornado
          '132': valkyrie
          '133': valkyrie-evolution
          '134': wall-breaker
          '135': wall-breaker-evolution
          '136': witch
          '137': wizard
          '138': x-bow
          '139': zap
          '140': zappy
          '141': baby-dragon-evolution
          '142': berserker
          '143': boss-bandit
          '144': cannon-evolution
          '145': dart-goblin-evolution
          '146': electro-dragon-evolution
          '147': executioner-evolution
          '148': furnace-evolution
          '149': giant-snowball-evolution
          '150': goblin-barrel-evolution
          '151': goblin-cage-evolution
          '152': goblin-curse
          '153': goblin-demolisher
          '154': goblin-drill-evolution
          '155': goblin-giant-evolution
          '156': goblin-machine
          '157': goblinstein
          '158': hunter-evolution
          '159': inferno-dragon-evolution
          '160': lumberjack-evolution
          '161': mega-knight-evolution
          '162': musketeer-evolution
          '163': pekka-evolution
          '164': royal-ghost-evolution
          '165': royal-hog-evolution
          '166': rune-giant
          '167': skeleton-barrel-evolution
          '168': spirit-empress
          '169': suspicious-bush
          '170': vines
          '171': void
          '172': witch-evolution
          '173': wizard-evolution
          '174': elite-barbarian-evolution
          '175': minion-evolution
          '176': princess-evolution
          '177': zap-evolution
          '178': barbarian-launcher
          '179': party-hut
          '180': party-rocket
          '181': raging-prince
          '182': rocket-silo
          '183': ronin
          '184': baby-goblin
          '185': minion-giant
          '186': skeleton-army-evolution
          '187': electro-giant-evolution
          '188': knight-hero
          '189': giant-hero
          '190': mini-pekka-hero
          '191': musketeer-hero
          '192': wizard-hero
          '193': ice-golem-hero
          '194': goblin-hero
          '195': mega-minion-hero
          '196': barbarian-barrel-hero
          '197': magic-archer-hero
          '198': balloon-hero
          '199': dark-prince-hero
          '200': bowler-hero
          '201': tombstone-hero
          '202': valkyrie-hero
          '203': berserker-hero
          '204': ice-wizard-hero
          '205': electro-wizard-hero
          '206': royal-chef
          '207': terry
          '208': drop
  - name: source
    dtype: string
  - name: video_id
    dtype: string
  - name: track_id
    dtype: int32
  - name: frame
    dtype: string
  splits:
  - name: train
    num_examples: 27500
- config_name: crops_predicted
  features:
  - name: image
    dtype: image
  - name: video_id
    dtype: string
  - name: track_id
    dtype: int32
  - name: pred
    dtype: string
  - name: conf
    dtype: float32
  - name: top3
    dtype: string
  - name: trusted
    dtype: string
  - name: graded
    dtype: string
  splits:
  - name: train
    num_examples: 42778

---
# Clash Royale arena detection

Labelled Clash Royale arena data for detecting units, buildings, spells and towers, built for a Hog 2.6
playing bot. Fan-made research data, not affiliated with or endorsed by Supercell. Last updated 2026-10-15.

| Part | What | Size |
|---|---|---|
| **`crops` config (default)** | the author's graded unit crops, one per graded track | 27,500 crops (18,995 by hand + 8,505 auto-filled) |
| `crops_predicted` config | YouTube track crops with the crop classifier's call: confidence ≥ 0.7 or approved by the author | grows as the author approves more |
| `track_grades/` | the author's per-track grades with every box | 99 videos |
| `track_labels/<video_id>/` | per-video label files: author's answers, auto-fills, rejections, trusted training labels | 99 videos |
| `synthetic_arena` config | real-arena synthetic frames: real YouTube arena backdrops + real unit cut-outs, with boxes | 105,000 frames, 158 classes + spells |
| `archive/synthetic/` | archived: 6,000 synthetic frames with boxes (pasted cut-outs, too rough to use) | 6,000 frames |
| `video_labels/` | YOLO labels for broadcast / YouTube frames (labels only) | 11,107 files, 6 videos |
| `bundles/` | everything the labelling pipeline made per video | 75 videos, 4.1 GB |

Frames from the author's own phone games and Training Camp screenshots are **not** included.

## Format

* **Frame:** the 568x896 arena crop (18x32 tile grid; queen towers at (116,200) (461,200) (116,689)
  (461,689), kings at (284,108) (284,790)), JPEG. See [SPEC.md](SPEC.md).
* **Archived synthetic parquet:** `objects.bbox` is `[x0, y0, x1, y1]` in pixels; `objects.category` uses
  the 208 class names below.
* **Class list:** `classes.txt` / `data.yaml`. The class order is permanent: new
  classes are only appended, so old label files stay valid.

## Classes (208)

Every card, evolution and hero as of 8 Oct 2026. A class is a unit as it appears on the field, **not a
card**: Skeleton Army = `skeleton`, Goblin Gang = `goblin` + `spear-goblin`, Minion Horde = `minion`, Three
Musketeers = `musketeer`.

* **Evolution:** diamond level badge, class `<card>-evolution`.
* **Hero:** crown badge, its own class `<card>-hero` (since 2026-10-08), e.g. `musketeer-hero`,
  `ice-wizard-hero`, `giant-hero`. Champions (golden-knight, skeleton-king, archer-queen, mighty-miner, monk,
  little-prince, goblinstein, boss-bandit) have their own class.
* **Spells are classes:** poison, earthquake, graveyard, rage, freeze, arrows, the-log, barbarian-barrel and
  so on label the spell's area or projectile. Fireball explosions and tornado swirls are not labelled.
* **No examples yet:**
  * `terry`: a removed one-time card.
  * `zap` and `zap-evolution`: too quick to see.
  * `rocket`, `vines`, `heal-spirit`: parked, because in gameplay footage they are a blur, a ground effect
    or tiny, and too hard to box reliably.
* **Pending splits:** these are labelled as the base card for now. `bowler` vs `bowler-hero`;
  `barbarian-barrel` vs `barbarian-barrel-hero` (hero = golden spikes, normal = black spikes); `minion` vs
  `minion-evolution`.

Full rules are in [LABELLING.md](LABELLING.md).

## How the video labels are made

YouTube gameplay (pro broadcasts, top-ladder POV, card guides) is normalised to the arena frame. Then:

1. **Tracks.** A detector finds the units and they are tracked across frames, so one track is one unit.
2. **Hand grading.** The author names tracks in a grading tool: 18,995 tracks by hand so far.
3. **Auto-fill.** Each answer is copied to near-identical tracks of the same video (DINOv2 crop similarity
   ≥ 0.8, 96-100% right when checked): 8,505 more.
4. **Crop classifier.** A crop classifier is trained on those answers (see the model repo
   `SudhishNeel/clash-royale-detector`, `crop_classifier/`). Its confident calls were checked yes/no by the
   author.
5. **Trusted labels only.** A track is used for training only if one of these is true; everything else is
   painted out of the frame:
   * the author named it, or it was auto-filled from the author's answer;
   * an agent label agrees with the classifier (audit: 38/39 right);
   * the classifier alone is at confidence ≥ 0.85, except for goblin / spear-goblin (tightened after the
     audit: at ≥ 0.7 these calls were only 28/40 right, goblins 1/8);
   * for spells, the detector's spell call is in the classifier's top 3 (audit: 18/20 right).

   Audit = the author's yes/no check of a random sample of machine-trusted tracks (2026-10-09).

   Tracks the author rejected are never trusted. In hero videos, base-card labels are not trusted unless
   the author gave them, so `bowler` is never confused with `bowler-hero`.

**Status of the trusted labels (2026-10-09; the trusted files are refreshed at the next label upload):**

* 46,919 tracks in 65 graded videos; 26,496 unit tracks are trusted.
* Where the trusted labels come from: 14,038 by the author, 7,340 auto-filled, 6,237 agreement, 1,420 from
  the classifier alone, 416 spells.
* 17,739 tracks are left out.
* New footage is being added for the thinnest classes: rocket, goblin-barrel, void, vines, heal-spirit,
  boss-bandit, goblin-machine, wizard-hero and bowler-hero.

Agent-only labels (LLM agents naming crops) agreed with the author only about 60% of the time. That is why
none are used unless the classifier agrees.

## Hand grading (`track_grades/`)

Per-track class grades from the dataset author for YouTube gameplay videos. No frames are stored.
`track_grades/<video_id>/grades.csv` has one row per box of every graded track, with these columns:
`track_id, label, source, frame, is_best, x0, y0, x1, y1`. Coordinates are pixels on the 568x896 frame.

* `source` is `user` (graded by hand) or `auto` (the author's answer copied to a near-identical track).
* `label` can be `drop`, meaning not a unit (effects, UI, text).
* `is_best` marks the frame the grade was made on. Other frames of a track can drift onto a neighbouring
  unit.

To rebuild the frames, run
`python scripts/rebuild_video_frames.py <video_id> video.mp4 out_dir track_grades` on a video you have
downloaded yourself.

## Graded crops (`crops` config, shown by default)

One row per graded track: the unit cut out of its box on the frame it was graded on. The columns are
`image, label, source, video_id, track_id, frame`.

* `label` is one of the 208 classes or `drop`.
* `video_id`, `track_id` and `frame` link back to `track_grades/`.

## Real-arena synthetic frames (`synthetic_arena` config)

Extra training frames for the rarer cards, built only from real gameplay pixels (2026-10-15):

* **Backdrop:** a real arena from a YouTube match, cleaned by taking the per-pixel median over that match's frames
  with every unit masked out (113 arenas).
* **Units:** real units cut out of the same footage at their true in-game scale, from tracks the author labelled (or
  the crop classifier confirmed). Every cut-out was checked by the crop classifier on its own picture, so deploy
  clouds, clocks and name captions are left out. 702 cut-outs over 129 classes.
* **Placement:** game-plausible: troops in lanes, on bridges and around towers, buildings in their owner's half, no
  ground units on the river. 36-40% of frames have overlapping fights (back units partly hidden); a unit is only pasted
  and labelled if at least 30% of it stays visible.
* **Balance:** rarer classes appear more often (every class at least 461 times).
* **Three batches:**
  * `train-0000*`: 20,000 frames, 129 classes, the strictest cut-out check.
  * `train-b2-*` (ids `synth_b2_*`): 6,000 frames aimed at the rarer cards, 158 classes, about 80% of units from
    cards with few cut-outs. For tracks the author labelled, each cut-out is compared with the author's own graded
    crops of that card instead of requiring the crop classifier, which was trained before many rare cards were
    labelled.
  * `train-b3-*` (ids `synth_b3_*`): 4,000 frames with 1-3 spells each, from the author's hand-labelled spells.
    Area spells (rage, poison, freeze, graveyard, earthquake, arrows, void, goblin-curse) are pasted as
    see-through overlays under the units, so the arena shows through; the-log, barrels, snowball and axe are pasted
    like units.
* **Bulk batches:** `train-c###*` (ids `synth_c###*`): runs of 5,000 frames, each with its own randomly drawn
  settings: units per frame (2-20), share of overlapping fights (15-60%), spells in about 60% of runs (0-4
  per frame) and rare-card targeting in about 30% of runs. Same icon-free cut-outs and spell overlays as
  batches 2 and 3.
* **No icons:** cut-outs showing a deploy clock, an emote, a caption or another overlay icon were found by a visual
  review and left out of batches 2 and 3 (138 cut-outs). Batch 1 was made before that review and still has some.
* **Not in the synthetic frames yet:** boss-bandit, dirt, royal-delivery, tornado and a few evolutions. They do
  have real hand-graded labels (see `crops`); no clean cut-out of them could be made yet.

Columns: `image` (568x896), `image_id`, `backdrop_video` (the match the arena came from), `n_boxes`,
`objects.bbox` (`[x0, y0, x1, y1]` pixels) and `objects.category` (the 208 classes). The author checked a random
sample by eye before upload. Use these alongside the real labels, not instead of them.

## Classifier-labelled crops (`crops_predicted` config)

YouTube track crops (112px tight crops) with the crop classifier's call, where the call is confident or the
author approved it. A row is included when either holds:

* the classifier's confidence is at least 0.7;
* the author graded or approved the track: lower-confidence calls are checked one by one in a yes/no tool,
  and only approved ones are added.

Tracks the author rejected are never included.

| Column | Meaning |
|---|---|
| `video_id`, `track_id` | the track |
| `pred`, `conf`, `top3` | the crop classifier's call (fine-tuned on 24,421 graded tracks) |
| `trusted` | the label used for training; empty means left out (see "How the video labels are made") |
| `graded` | `user` or `auto` when the author graded it; those rows are also in `crops` |

## Per-video label files (`track_labels/<video_id>/`)

The current label files of every graded video: `names_user.json` (the author's answers), `auto_user.json`
(auto-fills), `yn_rejects.json` (classifier calls the author rejected), `trusted.json` + `trusted_stats.json`
(the filtered training labels). Newer than the copies inside `bundles/`.

## Raw bundles (`bundles/<video_id>.tar`)

Everything the labelling pipeline made for one video:

* `tracks.json`: every track's boxes per frame.
* `crops/` and `ctx/`: track crops and whole-arena previews.
* `decks/` and `layout.json`.
* Every label file:
  * agent passes: `names_*`, `unsure_*`;
  * Opus review: `review_*`, with `*.pre_opus.json` holding the labels from before the review;
  * the author's answers: `names_user.json`, `auto_user.json`, and `yn_rejects.json` where present
    (classifier calls the author rejected);
  * the filtered training labels: `trusted.json`, with `trusted_stats.json`.
* DINOv2 tight-crop embeddings: `emb_tight.npz`.
