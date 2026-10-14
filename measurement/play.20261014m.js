(() => {
  "use strict";

  // ── Measurement version (2026-10-14) ──────────────────────────────────────
  // A COPY of webapp/app.20261007d.js, not a link to it: the measuring tool
  // must not change when the game changes. What differs from the game:
  //   - no quests: pure play at the level chosen in Settings (default tenths);
  //   - a measurement = 33 attempts; after the 33rd a "Measurement complete"
  //     card shows its numbers and the screen returns to its initial state;
  //   - own storage keys, no server sync, own on-device leaderboard
  //     (early / late average, attempts per minute, attempts per win);
  //   - no cheer-ups, twins/neighbors, numeric-time and progress-bar switches.
  // Quest/analytics code that stays below is unreachable from this page.

  // Self-update detection (Alex 2026-08-15): a browser (or an intermediate
  // proxy/CDN) can keep serving a stale index.html long after a new build is
  // deployed — it's the only file whose NAME never changes between deploys
  // (everything it references, app.YYYYMMDDx.js/styles.YYYYMMDDx.css/icons,
  // uses a cache-busted filename instead), so if IT goes stale, the page
  // just silently keeps running an old build forever, pointing at old JS.
  // The server now sends a no-cache header for index.html specifically (see
  // .htaccess) — this is the belt-and-suspenders client-side half: capture
  // which script file we're actually running (via `document.currentScript`,
  // only valid synchronously like this, at the very top of the file, before
  // anything else executes), and periodically re-fetch a genuinely fresh
  // copy of index.html to compare against.
  const OWN_SCRIPT_SRC = document.currentScript ? document.currentScript.src : "";

  // Nullish-coalescing (`??`) and optional-chaining (`?.`) polyfill helper.
  // Old iPhone Safari (< 13.4) doesn't parse either — since app.js is one
  // classic script, ANY unsupported token anywhere in the file is a
  // SyntaxError that kills the WHOLE script (nothing runs, no error shown).
  // `nz(a ?? b)` reads as `a` with a fallback of `b` when `a` is null/undefined.
  function nz(value, fallback) {
    return value === undefined || value === null ? fallback : value;
  }

  // About-screen version string (Alex 2026-08-15: "должна обновляться всякий
  // раз при деплое" — the date/time part must never go stale again). The
  // semantic version number below is still bumped by hand (it should track
  // meaningful changes, not every upload), but the DATE is read from the
  // server's actual Last-Modified header on index.html via
  // `document.lastModified` — since this project's own convention already
  // requires re-uploading index.html on every JS/CSS-changing deploy (new
  // cache-busted filenames), this tracks real deploys. A fresh upload
  // always shows a fresh,
  // accurate timestamp with zero manual editing, no matter when someone
  // remembers (or forgets) to touch this file.
  const APP_VERSION = "13.49";
  const STORAGE_KEY = "play-of-time.measurement.v1";
  const FALLBACK_STORAGE_PREFIX = "play-of-time-measurement-fallback:";
  // Identity lives in its own storage key, separate from `db` — "Erase all
  // data" resets game progress but should not orphan the player's leaderboard
  // name/rank from under them.
  const IDENTITY_KEY = "play-of-time.measurement.identity.v1";
  const API_BASE = "./api/";
  const SYNC_BATCH_SIZE = 300;
  const AIM_TIME = 3000;
  const MIN_ATTEMPT = 2000;
  const MAX_ATTEMPT = 4000;
  const SESSION_AUTO_CLOSE_ATTEMPTS = 33;
  const MEASUREMENT_ATTEMPTS = 33;
  // Honest tap timing (dithering) — mirrors native TouchRateEstimator / Utils.kt.
  const DEFAULT_TOUCH_CELL_MS = 4.0;   // fallback cell until measured (~250 Hz panel)
  const DITHER_DISABLE_CELL_MS = 1.2;  // cell <= this (~1000 Hz) → dithering not needed
  const DITHER_MAX_HALF_MS = 10.0;     // clamp dither half-width (panel drop protection)
  // "week" inserted between "day" and "month" (Alex 2026-09-19: "на экран
  // analytics добавь в фильтр после day и перед month, week (отрабатывает
  // аналогично другим фильтрам)") -- matches native PeriodFilter's own
  // Session/Day/Week/Month/All time order.
  const PERIODS = ["SESSION", "TODAY", "WEEK", "MONTH", "ALL_TIME"];
  // Fixed on-screen histogram bar width — same px width on every level (L1/L2/
  // L3), regardless of how many buckets the accuracy tick produces. Previously
  // computed from bucket count (360/buckets.length*0.6, capped at 3px), which
  // happened to land near 3px on L2 but noticeably thinner on levels with more
  // (finer-tick) buckets, e.g. L3.
  const HIST_BAR_PX = 3;
  const ACCURACY = {
    SECONDS: 0,
    DECISECONDS: 1,
    CENTISECONDS: 2,
    MILLISECONDS: 3
  };
  const VICTORY = {
    ONE_ONLY: { count: 1, condition: "SESSION", text: "" },
    TWO_IN_ROW: { count: 2, condition: "ROW", text: "two times in a row" },
    THREE_IN_ROW: { count: 3, condition: "ROW", text: "three times in a row" },
    TWO_IN_N: { count: 2, condition: "IN_N", text: "two times in N attempts" },
    THREE_IN_N: { count: 3, condition: "IN_N", text: "three times in N attempts" },
    MEASURE: { count: 0, condition: "MEASURE", text: "" }
  };
  // Measurement: one pseudo-quest per level — no victory condition (targets are
  // just a hit counter), no tolerance, no indulgence, 33 attempts per
  // measurement. Level 1 = tenths, 2 = hundredths, 3 = thousandths.
  const QUESTS = [
    q(1, 1, "MEASURE", "DECISECONDS", MEASUREMENT_ATTEMPTS, 0, false, false, null),
    q(2, 1, "MEASURE", "CENTISECONDS", MEASUREMENT_ATTEMPTS, 0, false, false, null),
    q(3, 1, "MEASURE", "MILLISECONDS", MEASUREMENT_ATTEMPTS, 0, false, false, null)
  ];

  const app = document.getElementById("app");
  let db = loadDb();
  let identity = loadIdentity();
  let view = "play";
  // Leaderboard fetch state (transient — not persisted).
  let leaderboardSort = "level";
  let leaderboardData = null;
  let leaderboardLoading = false;
  let leaderboardError = null;
  // Tap own row -> report.html ("faces"); tap another row -> compare popup
  // (Alex 2026-09-17: "в лидборде тюкаешь по себе - морды, тюкаешь по
  // сапернику - сравнение" -- ports native LeaderboardScreen.kt's
  // onSelectRow/ComparePlayersDialog/ReportDialog).
  let leaderboardCompareRow = null;
  let leaderboardReportUrl = null;
  let leaderboardReportLoading = false;
  let lbNameStatus = "";
  let syncTimer = 0;
  // Set right before we deliberately drop out of fullscreen (e.g. so a
  // mobile keyboard can appear for the leaderboard name field) — tells the
  // fullscreenchange listener in bindHostPage this exit was ours, not the
  // player leaving the game, so it doesn't close the whole overlay.
  let suppressFullscreenClose = false;
  let syncInFlight = false;
  let timer = {
    state: "ready",
    startWall: 0,
    startPerf: 0,
    raf: 0,
    elapsed: 0,
    resultType: "none"
  };
  let audioCtx = null;
  // Real celebration / disappointment sounds (native res/raw/*.m4a). These
  // two instances exist only to warm the browser's HTTP cache early —
  // playSuccessSound()/playSorrySound() below always play a FRESH Audio()
  // each time rather than reusing (and currentTime-resetting) one long-lived
  // element, which is a known source of "the sound sometimes just doesn't
  // play": a shared element can get stuck mid-transition on a fast repeat
  // trigger, especially on mobile. A fresh instance plays instantly off the
  // warmed cache and can't inherit a stuck previous state.
  const HOORAY_SRC = "./assets/hooray.m4a";
  const SORRY_SRC = "./assets/sorry.m4a";
  const hoorayWarmup = new Audio(HOORAY_SRC);
  const sorryWarmup = new Audio(SORRY_SRC);
  hoorayWarmup.preload = "auto";
  sorryWarmup.preload = "auto";
  // Mobile Chrome/Opera can silently withhold audio playback permission
  // (2026-08-23: Alex reports salute plays with no sound on mobile Chrome +
  // Opera, no console errors surfaced since every .play() here is
  // .catch(()=>{})'d). Two independent mitigations, since it can't be
  // reproduced/confirmed from here (no real mobile device in this dev
  // environment): (1) explicitly "unlock" playback with a real play()+pause()
  // on an already-loaded element, synchronously inside the very first
  // genuine tap on the page (openGame(), below) — the standard fix for
  // mobile browsers that only grant audio permission within a user-gesture
  // call stack, which async/rAF-triggered salute audio calls are not. (2)
  // log (not swallow) play() rejections for salute/hooray/sorry so the next
  // report from a real device shows the actual DOMException name instead of
  // nothing.
  let audioUnlocked = false;
  function unlockAudioPlayback() {
    if (audioUnlocked) return;
    audioUnlocked = true;
    [hoorayWarmup, sorryWarmup].forEach((el) => {
      try {
        const p = el.play();
        if (p && p.catch) p.then(() => el.pause()).catch(() => {});
      } catch (e) { /* ignore */ }
    });
  }
  function logPlayRejection(label, err) {
    try { console.warn(`[audio] ${label} play() rejected:`, err && err.name, err && err.message); } catch (e) { /* ignore */ }
  }
  let appFrameHidden = false; // the landing page hid this app (left full screen)
  let metronomeId = 0;
  let playTickStart = 0;
  let lastRunPointerAt = 0;
  // Win-flow state (transient — not persisted, resets cleanly on reload).
  let awaitingHurray = false;   // green HOORAY! shown, tap → GreetingView
  let displayGreeting = false;  // GreetingView overlay visible
  let pendingTrigger = "NONE";  // cheer-up trigger armed for the next tap/session
  let cheerBubble = null;       // { trigger, text, attemptsLeft }
  let helpMode = false;         // "?" interactive help mode active
  let helpTopic = null;         // currently explained zone ("quest"/"stopwatch"/…) or null
  let helpPage = 0;            // page of the open help bubble (long texts are paged)
  let meditationOpen = false;   // About-screen "tap the logo" fullscreen meditation view (Alex 2026-09-15, native; ported to web 2026-09-18)
  let meditationVibrateTimer = 0;
  let potLogoHtmlCache = null;  // .pot-logo layer <img> markup, captured from the DOM once so the meditation view doesn't need its own ~80KB copy of the base64 art
  let justLandedMs = null;      // bucket centre of the freshest attempt (one-shot bar grow)
  let sessionSummary = null;    // yellow session-end summary overlay data
  let measurementDone = null;   // "Measurement complete" card data (leaderboard record)
  let pendingFailCard = null;   // FailCardData armed by onQuestWindowFailure, consumed on RESET tap
  let failCard = null;          // full-screen FailCard currently showing (native port, Alex 2026-09-25)
  // Main-screen dynamic scale (native: dynamicScaleMaxMs) — the histogram and
  // the log's error bars zoom to whichever attempts are currently SCROLLED
  // INTO VIEW in the log, not the whole day. Recomputed on every scroll tick;
  // falls back to the full-period scale when nothing is measurable yet.
  let mainDynamicScaleMax = null;
  let mainScaleFallback = 200;
  let mainGeomEarly = 0, mainGeomAbs = 0, mainGeomLate = 0, mainAccuracy = 0;
  // Sessions-log tree expansion (native: expandedYears/Months/Days/Sessions).
  // Auto-expands the path to the most recent session; re-computed on period change.
  let treeExpanded = null;
  // What the Pace of learning chart shows (Alex 2026-10-07): pace / hit / lucky.
  let paceMetric = "pace";      // { years:Set, months:Set, weeks:Set, days:Set, sessions:Set }
  let pbDisplayHalfMs = null;   // progress-bar half-window currently shown (auto, animated)
  let pbAnimRaf = 0;            // rAF handle for the smooth scale/number transition
  // Dither compensation state (echo/re-roll by RAW cell) — only previous attempt kept.
  let lastDitherCellIndex = null;
  let lastDitherValueMs = 0;
  // Live touch-cell estimator: median of coalesced pointer-sample intervals.
  const touchIntervals = [];
  let touchCellCached = 0; // 0 = no estimate yet

  function touchFeed(times) {
    if (!times || times.length < 2) return;
    for (let i = 1; i < times.length; i += 1) {
      const d = times[i] - times[i - 1];
      if (d >= 0.3 && d <= 30) {
        touchIntervals.push(d);
        if (touchIntervals.length > 400) touchIntervals.shift();
      }
    }
    if (touchIntervals.length >= 24) {
      const sorted = [...touchIntervals].sort((a, b) => a - b);
      touchCellCached = sorted[sorted.length >> 1]; // median
    }
  }

  function touchCellForDither(fallbackMs) {
    return touchCellCached > 0 ? touchCellCached : fallbackMs;
  }

  function touchDitherHalf(fallbackMs) {
    const cell = touchCellForDither(fallbackMs);
    if (cell <= DITHER_DISABLE_CELL_MS) return 0;
    return Math.min(DITHER_MAX_HALF_MS, Math.max(0.5, cell / 2));
  }

  // Apply dithering to a raw stop duration. L3 (milliseconds) only: there the win
  // window (±0.5 ms) is finer than the input cell, so exactly-3.000 can fall between
  // grid nodes. L1/L2 windows are wider than the cell → never dither. Echo keeps
  // physical repeats (twins / triple-3.000) alive; narrow 2999/3001-miss re-roll.
  function applyDither(rawDuration) {
    const quest = getQuest();
    const fallback = db.settings.touchCellMs || DEFAULT_TOUCH_CELL_MS;
    const cellMs = touchCellForDither(fallback);
    const ditherHalfMs = quest.accuracy === ACCURACY.MILLISECONDS ? touchDitherHalf(fallback) : 0;
    if (touchCellCached > 0) db.settings.touchCellMs = touchCellCached;
    const freshDither = () => ditherHalfMs <= 0
      ? rawDuration
      : Math.round(rawDuration + (Math.random() * 2 - 1) * ditherHalfMs);
    if (ditherHalfMs <= 0) {
      lastDitherCellIndex = null;
      return rawDuration;
    }
    const cellIndex = Math.round((rawDuration - AIM_TIME) / cellMs);
    const tolMs = getEffectiveToleranceMs();
    let value;
    if (lastDitherCellIndex !== null && cellIndex === lastDitherCellIndex) {
      const echoed = lastDitherValueMs;
      const offset = Math.abs(echoed - AIM_TIME);
      const isNeighborMiss = offset === 1 && offset > tolMs; // 2999/3001 that still misses
      value = isNeighborMiss ? freshDither() : echoed;
    } else {
      value = freshDither();
    }
    lastDitherCellIndex = cellIndex;
    lastDitherValueMs = value;
    return value;
  }

  function q(levelId, taskId, victoryKey, accuracyKey, maxAttemptsDefault, toleranceMsDefault, adaptiveN, adaptiveT, congrats, maxToleranceSteps = 3) {
    return {
      levelId,
      taskId,
      victoryKey,
      victory: VICTORY[victoryKey],
      accuracy: ACCURACY[accuracyKey],
      maxAttemptsDefault,
      toleranceMsDefault,
      adaptiveN,
      adaptiveT,
      adaptiveNStep: 33,
      // Indulgence ceiling for adaptive tolerance = toleranceMsDefault + maxToleranceSteps * accuracyStepMs.
      // Default 3 steps (±3ms at MILLISECONDS accuracy); L3.4/L3.5 widen this to 9 (Alex 2026-08-09).
      maxToleranceSteps,
      congratulationDescription: congrats
    };
  }

  function defaultDb() {
    const first = currentQuestFrom(1, 1);
    return {
      settings: {
        soundOn: false,
        vibrationOn: false,
        // 2026-09-10 native parity: two settings split out of the always-on
        // sound/visual behavior below, both default ON so existing behavior
        // is unchanged until a player explicitly opts out.
        saluteResultSoundsOn: false,
        twinOutlineOn: false,
        beatsPerSecond: 2,
        aimTime: AIM_TIME,
        currentLevelId: 1,
        currentTaskId: 1,
        accuracy: first.accuracy,
        selectedHand: "RIGHT",
        periodFilter: "TODAY",
        cheerUpsEnabled: false,
        welcomeShown: true,
        touchCellMs: DEFAULT_TOUCH_CELL_MS,
        cheerPhraseIndex: 0,
        numericTime: true,
        progressBarOn: true,
        progressScale: "auto"
      },
      quest: {
        currentN: nz(first.maxAttemptsDefault, -1),
        currentToleranceMs: first.toleranceMsDefault,
        windowAttempts: 0,
        windowWins: 0,
        windowMaxErrorMs: 0,
        windowLastWinIndex: 0,
        windowFirstWinIndex: 0,
        windowStartMs: Date.now(),
        wasIndulged: false
      },
      ids: { session: 1, attempt: 1, measurement: 1 },
      sessions: [],
      attempts: [],
      // One record per completed measurement (33 attempts) — the leaderboard.
      measurements: []
    };
  }

  function loadDb() {
    try {
      const parsed = JSON.parse(readStorage(STORAGE_KEY));
      if (!parsed || !parsed.settings || !Array.isArray(parsed.attempts)) return defaultDb();
      return normalizeDb(parsed);
    } catch (_) {
      return defaultDb();
    }
  }

  function normalizeDb(value) {
    const fresh = defaultDb();
    const merged = {
      ...fresh,
      ...value,
      settings: { ...fresh.settings, ...value.settings },
      quest: { ...fresh.quest, ...value.quest },
      ids: { ...fresh.ids, ...value.ids },
      sessions: Array.isArray(value.sessions) ? value.sessions : [],
      attempts: Array.isArray(value.attempts) ? value.attempts : [],
      measurements: Array.isArray(value.measurements) ? value.measurements : []
    };
    // Measurement version: these switches are gone from Settings and fixed here.
    merged.settings.cheerUpsEnabled = false;
    merged.settings.saluteResultSoundsOn = false; // switch removed (Alex 2026-10-14), always off
    merged.settings.twinOutlineOn = false;
    merged.settings.numericTime = true;
    merged.settings.progressBarOn = true;
    merged.settings.progressScale = "auto";
    merged.settings.welcomeShown = true;
    merged.settings.currentTaskId = 1;
    // Use the merged object directly, NOT getQuest(): during initial load the
    // module-level `db` is still in its TDZ, so getQuest() would throw and wipe
    // all saved progress back to default on every page refresh.
    const quest = currentQuestFrom(merged.settings.currentLevelId, merged.settings.currentTaskId);
    merged.settings.accuracy = quest.accuracy;
    if (merged.quest.currentN === undefined) merged.quest.currentN = nz(quest.maxAttemptsDefault, -1);
    if (merged.quest.currentToleranceMs === undefined) merged.quest.currentToleranceMs = quest.toleranceMsDefault;
    if (merged.settings.saluteResultSoundsOn === undefined) merged.settings.saluteResultSoundsOn = true;
    // Migration: sessions saved before the `closed` flag existed. Historical
    // sessions (all but the last) are closed by definition. The last session is
    // self-healed to closed if it already reached its window/auto-close limit —
    // otherwise the old "session never actually closed" bug would re-fire the
    // summary on the next attempt.
    merged.sessions.forEach((s) => { if (s.closed === undefined) s.closed = false; });
    const last = merged.sessions[merged.sessions.length - 1];
    if (last && !last.closed && !last.isQuestComplete) {
      const questMatch = last.questLevelId === quest.levelId && last.questTaskId === quest.taskId;
      const inLast = merged.attempts.filter((a) => a.sessionId === last.id).length;
      const baseN = quest.maxAttemptsDefault;
      let limit;
      if (baseN == null) limit = SESSION_AUTO_CLOSE_ATTEMPTS;
      else {
        const step = quest.adaptiveNStep;
        const maxN = baseN + 2 * step;
        const cn = merged.quest.currentN;
        limit = (cn == null || cn <= baseN)
          ? baseN
          : Math.min(maxN, baseN + Math.ceil((cn - baseN) / step) * step);
      }
      if (!questMatch || inLast >= limit) last.closed = true;
    }
    return merged;
  }

  function saveDb() {
    writeStorage(STORAGE_KEY, JSON.stringify(db));
  }

  function browserLocalStorage() {
    try {
      if (!window.localStorage) return null;
      const probe = STORAGE_KEY + ".probe";
      window.localStorage.setItem(probe, "1");
      window.localStorage.removeItem(probe);
      return window.localStorage;
    } catch (_) {
      return null;
    }
  }

  // Fallback when localStorage is blocked: window.name holds a JSON map
  // {key: value}. It used to hold ONE value, so the identity and the game
  // data overwrote each other and the device got a new player id on every
  // load (measurement version fix, 2026-10-14).
  function fallbackMap() {
    if (!window.name || !window.name.startsWith(FALLBACK_STORAGE_PREFIX)) return {};
    try {
      const map = JSON.parse(window.name.slice(FALLBACK_STORAGE_PREFIX.length));
      return map && typeof map === "object" && !Array.isArray(map) && !map.settings ? map : {};
    } catch (_) { return {}; }
  }

  function readStorage(key) {
    const storage = browserLocalStorage();
    if (storage) return storage.getItem(key);
    const map = fallbackMap();
    return Object.prototype.hasOwnProperty.call(map, key) ? map[key] : null;
  }

  function writeStorage(key, value) {
    const storage = browserLocalStorage();
    if (storage) {
      storage.setItem(key, value);
      return;
    }
    // Some embedded browser test surfaces disable localStorage. Keep the app usable
    // in that restricted context while normal browsers use localStorage above.
    const map = fallbackMap();
    map[key] = value;
    window.name = FALLBACK_STORAGE_PREFIX + JSON.stringify(map);
  }

  // ── Player identity (anonymous, device-local) ────────────────────────────
  // A random id + a paired secret token, generated once and kept forever in
  // this browser. The id is the leaderboard row key; the token proves later
  // requests (rename, sync) come from the same device, so a stranger who
  // learns someone's id can't rename them or feed them fake attempts.
  function makeUuid() {
    if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
    // Old-browser fallback (no crypto.randomUUID before ~2022 Safari/Chrome).
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  function loadIdentity() {
    try {
      const parsed = JSON.parse(readStorage(IDENTITY_KEY));
      if (parsed && parsed.playerId && parsed.syncToken) return parsed;
    } catch (_) { /* fall through to a fresh identity */ }
    const fresh = {
      playerId: makeUuid(),
      syncToken: makeUuid().replace(/-/g, ""),
      displayName: null,
      lastSyncedAttemptId: 0
    };
    writeStorage(IDENTITY_KEY, JSON.stringify(fresh));
    return fresh;
  }

  function saveIdentity() {
    writeStorage(IDENTITY_KEY, JSON.stringify(identity));
  }

  function ensureAudio() {
    if (!audioCtx) {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (Ctx) audioCtx = new Ctx();
    }
    if (audioCtx && audioCtx.state === "suspended") audioCtx.resume();
    return audioCtx;
  }

  function playTone(frequency, durationMs, volume = 0.05, type = "square") {
    if (!db.settings.soundOn) return;
    const ctx = ensureAudio();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = frequency;
    gain.gain.value = volume;
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + durationMs / 1000);
    osc.stop(ctx.currentTime + durationMs / 1000);
  }

  function startMetronome() {
    stopMetronome();
    const interval = Math.max(1, Math.round(1000 / Math.max(1, db.settings.beatsPerSecond || 2)));
    const tickFeedback = () => {
      if (db.settings.soundOn) playTone(1050, 35, 0.035);
      if (db.settings.vibrationOn && navigator.vibrate) navigator.vibrate(12);
    };
    if (!db.settings.soundOn && !db.settings.vibrationOn) return;
    tickFeedback();
    metronomeId = window.setInterval(tickFeedback, interval);
  }

  function stopMetronome() {
    if (metronomeId) window.clearInterval(metronomeId);
    metronomeId = 0;
    if (navigator.vibrate) navigator.vibrate(0);
  }

  function playNack() {
    playTone(180, 200, 0.08, "sawtooth");
    if (db.settings.vibrationOn && navigator.vibrate) navigator.vibrate([35, 30, 35]);
  }

  // 2026-09-10 native parity (item 6 in native_changes_2026-09-09_to_10.md):
  // native's MediaPlayer could be silently garbage-collected mid-playback
  // because nothing but a local variable held it — root-caused and fixed
  // there by keeping played instances in a list until their own completion
  // callback releases them. HTMLAudioElement isn't known to fail the exact
  // same way (browsers keep an internal reference while a clip has pending
  // playback work), but the *symptom* Alex described ("plays for a few ms,
  // then stops") is the kind of thing that could in principle show up here
  // too — this costs nothing and removes any theoretical risk of it.
  const keepAliveAudio = new Set();
  function keepAudioAlive(instance) {
    keepAliveAudio.add(instance);
    const release = () => { keepAliveAudio.delete(instance); };
    instance.addEventListener("ended", release, { once: true });
    instance.addEventListener("error", release, { once: true });
    // Safety net in case neither ever fires — don't hold the reference forever.
    setTimeout(release, 20000);
  }

  function playOnce(src) {
    try {
      const a = new Audio(src);
      keepAudioAlive(a);
      const attempt = () => a.play().catch((err) => {
        // One retry: covers a transient "interrupted by a pending play
        // request" rejection, which is otherwise silent and easy to read
        // as "the sound just didn't play".
        try { a.play().catch((err2) => logPlayRejection(src, err2)); } catch (_) { /* ignore */ }
      });
      attempt();
    } catch (_) { /* ignore */ }
  }

  function playSuccessSound() {
    if (!db.settings.saluteResultSoundsOn) return;
    playOnce(HOORAY_SRC);
  }

  function playSorrySound() {
    if (!db.settings.saluteResultSoundsOn) return;
    playOnce(SORRY_SRC);
  }

  // Measurement: the level alone picks the pseudo-quest (taskId is always 1).
  function currentQuestFrom(levelId) {
    return QUESTS.find((quest) => quest.levelId === levelId) || QUESTS[0];
  }

  function getQuest() {
    return currentQuestFrom(db.settings.currentLevelId, db.settings.currentTaskId);
  }

  function getTasksCountForLevel(levelId) {
    return QUESTS.filter((quest) => quest.levelId === levelId).length;
  }

  function formatTime(ms, accuracy = db.settings.accuracy) {
    const seconds = ms / 1000;
    if (accuracy === ACCURACY.SECONDS) return String(Math.trunc(seconds));
    if (accuracy === ACCURACY.DECISECONDS) return seconds.toFixed(1).padStart(3, "0");
    if (accuracy === ACCURACY.CENTISECONDS) return seconds.toFixed(2).padStart(4, "0");
    return seconds.toFixed(3);
  }

  function accuracyStepMs(quest = getQuest()) {
    if (quest.accuracy === ACCURACY.SECONDS) return 1000;
    if (quest.accuracy === ACCURACY.DECISECONDS) return 100;
    if (quest.accuracy === ACCURACY.CENTISECONDS) return 10;
    return 1;
  }

  function roundToAccuracy(seconds, accuracy) {
    const factor = accuracy === 0 ? 1 : accuracy === 1 ? 10 : accuracy === 2 ? 100 : 1000;
    return Math.round(seconds * factor) / factor;
  }

  function signedErrorMs(attempt) {
    return attempt.endTime - attempt.startTime - AIM_TIME;
  }

  function displayErrorSeconds(durationMs, accuracy = db.settings.accuracy) {
    const error = Number(formatTime(durationMs, accuracy)) - Number(formatTime(AIM_TIME, accuracy));
    if (accuracy === ACCURACY.SECONDS) return Math.round(error);
    if (accuracy === ACCURACY.DECISECONDS) return Math.round(error * 10) / 10;
    if (accuracy === ACCURACY.CENTISECONDS) return Math.round(error * 100) / 100;
    return Math.round(error * 1000) / 1000;
  }

  function isTrueThree(attempt) {
    const accuracy = attempt.accuracy;
    return roundToAccuracy(AIM_TIME / 1000, accuracy) ===
      roundToAccuracy((attempt.endTime - attempt.startTime) / 1000, accuracy);
  }

  // Native AnalyticsUseCase.countTrueThree port (Alex 2026-09-25) -- the
  // ANALYTICS "WINS" metric, tolerance-aware (counts attempts within the
  // attempt's own recorded toleranceMs of the rounded aim, not just exact
  // zero error like isTrueThree/"PRECISE"). resolutionFloorMs is 0 for every
  // accuracy in the current native build, so it is omitted here (not lost --
  // there is nothing to add).
  function countTrueThreeTol(attempts) {
    return attempts.filter((a) => {
      const factor = Math.pow(10, a.accuracy);
      const aimRounded = Math.round(AIM_TIME / 1000 * factor);
      const durRounded = Math.round((a.endTime - a.startTime) / 1000 * factor);
      const roundedErrMs = (durRounded - aimRounded) * (1000 / factor);
      const winTol = a.toleranceMs || 0;
      return Math.abs(roundedErrMs) <= winTol + 0.5;
    }).length;
  }

  function isQuestWinAttempt(attempt) {
    const err = displayErrorSeconds(attempt.endTime - attempt.startTime, attempt.accuracy);
    return Math.abs(err * 1000) <= (attempt.toleranceMs || 0) + 0.0005;
  }

  function startOfToday() {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    return date.getTime();
  }

  // Calendar week from Monday (Alex 2026-10-06) -- the same week as in the
  // sessions tree, matches native PeriodFilter.WEEK. It used to be a rolling
  // 7-day window, so the filter's week and the tree's week did not match.
  function startOfWeek() {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
    return date.getTime();
  }

  function startOfMonth() {
    const date = new Date();
    date.setDate(1);
    date.setHours(0, 0, 0, 0);
    return date.getTime();
  }

  function periodStart(period) {
    if (period === "TODAY") return startOfToday();
    if (period === "WEEK") return startOfWeek();
    if (period === "MONTH") return startOfMonth();
    return 0;
  }

  function currentSession() {
    const open = db.sessions[db.sessions.length - 1];
    // `closed` = session finished for ANY reason (quest done, window fail, or
    // auto-close). `isQuestComplete` alone is not enough: a window-fail closes a
    // session with isQuestComplete=false, and without the `closed` flag it would
    // stay "open" and re-fire the session-end summary on every further attempt.
    if (!open || open.closed || open.isQuestComplete) return null;
    const quest = getQuest();
    if (open.questLevelId !== quest.levelId || open.questTaskId !== quest.taskId) return null;
    return open;
  }

  function attemptsBySessionId(sessionId) {
    return db.attempts.filter((attempt) => attempt.sessionId === sessionId).sort((a, b) => a.startTime - b.startTime);
  }

  function attemptsForPeriod(period) {
    if (period === "SESSION") {
      const session = currentSession();
      return session ? attemptsBySessionId(session.id) : [];
    }
    if (period === "ALL_TIME") return [...db.attempts].sort((a, b) => a.startTime - b.startTime);
    const from = periodStart(period);
    const touching = new Set(db.attempts.filter((attempt) => attempt.startTime >= from).map((attempt) => attempt.sessionId));
    return db.attempts
      .filter((attempt) => touching.has(attempt.sessionId))
      .sort((a, b) => a.startTime - b.startTime);
  }

  function sessionsForPeriod(period) {
    if (period === "SESSION") {
      const session = currentSession();
      return session ? [session] : [];
    }
    if (period === "ALL_TIME") return [...db.sessions];
    const ids = new Set(attemptsForPeriod(period).map((attempt) => attempt.sessionId));
    return db.sessions.filter((session) => ids.has(session.id));
  }

  function getEffectiveN() {
    const quest = getQuest();
    const baseline = quest.maxAttemptsDefault;
    if (baseline == null) return -1;
    if (db.quest.currentN === -1) db.quest.currentN = baseline;
    const max = baseline + 2 * quest.adaptiveNStep;
    let value = db.quest.currentN <= baseline
      ? baseline
      : baseline + Math.ceil((db.quest.currentN - baseline) / quest.adaptiveNStep) * quest.adaptiveNStep;
    value = Math.min(value, max);
    db.quest.currentN = value;
    return value;
  }

  function getEffectiveToleranceMs() {
    const quest = getQuest();
    if (!quest.adaptiveT) return db.quest.currentToleranceMs || 0;
    const step = accuracyStepMs(quest);
    const max = quest.toleranceMsDefault + quest.maxToleranceSteps * step;
    let stored = db.quest.currentToleranceMs || 0;
    let value = stored <= quest.toleranceMsDefault
      ? quest.toleranceMsDefault
      : quest.toleranceMsDefault + Math.ceil((stored - quest.toleranceMsDefault) / step) * step;
    value = Math.min(value, max);
    db.quest.currentToleranceMs = value;
    return value;
  }

  function isSubquest() {
    const quest = getQuest();
    const baseN = nz(quest.maxAttemptsDefault, Number.MAX_SAFE_INTEGER);
    return (quest.adaptiveN && getEffectiveN() > baseN) ||
      (quest.adaptiveT && getEffectiveToleranceMs() > quest.toleranceMsDefault);
  }

  // Indulgence step (1/2/3…) of the current live subquest; 0 = baseline. Ported
  // from native MainViewModel.subquestStep() (Alex 2026-08-14/15) — used to
  // build the "SubQuest#L.T(step)" tag in cheer-up bubbles (cheerQuestTag()).
  function subquestStep() {
    const quest = getQuest();
    if (quest.adaptiveT) {
      const stepMs = accuracyStepMs(quest);
      const over = getEffectiveToleranceMs() - quest.toleranceMsDefault;
      return stepMs > 0 ? Math.max(0, Math.floor(over / stepMs)) : 0;
    }
    if (quest.adaptiveN) {
      const baseN = quest.maxAttemptsDefault;
      if (baseN == null) return 0;
      const step = quest.adaptiveNStep;
      const over = getEffectiveN() - baseN;
      return step > 0 ? Math.max(0, Math.floor(over / step)) : 0;
    }
    return 0;
  }

  function resetQuestAdaptiveParams(quest) {
    db.quest.currentN = nz(quest.maxAttemptsDefault, -1);
    db.quest.currentToleranceMs = quest.toleranceMsDefault;
    db.quest.windowAttempts = 0;
    db.quest.windowWins = 0;
    db.quest.windowMaxErrorMs = 0;
    db.quest.windowLastWinIndex = 0;
    db.quest.windowStartMs = Date.now();
    db.quest.wasIndulged = false;
  }

  function questParamsLabel() {
    const quest = getQuest();
    const parts = [formatTime(AIM_TIME, quest.accuracy)];
    const tolerance = getEffectiveToleranceMs();
    if (quest.adaptiveT && tolerance > 0) parts.push("+- " + formatTime(tolerance, quest.accuracy));
    if (quest.adaptiveN && getEffectiveN() > 0) parts.push("in " + getEffectiveN() + " attempts");
    return parts.join(" ");
  }

  function makeSession(startTime) {
    const quest = getQuest();
    const session = {
      id: db.ids.session++,
      startTime,
      endTime: startTime,
      isQuestComplete: false,
      closed: false,
      questLevelId: quest.levelId,
      questTaskId: quest.taskId,
      activeTimeMs: 0
    };
    db.sessions.push(session);
    db.face = null;
    lastDitherCellIndex = null; // new session — do not inherit cell/value
    pbDisplayHalfMs = null;     // new session — re-fit the progress-bar scale
    startPlayTicker();
    return session;
  }

  function startPlayTicker() {
    if (view !== "play" || !currentSession() || playTickStart) return;
    if (document.hidden || appFrameHidden) return; // not on screen — not active
    playTickStart = Date.now();
  }

  function flushPlayTicker() {
    if (!playTickStart) return;
    const session = currentSession();
    const elapsed = Date.now() - playTickStart;
    playTickStart = 0;
    if (session && elapsed > 0) {
      session.activeTimeMs = (session.activeTimeMs || 0) + elapsed;
      session.endTime = Date.now();
      saveDb();
    }
  }

  function recalcScm() {
    const ordered = [...db.attempts].sort((a, b) => a.startTime - b.startTime);
    // Edge attempts (first / last) use themselves as the missing neighbour (native).
    ordered.forEach((attempt, i) => {
      const prev = ordered[i - 1] || attempt;
      const next = ordered[i + 1] || attempt;
      attempt.scm = calculateScm(prev, attempt, next);
    });
  }

  function calculateScm(prev, curr, next) {
    const tPrevAbs = prev.endTime - prev.startTime;
    const tCurrAbs = curr.endTime - curr.startTime;
    const tNextAbs = next.endTime - next.startTime;
    const tPrev = Math.abs(tPrevAbs - AIM_TIME);
    const tCurr = Math.abs(tCurrAbs - AIM_TIME);
    const tNext = Math.abs(tNextAbs - AIM_TIME);
    const denominator = 3 * tCurr + tPrev + tNext +
      Math.abs(tCurrAbs - tNextAbs) +
      Math.abs(tCurrAbs - tPrevAbs) -
      Math.abs(tNextAbs - tPrevAbs) / 3 +
      1;
    const levelFactor = Math.pow(10, curr.accuracy - 3);
    return 99 / denominator * levelFactor;
  }

  function registerAttempt(startTime, endTime) {
    let session = currentSession();
    if (!session) session = makeSession(startTime);
    const duration = endTime - startTime;
    const attempt = {
      id: db.ids.attempt++,
      sessionId: session.id,
      startTime,
      endTime,
      deflection: null,
      accuracy: getQuest().accuracy,
      hand: db.settings.selectedHand,
      scm: null,
      toleranceMs: getEffectiveToleranceMs()
    };
    db.attempts.push(attempt);
    session.endTime = endTime;
    recalcScm();
    const result = updateQuestWindow(attempt);
    updateFaceState(attempt);
    updateErrorPattern(attempt.sessionId);
    // Cheer-up bubble auto-dismisses on the next attempt (native cheerUpAttemptsLeft=1).
    if (cheerBubble) {
      cheerBubble.attemptsLeft -= 1;
      if (cheerBubble.attemptsLeft <= 0) dismissCheerBubble();
    }
    saveDb();
    scheduleSync();
    return { attempt, result };
  }

  // ── Leaderboard sync (best-effort, never blocks gameplay) ────────────────
  // Every recorded attempt is already saved locally; this just mirrors it to
  // the server so the leaderboard can rank it. If the network is down or the
  // backend isn't deployed yet, this silently no-ops and retries on the next
  // attempt — the game itself never depends on it succeeding.
  function pendingAttemptsForSync() {
    const lastId = identity.lastSyncedAttemptId || 0;
    // >= not > : recalcScm() retroactively finalizes an attempt's `scm` the
    // moment it gets a real next-neighbour, which happens exactly once, right
    // after it stops being the newest attempt — i.e. exactly the attempt at
    // `lastId` itself, one sync cycle after it was first sent. Re-including
    // it here (in addition to anything newer) lets that correction reach the
    // server; sync.php upserts on (player_id, local_id) so resending an
    // unchanged attempt is a harmless no-op. Alex 2026-09-03: fixes Ranks
    // "Coins" permanently disagreeing with the app's own total (see TASKS.md).
    return db.attempts.filter((a) => a.id >= lastId).sort((a, b) => a.id - b.id);
  }

  function attemptSyncPayload(attempt) {
    const session = db.sessions.find((s) => s.id === attempt.sessionId);
    return {
      localId: attempt.id,
      sessionId: attempt.sessionId,
      levelId: session ? session.questLevelId : db.settings.currentLevelId,
      taskId: session ? session.questTaskId : db.settings.currentTaskId,
      accuracy: attempt.accuracy,
      durationMs: attempt.endTime - attempt.startTime,
      errorMs: signedErrorMs(attempt),
      isWin: isQuestWinAttempt(attempt) ? 1 : 0,
      toleranceMs: attempt.toleranceMs || 0,
      sc: attempt.scm || 0,
      recordedAt: attempt.startTime
    };
  }

  // Measurement version: nothing is sent to the game's server. Storage and the
  // leaderboard are on this device until a server for measurements is chosen.
  function scheduleSync() {
    return;
    clearTimeout(syncTimer);
    // Coalesce rapid-fire attempts (and page-load) into one request instead of
    // hammering the API on every single tap.
    syncTimer = setTimeout(() => performSync(false), 1200);
  }

  function performSync(useBeacon) {
    return;
    if (!identity.playerId) return;
    const allPending = pendingAttemptsForSync();
    const pending = allPending.slice(0, SYNC_BATCH_SIZE);
    const payload = {
      playerId: identity.playerId,
      syncToken: identity.syncToken,
      levelId: db.settings.currentLevelId,
      taskId: db.settings.currentTaskId,
      platform: "web",
      attempts: pending.map(attemptSyncPayload)
    };
    const body = JSON.stringify(payload);
    if (useBeacon) {
      // Page is closing/hiding — fire-and-forget, no response to read. Do NOT
      // advance lastSyncedAttemptId here: we can't confirm delivery, and the
      // server's unique key makes resending the same batch later a safe no-op.
      if (navigator.sendBeacon) {
        navigator.sendBeacon(API_BASE + "sync.php", new Blob([body], { type: "application/json" }));
      }
      return;
    }
    if (syncInFlight) return; // next debounce tick will retry
    syncInFlight = true;
    fetch(API_BASE + "sync.php", { method: "POST", headers: { "Content-Type": "application/json" }, body })
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(String(res.status)))))
      .then((data) => {
        if (pending.length) {
          identity.lastSyncedAttemptId = Math.max(identity.lastSyncedAttemptId || 0, pending[pending.length - 1].id);
        }
        // The server is the single source of truth for the name (the short-
        // name generator and the one-off migration both rename players
        // server-side, with no client involvement) — always adopt whatever
        // it returns, not just on first sync. Do NOT send "name" in the
        // payload above: echoing the locally-cached value on every sync used
        // to stomp a server-side rename right back on the next sync (bug
        // found 2026-09-10, see TASKS.md).
        if (data && data.displayName) identity.displayName = data.displayName;
        saveIdentity();
        // More than one batch was waiting (first-time backfill) — keep going.
        // NOTE: can't call pendingAttemptsForSync() again here to check this —
        // since that filter is id >= lastSyncedAttemptId (not >), the attempt
        // we just sent (now == lastSyncedAttemptId) always matches itself, so
        // that call would always return length > 0 and reschedule forever,
        // polling sync.php every 1.2s even at idle. Compare against the
        // pre-slice count captured above instead: it's only larger than what
        // we just sent when a real backlog remains beyond this one batch.
        // Newly-recorded attempts don't need this path anyway — recordAttempt
        // calls scheduleSync() itself.
        if (allPending.length > pending.length) scheduleSync();
      })
      .catch(() => { /* offline or backend not deployed yet — next attempt retries */ })
      .finally(() => { syncInFlight = false; });
  }

  // Ranks epoch reset (Alex 2026-09-03, "десятая правка" в TASKS.md): tells
  // the server "everything synced under this identity so far is now
  // historical — start a fresh visible Coins/attempts/wins total, but keep
  // every row forever, nothing is deleted." Called once, right when the
  // player erases. Best-effort, same philosophy as sync itself: if this
  // fails (e.g. offline at the exact moment of Erase), the old epoch simply
  // stays active and new post-erase attempts keep accumulating into the
  // OLD (already-stale-looking) Ranks total until the player erases again
  // while online. Known, accepted gap for v1 — no retry queue for this one
  // call yet (unlike normal attempt sync, which does retry indefinitely).
  function requestEpochReset() {
    return;
    if (!identity.playerId) return;
    fetch(API_BASE + "reset_epoch.php", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerId: identity.playerId, syncToken: identity.syncToken })
    }).catch(() => { /* offline — known gap, see TASKS.md */ });
  }

  function updateErrorPattern(sessionId) {
    const attempts = attemptsBySessionId(sessionId);
    const errors = attempts.map((attempt) => displayErrorSeconds(attempt.endTime - attempt.startTime, attempt.accuracy));
    const pauses = [];
    for (let i = 1; i < attempts.length; i += 1) {
      pauses.push(attempts[i].startTime - attempts[i - 1].endTime);
    }
    db.errorPattern = detectErrorPattern(errors, pauses);
    const flags = instantAttemptFlags(attempts);
    db.twinShotVisible = flags.twinShot;
    db.neighborShotVisible = flags.neighborShot;
    db.itWasCloseVisible = flags.itWasClose;
  }

  function updateFaceState(attempt) {
    const sessionId = attempt.sessionId;
    const attempts = attemptsBySessionId(sessionId);
    if (!attempts.length) {
      db.face = null;
      return;
    }
    let kind = "nice";
    if (attempts.length > 1) {
      const last = attempts[attempts.length - 1];
      const prev = attempts[attempts.length - 2];
      kind = Math.abs(displayErrorSeconds(last.endTime - last.startTime, last.accuracy)) <=
        Math.abs(displayErrorSeconds(prev.endTime - prev.startTime, prev.accuracy))
        ? "nice"
        : "sad";
    }
    const prevKind = db.face ? db.face.kind : undefined;
    db.face = {
      kind,
      changed: Boolean(prevKind && prevKind !== kind),
      repeat: Boolean(prevKind && prevKind === kind),
      sessionId,
      attemptId: attempt.id,
      animationConsumed: false,
      tick: Date.now()
    };
  }

  // Measurement version: no quest window. The session is one measurement;
  // the 33rd attempt closes it, records its numbers for the leaderboard and
  // returns { type: "measurement" } so stopTimer() shows the completion card.
  function updateQuestWindow(attempt) {
    const sessionAttempts = attemptsBySessionId(attempt.sessionId);
    if (sessionAttempts.length >= MEASUREMENT_ATTEMPTS) {
      closeSession(attempt.sessionId, true);
      const record = recordMeasurement(attempt.sessionId);
      return { type: "measurement", message: "", record };
    }
    return { type: "none", message: "" };
  }

  // Numbers of one measurement (also the leaderboard row):
  //   lateAvgMs  — arithmetic mean of late errors over MISSES only, ms
  //                (null if no late misses). A hit (3.0 on screen) is left
  //                out even if its raw ms is off (Alex 2026-10-14: "берём
  //                только те попытки, которые помечены как неуспешные");
  //   earlyAvgMs — the same for early misses, as a positive number;
  //   perMinute  — attempts ÷ minutes from the first RUN to the last STOP;
  //   perWin     — attempts ÷ wins (null if no wins). A win is an exact hit
  //                at the level's precision: 3.0 / 3.00 / 3.000.
  // activeMs: time the main screen was open and active during the
  // measurement (session.activeTimeMs, the play ticker) — Alex 2026-10-14:
  // only that time counts for attempts per minute, not the wall clock from
  // the first RUN to the last STOP (Ranks, Settings, a background tab or the
  // landing page do not count).
  function measurementStats(attempts, activeMs) {
    const errs = attempts.filter((a) => !isQuestWinAttempt(a)).map(signedErrorMs);
    const late = errs.filter((e) => e > 0);
    const early = errs.filter((e) => e < 0).map((e) => -e);
    const mean = (arr) => (arr.length ? arr.reduce((sum, v) => sum + v, 0) / arr.length : null);
    const wins = attempts.filter(isQuestWinAttempt).length;
    return {
      attempts: attempts.length,
      wins,
      lateAvgMs: mean(late),
      earlyAvgMs: mean(early),
      perMinute: activeMs > 0 ? attempts.length / (activeMs / 60000) : null,
      perWin: wins ? attempts.length / wins : null
    };
  }

  function recordMeasurement(sessionId) {
    const attempts = attemptsBySessionId(sessionId);
    if (!attempts.length) return null;
    if (!db.ids.measurement) db.ids.measurement = db.measurements.length + 1;
    const record = {
      id: db.ids.measurement++,
      sessionId,
      levelId: getQuest().levelId,
      accuracy: attempts[0].accuracy,
      startTime: attempts[0].startTime,
      endTime: attempts[attempts.length - 1].endTime,
      ...measurementStats(attempts, (db.sessions.find((x) => x.id === sessionId) || {}).activeTimeMs || 0)
    };
    db.measurements.push(record);
    return record;
  }

  function checkVictory(sessionAttempts) {
    const quest = getQuest();
    if (quest.victory.condition === "MEASURE") return false;
    const wins = sessionAttempts.map(isQuestWinAttempt);
    const needed = quest.victory.count;
    if (quest.victory.condition === "ROW") {
      let streak = 0;
      for (const win of wins) {
        streak = win ? streak + 1 : 0;
        if (streak >= needed) return true;
      }
      return false;
    }
    return wins.filter(Boolean).length >= needed;
  }

  function onQuestWindowFailure(quest) {
    const baseN = nz(quest.maxAttemptsDefault, Number.MAX_SAFE_INTEGER);
    const baseT = quest.toleranceMsDefault;
    const stepMs = accuracyStepMs(quest);
    const nMax = quest.adaptiveN ? quest.maxAttemptsDefault + 2 * quest.adaptiveNStep : 0;
    const tMax = quest.adaptiveT ? baseT + quest.maxToleranceSteps * stepMs : 0;
    const effNBefore = getEffectiveN();
    const effTBefore = getEffectiveToleranceMs();
    // FailCard data (Alex 2026-09-25, native MainViewModel.onQuestWindowFailure
    // port) -- phase description + window stats + face time-range, captured
    // BEFORE the N/t bump below (same ordering as native).
    const prevPhaseHuman = describePhase(quest, currentWindowLimit(), effTBefore);
    const failAttemptsBefore = db.quest.windowAttempts;
    const failWinsBefore = db.quest.windowWins;
    const failFromMs = db.quest.windowStartMs || 0;
    const failToMs = Date.now();
    const wasAtBaseline = (!quest.adaptiveN || effNBefore <= baseN) &&
      (!quest.adaptiveT || effTBefore <= baseT);
    const wasAtCap = (quest.adaptiveN && effNBefore >= nMax) || (quest.adaptiveT && effTBefore >= tMax);
    if (quest.adaptiveN) db.quest.currentN = Math.min(nMax, effNBefore + quest.adaptiveNStep);
    if (quest.adaptiveT) db.quest.currentToleranceMs = Math.min(tMax, effTBefore + stepMs);
    db.quest.wasIndulged = true;
    const newPhaseHuman = describePhase(quest, currentWindowLimit(), getEffectiveToleranceMs());
    db.quest.windowAttempts = 0;
    db.quest.windowWins = 0;
    db.quest.windowMaxErrorMs = 0;
    db.quest.windowLastWinIndex = 0;
    db.quest.windowFirstWinIndex = 0;
    db.quest.windowStartMs = Date.now();
    const nowAtCap = (quest.adaptiveN && db.quest.currentN >= nMax) ||
      (quest.adaptiveT && db.quest.currentToleranceMs >= tMax);
    // Native trigger selection (MainViewModel onQuestWindowFailure).
    pendingTrigger = wasAtCap ? "AFTER_SUBQUEST_STALLED"
      : nowAtCap ? "AFTER_SUBQUEST_CAPPED"
        : wasAtBaseline ? "AFTER_SUBQUEST_ENTER"
          : "AFTER_SUBQUEST_DEEPER";
    pendingFailCard = {
      trigger: pendingTrigger,
      attempts: failAttemptsBefore,
      wins: failWinsBefore,
      prevPhaseDescription: prevPhaseHuman,
      newPhaseDescription: newPhaseHuman,
      levelId: quest.levelId,
      fromMs: failFromMs,
      toMs: failToMs
    };
  }

  function onQuestWindowSuccess(quest, sessionId) {
    const currentN = getEffectiveN();
    const currentT = getEffectiveToleranceMs();
    const baseN = nz(quest.maxAttemptsDefault, Number.MAX_SAFE_INTEGER);
    const baseT = quest.toleranceMsDefault;
    const atBaselineN = !quest.adaptiveN || currentN <= baseN;
    const atBaselineT = !quest.adaptiveT || currentT <= baseT;
    const worstWinErrorMs = db.quest.windowMaxErrorMs;
    const lastWinIndex = db.quest.windowLastWinIndex;
    const firstWinIndex = db.quest.windowFirstWinIndex;
    // Native parity fix (Alex 2026-09-25): the GAP between the window's
    // first and last win (native's winSpan -- MainViewModel.onQuestWindowSuccess,
    // Alex 2026-08-23: "уровень определяется количеством попыток между
    // успешными выбиваниями") -- NOT lastWinIndex's raw position from the
    // window's start. A late-starting streak still proves baseline quality
    // if the wins themselves landed close together.
    const winSpan = (firstWinIndex >= 1 && firstWinIndex <= lastWinIndex)
      ? lastWinIndex - firstWinIndex + 1
      : lastWinIndex;

    db.quest.windowAttempts = 0;
    db.quest.windowWins = 0;
    db.quest.windowMaxErrorMs = 0;
    db.quest.windowLastWinIndex = 0;
    db.quest.windowFirstWinIndex = 0;
    db.quest.windowStartMs = Date.now();

    const wasAtBaselineBeforeWin = atBaselineN && atBaselineT;
    const wasIndulgedBeforeWin = db.quest.wasIndulged;
    const baselineQualityT = !quest.adaptiveT || worstWinErrorMs <= baseT;
    const baselineQualityN = !quest.adaptiveN || (winSpan >= 1 && winSpan <= baseN);
    if ((atBaselineN && atBaselineT) || (baselineQualityT && baselineQualityN)) {
      if (quest.adaptiveN) db.quest.currentN = baseN;
      if (quest.adaptiveT) db.quest.currentToleranceMs = baseT;
      flushPlayTicker();
      // Snapshot greeting stats from the COMPLETED quest before advancing.
      const greeting = buildGreetingStats(quest, sessionId);
      const isLastTask = quest.taskId >= getTasksCountForLevel(quest.levelId);
      const isLastLevel = quest.levelId >= 3;
      const isAllDone = isLastLevel && isLastTask;
      // Session is deliberately left OPEN here (2026-08-23, Alex: "текст
      // квеста, цели, стрелы обновляется сейчас сразу после выбивания, а
      // должны меняться только после промежуточного поздравительного
      // экрана") — closing it now (the old `advanceQuest()` did, even
      // though its own comment claimed otherwise) made `currentSession()`
      // return null the instant the win registered, which zeroed `landed`/
      // `sessionAttempts` on the very next render: the just-completed
      // targets flipped back to unhit and the "N/M · left K/L" line reset
      // to 0, one full render before the HOORAY tap even happened — while
      // the quest POINTER correctly stayed deferred, the on-screen state
      // it was drawing from didn't. Real fix: closeSession() now happens
      // in onTakeNext(), in the same place — and at the same moment — the
      // pointer itself advances, so everything on screen holds the
      // just-won quest's fully-landed state until GreetingView is
      // dismissed, then flips to the next quest all at once.
      const next = peekNextQuest(quest);       // "what's next", not committed yet
      const arrival = next || quest;           // for display purposes only
      greeting.isLevelComplete = isLastTask;
      greeting.isGameComplete = isAllDone;
      greeting.nextPrecisionLabel = isAllDone ? "" : precisionLabel(arrival.accuracy);
      greeting.congrats = quest.congratulationDescription || "";
      greeting.arrivalLevelId = isAllDone ? quest.levelId : arrival.levelId;
      greeting.arrivalTaskId = isAllDone ? quest.taskId : arrival.taskId;
      // Carried through to onTakeNext() (GreetingView-dismiss equivalent),
      // which actually calls closeSession(sessionId, true) +
      // commitQuestAdvance(pendingNext, pendingQuest).
      greeting.sessionId = sessionId;
      greeting.pendingNext = next ? { levelId: next.levelId, taskId: next.taskId } : null;
      greeting.pendingQuest = { levelId: quest.levelId, taskId: quest.taskId };
      db.greeting = greeting;
      awaitingHurray = true;
      pendingTrigger = isAllDone ? "AFTER_GAME"
        : isLastTask ? "AFTER_LEVEL"
          : !wasAtBaselineBeforeWin ? "AFTER_SUBQUEST_TO_BASELINE"
            : wasIndulgedBeforeWin ? "AFTER_SIMPLIFIED_QUEST_DONE"
              : "AFTER_QUEST";
      return { type: "quest", message: "" };
    }

    // Rule 3: subquest win, not baseline-quality → jump straight to the
    // TIGHTEST rung the actual performance qualifies for, not just one notch
    // back. A player who wins with far better precision than the current
    // indulgence demands (e.g. landing exact 0-error hits while 5 steps of
    // tolerance are still active) has effectively already cleared several
    // stricter rungs at once — capping them at a token one-step improvement
    // ignored that and made them re-earn ground they'd already covered.
    // Still guarantees at least a one-step improvement when performance is
    // only borderline-better than the current rung.
    // Native parity fix (Alex 2026-09-25, "запомни и следуй этому правилу"):
    // exact port of MainViewModel.onQuestWindowSuccess's Rule 3
    // (MainViewModel.kt ~1714-1739) -- jump to the level actually EARNED by
    // this window's performance, MINUS one, floored at baseline. The
    // previous formula here (`Math.min(currentT - step, sufficientT)`)
    // silently capped every success at a ONE-STEP improvement no matter how
    // good the performance was -- e.g. 3 clean wins with worst error exactly
    // one step above baseline landed back on the same one-step-above rung
    // instead of jumping straight to true baseline, which is exactly the
    // "3.01 three times → still 3.01 next" report. Native's `neededLevel-1`
    // can jump MULTIPLE levels in a single success; this now matches it.
    if (quest.adaptiveN && currentN > baseN) {
      const step = quest.adaptiveNStep;
      const overBase = Math.max(0, winSpan - baseN);
      const neededLevel = Math.ceil(overBase / step);
      const newLevel = Math.max(0, neededLevel - 1);
      // Native fallback: index not tracked (0) -> safe one-step-back.
      const newN = lastWinIndex >= 1 ? baseN + newLevel * step : currentN - step;
      db.quest.currentN = Math.min(currentN, Math.max(baseN, newN));
    }
    if (quest.adaptiveT && currentT > baseT) {
      const step = accuracyStepMs(quest);
      const neededLevel = Math.ceil(worstWinErrorMs / step);
      const newLevel = Math.max(0, neededLevel - 1);
      const newT = baseT + newLevel * step;
      db.quest.currentToleranceMs = Math.min(currentT, Math.max(baseT, newT));
    }
    closeSession(sessionId, false);
    pendingTrigger = "AFTER_SUBQUEST_STEPBACK";
    return { type: "contract", message: "" };
  }

  // Level names for the measurement version (Settings, log, leaderboard).
  function levelPrecisionName(levelId) {
    if (levelId === 2) return "Hundredths";
    if (levelId === 3) return "Thousandths";
    return "Tenths";
  }

  function precisionLabel(accuracy) {
    if (accuracy === ACCURACY.DECISECONDS) return "tenths of a second";
    if (accuracy === ACCURACY.CENTISECONDS) return "hundredths of a second";
    if (accuracy === ACCURACY.MILLISECONDS) return "thousandths of a second";
    return "seconds";
  }

  // Greeting stats built from the completed quest's session + level history.
  function buildGreetingStats(quest, sessionId) {
    const base = buildCompletionOverlay(quest, sessionId);
    const sessionAttempts = attemptsBySessionId(sessionId);
    const levelSessionIds = new Set(db.sessions.filter((s) => s.questLevelId === quest.levelId).map((s) => s.id));
    const levelAttempts = db.attempts.filter((a) => levelSessionIds.has(a.sessionId));
    base.sessionAttempts = sessionAttempts.length;
    base.sessionWins = sessionAttempts.filter(isQuestWinAttempt).length;
    base.winRatePct = sessionAttempts.length ? Math.round(base.sessionWins / sessionAttempts.length * 100) : 0;
    base.levelAttempts = levelAttempts.length;
    base.levelTrueThrees = levelAttempts.filter(isTrueThree).length;
    base.sessionId = sessionId;
    // Interpretation table unlocks from L2.2 (native).
    base.interpUnlocked = quest.levelId >= 3 || (quest.levelId === 2 && quest.taskId >= 2);
    return base;
  }

  // ── GreetingView "session vs history" interpretation (native, L2.2+) ────────
  function interpParams(attempts, gapMin) {
    const errs = attempts.map(signedErrorMs);
    const abs = errs.map(Math.abs);
    const early = errs.filter((v) => v < 0).map(Math.abs);
    const late = errs.filter((v) => v > 0);
    let shrink = 0, pairs = 0;
    for (let i = 1; i < abs.length; i += 1) { pairs += 1; if (abs[i] < abs[i - 1]) shrink += 1; }
    return {
      precision: abs.length ? geometricMean(abs) : -1,       // lower better
      lucky: nz(luckyIndexOf(attempts), -1),                   // higher better
      excitation: early.length ? geometricMean(early) : -1,  // lower better
      inhibition: late.length ? geometricMean(late) : -1,    // lower better
      focus: abs.length ? Math.round(100 * (1 - Math.abs(late.length - early.length) / abs.length)) : -1, // higher
      consistency: pairs ? Math.round(100 * shrink / pairs) : -1, // higher
      pace: gapMin >= 0 ? gapMin : -1                        // lower better
    };
  }

  function avgSessionGapMin() {
    const sorted = [...db.sessions].sort((a, b) => a.startTime - b.startTime);
    if (sorted.length < 2) return -1;
    let sum = 0, n = 0;
    for (let i = 1; i < sorted.length; i += 1) {
      sum += Math.max(0, (sorted[i].startTime - sorted[i - 1].endTime) / 60000);
      n += 1;
    }
    return n ? Math.round(sum / n) : -1;
  }

  function interpVerdict(s, h, lowerBetter) {
    if (s < 0 || h <= 0) return null;
    const d = s - h;
    if (Math.abs(d) <= 0.1 * h) return 0;
    return (lowerBetter ? d < 0 : d > 0) ? 1 : -1;
  }

  function buildInterpTable(sessionId) {
    const sessAtt = attemptsBySessionId(sessionId);
    const sorted = [...db.sessions].sort((a, b) => a.startTime - b.startTime);
    const idx = sorted.findIndex((s) => s.id === sessionId);
    const gapMin = idx > 0 ? Math.max(0, Math.round((sorted[idx].startTime - sorted[idx - 1].endTime) / 60000)) : -1;
    const S = interpParams(sessAtt, gapMin);
    const H = interpParams(db.attempts, avgSessionGapMin());
    const defs = [
      ["Precision", "precision", true, (v) => Math.round(v) + " ms"],
      ["Luck", "lucky", false, (v) => v.toFixed(2)],
      ["Excitation", "excitation", true, (v) => Math.round(v) + " ms"],
      ["Inhibition", "inhibition", true, (v) => Math.round(v) + " ms"],
      ["Focus", "focus", false, (v) => v + "%"],
      ["Consistency", "consistency", false, (v) => v + "%"],
      ["Pace", "pace", true, (v) => v + " min"]
    ];
    const rows = [];
    defs.forEach(([label, key, lower, fmt]) => {
      const s = S[key], h = H[key];
      const verdict = interpVerdict(s, h, lower);
      if (verdict == null) return;
      rows.push({ label, s: fmt(s), h: fmt(h), verdict });
    });
    if (!rows.length) return "";
    // Per-parameter interpretation sentence (native GreetingView banks, condensed).
    const PHRASES = {
      Precision: [" was sharper than usual — your errors ran smaller.", " slipped — your errors ran bigger than usual.", " held right at your usual level."],
      Luck: [" was on your side — more clean hits than chance predicts.", " stepped back — fewer clean hits than usual.", " stayed at its usual level."],
      Excitation: [" eased — your early stops came closer to the mark.", " ran high — you kept firing ahead of the mark.", " stayed at its usual level."],
      Inhibition: [" lifted — your late stops came closer to the mark.", " weighed on you — stops kept landing past the mark.", " stayed at its usual level."],
      Focus: [" was sharper — your misses balanced evenly around the aim.", " wavered — misses kept leaning to one side.", " held steady at its usual level."],
      Consistency: [" grew — error after error kept shrinking.", " sagged — errors grew back instead of shrinking.", " stayed at its usual level."],
      Pace: [" picked up — you came back to play sooner than usual.", " cooled — the pause before this session was longer than usual.", " kept your usual rhythm."]
    };
    const paras = rows.map((r) => {
      const b = PHRASES[r.label] || ["", "", ""];
      const phrase = r.verdict > 0 ? b[0] : r.verdict < 0 ? b[1] : b[2];
      return `<p class="interp-para"><b>${r.label}</b>${phrase}</p>`;
    }).join("");
    const worse = rows.filter((r) => r.verdict < 0);
    const better = rows.filter((r) => r.verdict > 0);
    const conclusion = worse.length === 0 && better.length > 0
      ? "All in all — a strong session. Keep doing what you're doing."
      : worse.length === 0
        ? "All in all — steady as usual, right on your track."
        : worse.length === rows.length
          ? "All in all — a tough session, but you pushed through. Next quest, slow down and rebuild your rhythm."
          : "All in all — next quest, give a little extra care to " + worse.map((r) => r.label.toLowerCase()).join(", ") + "; the rest is already working for you.";
    const head = `<div class="cmp-row cmp-head"><span></span><span>session</span><span>vs</span><span>history</span></div>`;
    const body = rows.map((r) => {
      const cls = r.verdict > 0 ? "win" : r.verdict < 0 ? "early" : "";
      return `<div class="cmp-row"><span class="cmp-label">${r.label}</span><b class="${cls}">${r.s}</b><span class="cmp-vs">vs</span><span class="cmp-hist">${r.h}</span></div>`;
    }).join("");
    return `<div class="interp-table">${head}${body}</div><div class="interp-paras">${paras}</div><p class="interp-conclusion">${conclusion}</p>`;
  }

  // Looks up what the NEXT quest WOULD be, without touching db.settings —
  // used to compute GreetingView stats (arrivalLevelId/nextPrecisionLabel/…)
  // at win time without actually switching the quest pointer yet.
  function peekNextQuest(quest) {
    const index = QUESTS.findIndex((item) => item.levelId === quest.levelId && item.taskId === quest.taskId);
    return QUESTS[index + 1] || null;
  }

  // Switches the top-of-screen quest pointer (currentLevelId/currentTaskId/
  // accuracy + adaptive-param reset) — deferred until GreetingView is
  // dismissed (Alex 2026-07-30 native behavior, ported 2026-08-17): while the
  // green HOORAY! result is showing and while GreetingView is up, the top of
  // the main screen — and the hand-drawn checkmark next to it — must keep
  // showing the quest that was JUST won, not flip to the next one the instant
  // the win registers. Called from onTakeNext() with the same {next, quest}
  // pair peekNextQuest()/onQuestWindowSuccess computed at win time.
  function commitQuestAdvance(next, quest) {
    if (next) {
      db.settings.currentLevelId = next.levelId;
      db.settings.currentTaskId = next.taskId;
      db.settings.accuracy = next.accuracy;
      resetQuestAdaptiveParams(next);
    } else {
      db.gameComplete = true;
      resetQuestAdaptiveParams(quest);
    }
  }

  function closeSession(sessionId, complete) {
    const activeSessionForFlush = currentSession();
    if (activeSessionForFlush && activeSessionForFlush.id === sessionId) flushPlayTicker();
    const session = db.sessions.find((item) => item.id === sessionId);
    if (session) {
      session.isQuestComplete = Boolean(complete);
      session.closed = true;   // truly closed regardless of quest outcome
      session.endTime = Date.now();
    }
  }

  function buildCompletionOverlay(quest, sessionId) {
    const attempts = attemptsBySessionId(sessionId);
    const session = db.sessions.find((item) => item.id === sessionId);
    const sessions = [...db.sessions].sort((a, b) => a.startTime - b.startTime);
    const sessionIdx = sessions.findIndex((item) => item.id === sessionId);
    const prevSession = sessionIdx > 0 ? sessions[sessionIdx - 1] : null;
    const prevAttempts = prevSession ? attemptsBySessionId(prevSession.id) : [];
    const scm = attempts.reduce((sum, attempt) => sum + (attempt.scm || 0), 0);
    const errors = attempts.map((attempt) => Math.abs(signedErrorMs(attempt)));
    const early = attempts.filter((attempt) => signedErrorMs(attempt) < 0).map((attempt) => Math.abs(signedErrorMs(attempt)));
    const late = attempts.filter((attempt) => signedErrorMs(attempt) > 0).map(signedErrorMs);
    const hitRate = attempts.length ? Math.round(attempts.filter(isTrueThree).length / attempts.length * 100) : 0;
    const prevEarly = prevAttempts.filter((attempt) => signedErrorMs(attempt) < 0).map((attempt) => Math.abs(signedErrorMs(attempt)));
    const prevLate = prevAttempts.filter((attempt) => signedErrorMs(attempt) > 0).map(signedErrorMs);
    const prevHitRate = prevAttempts.length ? Math.round(prevAttempts.filter(isTrueThree).length / prevAttempts.length * 100) : 0;
    const earlyMs = Math.round(geometricMean(early));
    const lateMs = Math.round(geometricMean(late));
    return {
      levelId: quest.levelId,
      taskId: quest.taskId,
      playTimeMs: (session && session.activeTimeMs) || 0,
      earnedScm: scm,
      earlyMs,
      lateMs,
      absMs: Math.round(geometricMean(errors)),
      hitRate,
      earlyDeltaMs: prevAttempts.length ? Math.round((geometricMean(prevEarly) || 0) - earlyMs) : 0,
      lateDeltaMs: prevAttempts.length ? Math.round((geometricMean(prevLate) || 0) - lateMs) : 0,
      hitRateDelta: prevAttempts.length ? hitRate - prevHitRate : hitRate
    };
  }

  function geometricMean(values) {
    const positives = values.filter((value) => value > 0);
    if (!positives.length) return 0;
    return Math.exp(positives.reduce((sum, value) => sum + Math.log(value), 0) / positives.length);
  }

  // Shared error-distribution bucketing (used by BOTH the main screen and the
  // analytics distribution card, so the two histograms are identical).
  function buildDistribution(attempts) {
    if (!attempts.length) return { buckets: [], scaleMax: 200 };
    const roundedErrors = attempts.map((attempt) =>
      Math.round(displayErrorSeconds(attempt.endTime - attempt.startTime, attempt.accuracy) * 1000));
    const tick = accuracyStepMs();
    const worstAbs = Math.max(0, ...roundedErrors.map(Math.abs));
    const halfBuckets = Math.max(10, Math.ceil(worstAbs / tick));
    const buckets = Array.from({ length: 2 * halfBuckets + 1 }, (_, idx) => ({
      centerMs: (idx - halfBuckets) * tick, count: 0, winsCount: 0
    }));
    roundedErrors.forEach((error, idx) => {
      const relIdx = Math.floor((error + tick / 2) / tick);
      const bucketIdx = Math.min(Math.max(halfBuckets + relIdx, 0), buckets.length - 1);
      buckets[bucketIdx].count += 1;
      if (isTrueThree(attempts[idx])) buckets[bucketIdx].winsCount += 1;
    });
    return { buckets, scaleMax: halfBuckets * tick + tick / 2 };
  }

  // Histogram markup shared by main + analytics (bars + Y axis + X labels).
  function distributionChartHtml(attempts, accuracy) {
    const { buckets, scaleMax } = buildDistribution(attempts);
    if (!buckets.length) return "";
    const maxBucket = Math.max(5, ...buckets.map((b) => b.count));
    const barPx = HIST_BAR_PX;
    const HIST_H = 54;
    const bars = buckets.map((bucket) => {
      const cls = bucket.centerMs === 0 ? "win" : bucket.centerMs < 0 ? "early" : "late";
      const h = bucket.count ? Math.max(2, bucket.count / maxBucket * HIST_H) : 0;
      const x = scaleMax > 0 ? ((bucket.centerMs / scaleMax + 1) / 2) * 100 : 50;
      return `<span class="bar ${cls}" style="height:${h}px;left:calc(${x}% - ${barPx / 2}px);width:${barPx}px"></span>`;
    }).join("");
    const axisTicks = [-scaleMax, -scaleMax / 2, 0, scaleMax / 2, scaleMax]
      .map((v) => `<span>${Math.abs(v) < 0.0001 ? "0" : `${v > 0 ? "+" : ""}${formatAxis(v, accuracy)}`}</span>`).join("");
    return `<div class="summary-chart">
      <div class="histogram">${histYAxis(maxBucket, HIST_H)}${bars}</div>
      <div class="axis-labels">${axisTicks}</div>
    </div>`;
  }

  function deviationProfile(values) {
    const positives = values.filter((value) => value > 0);
    if (!positives.length) return { medianMs: 0, stdPct: 0 };
    const logs = positives.map(Math.log);
    const mean = logs.reduce((sum, value) => sum + value, 0) / logs.length;
    const variance = logs.reduce((sum, value) => sum + Math.pow(value - mean, 2), 0) / logs.length;
    return { medianMs: Math.exp(mean), stdPct: (Math.exp(Math.sqrt(variance)) - 1) * 100 };
  }

  function mainMetrics(period = db.settings.periodFilter) {
    const attempts = attemptsForPeriod(period);
    if (!attempts.length) return { attempts, redCount: 0, blueCount: 0, trueThreeCount: 0, trueThreeHitRate: 0, geomEarly: 0, geomLate: 0, geomAbs: 0, scaleMax: 200, buckets: [] };
    const errors = attempts.map(signedErrorMs);
    const redCount = errors.filter((value) => value < 0).length;
    const blueCount = errors.filter((value) => value > 0).length;
    const trueThreeCount = attempts.filter(isTrueThree).length;
    const { buckets, scaleMax } = buildDistribution(attempts);
    const durations = attempts.map((attempt) => attempt.endTime - attempt.startTime);
    return {
      attempts,
      redCount,
      blueCount,
      trueThreeCount,
      trueThreeHitRate: trueThreeCount / attempts.length,
      geomEarly: geometricMean(errors.filter((value) => value < 0).map(Math.abs)),
      geomLate: geometricMean(errors.filter((value) => value > 0)),
      geomAbs: geometricMean(durations) - AIM_TIME,
      scaleMax,
      buckets
    };
  }

  function analytics(period = db.settings.periodFilter) {
    const attempts = attemptsForPeriod(period);
    const sessions = sessionsForPeriod(period);
    const allAttempts = attemptsForPeriod("ALL_TIME");
    const allSessions = sessionsForPeriod("ALL_TIME");
    const errors = attempts.map(signedErrorMs);
    const absErrors = errors.map(Math.abs);
    const early = errors.filter((value) => value < 0).map(Math.abs);
    const late = errors.filter((value) => value > 0);
    const sorted = [...errors].sort((a, b) => a - b);
    const p3 = sorted.length ? sorted[Math.min(sorted.length - 1, Math.max(0, Math.trunc(sorted.length * 0.03)))] : 0;
    const p97 = sorted.length ? sorted[Math.min(sorted.length - 1, Math.max(0, Math.trunc(sorted.length * 0.97)))] : 0;
    const periodScm = attempts.reduce((sum, attempt) => sum + (attempt.scm || 0), 0);
    const totalScm = allAttempts.reduce((sum, attempt) => sum + (attempt.scm || 0), 0);
    const totalPlayTimeMs = allSessions.reduce((sum, session) => sum + (session.activeTimeMs || 0), 0);
    const periodPlayTimeMs = sessions.reduce((sum, session) => sum + (session.activeTimeMs || 0), 0);
    // Native port (Alex 2026-09-25): "wins" is now the tolerance-aware count
    // (native periodWins / trueThreeHitRatePct / attemptsPerWin all use this),
    // "precise" is the always-exact count (native periodPrecise) -- two
    // genuinely different metrics in native, not a rename.
    const wins = countTrueThreeTol(attempts);
    const precise = attempts.filter(isTrueThree).length;
    const daysSinceStart = countDaysSinceStart(allSessions);
    const rangeSpan = Math.abs(p3) + Math.abs(p97);
    // Lucky is always normalized "as if level 3" (Alex 2026-07-30, ported from
    // native AnalyticsUseCase.kt): fixed 1ms slot regardless of the attempts'
    // actual level, and a dedicated luckyWins count (|error| < 0.5ms) instead
    // of reusing the true-three win count — keeps this from distorting Hit Rate.
    const slotMs = 1;
    const outcomeSlots = Math.max(1, Math.trunc(rangeSpan / slotMs)) + 1;
    const dispersionProb = 1 / outcomeSlots;
    const luckyWins = attempts.filter((attempt) => Math.abs(signedErrorMs(attempt)) < 0.5).length;
    const empiricalProb = attempts.length ? luckyWins / attempts.length : 0;
    const periodPlayMin = periodPlayTimeMs / 60000;
    const scoredAttemptsCount = attempts.filter((attempt) => (attempt.scm || 0) > 0).length;
    return {
      period,
      attempts,
      sessions,
      totalScm,
      periodScm,
      totalPlayTimeMs,
      periodPlayTimeMs,
      totalAttempts: allAttempts.length,
      totalWins: allAttempts.filter(isTrueThree).length,
      sessionsCount: allSessions.length,
      periodSessionsCount: sessions.length,
      periodWins: wins,
      periodPrecise: precise,
      periodFromMs: attempts.length ? Math.min(...attempts.map((a) => a.startTime)) : 0,
      periodToMs: attempts.length ? Math.max(...attempts.map((a) => a.startTime)) : 0,
      daysSinceStart,
      startDateStr: allSessions.length ? new Date(Math.min(...allSessions.map((s) => s.startTime))).toLocaleDateString(undefined, { day: "2-digit", month: "short", year: "numeric" }) : "-",
      deviationAll: {
        medianMs: attempts.length ? geometricMean(attempts.map((attempt) => attempt.endTime - attempt.startTime)) - AIM_TIME : 0,
        stdPct: deviationProfile(absErrors).stdPct
      },
      deviationEarly: deviationProfile(early),
      deviationLate: deviationProfile(late),
      rangeEarlyMs: p3,
      rangeLateMs: p97,
      trueThreeHitRatePct: attempts.length ? wins / attempts.length * 100 : 0,
      attemptsPerWin: wins ? attempts.length / wins : 0,
      luckyIndex: dispersionProb > 0 ? empiricalProb / dispersionProb : 0,
      topEarly: topDeviations(attempts, true),
      topLate: topDeviations(attempts, false),
      // A win (eased ones too, e.g. 2.999 under tolerance) counts only as green,
      // never also as red/blue (Alex 2026-10-06) -- otherwise the three
      // counters added up to more than the number of attempts.
      earlyCount: attempts.filter((a) => signedErrorMs(a) < 0 && countTrueThreeTol([a]) === 0).length,
      lateCount: attempts.filter((a) => signedErrorMs(a) > 0 && countTrueThreeTol([a]) === 0).length,
      paceOfPlayPerMin: periodPlayMin > 0 ? attempts.length / periodPlayMin : 0,
      // Native bug #1 fix (Alex 2026-08-13): divide only by attempts that have
      // a computed SC (scm > 0), not by the raw attempt count — the freshest
      // attempt in history is by design still unscored (SC computed retroactively
      // when the NEXT attempt lands), and counting it as a real 0 was dragging
      // this average down, especially on short periods (Session/Today).
      paceOfLearning: scoredAttemptsCount > 0 ? periodScm / scoredAttemptsCount : 0,
      practiceDensityMinPerDay: daysSinceStart > 0 ? (totalPlayTimeMs / 60000) / daysSinceStart : 0
    };
  }

  function countDaysSinceStart(sessions) {
    const days = new Set(sessions.map((session) => new Date(session.startTime).toDateString()));
    return days.size;
  }

  function topDeviations(attempts, negated) {
    const map = new Map();
    attempts.forEach((attempt) => {
      const err = signedErrorMs(attempt);
      if (negated ? err >= 0 : err <= 0) return;
      const rounding = attempt.accuracy === 0 ? 1000 : attempt.accuracy === 1 ? 100 : attempt.accuracy === 2 ? 10 : 1;
      const rounded = Math.round(Math.abs(err) / rounding) * rounding;
      if (rounded > 0) map.set(rounded, (map.get(rounded) || 0) + 1);
    });
    return [...map.entries()]
      .filter(([, count]) => count >= 2)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([errorMs, count]) => ({ errorMs: negated ? -errorMs : errorMs, count }));
  }

  // ── Cheer-up engine (ported from native CheerUpMessages.kt v12.87) ──────────
  const NB = " ";

  function describePhase(quest, effectiveN, effectiveTMs) {
    const aim = quest.accuracy === 0 ? "3" : quest.accuracy === 1 ? "3.0" : quest.accuracy === 2 ? "3.00" : "3.000";
    let tolStr = "";
    if (effectiveTMs > 0) {
      const digits = quest.accuracy === 0 ? 0 : quest.accuracy === 1 ? 1 : quest.accuracy === 2 ? 2 : 3;
      tolStr = `${NB}±${NB}${(effectiveTMs / 1000).toFixed(digits)}`;
    }
    const target = aim + tolStr;
    const vc = quest.victory.condition;
    const cnt = quest.victory.count;
    let goal;
    if (vc === "SESSION") goal = cnt === 1 ? "once" : cnt === 2 ? "twice" : "three times";
    else if (vc === "ROW") goal = cnt === 2 ? "twice in a row" : "three times in a row";
    else {
      const n = effectiveN != null && effectiveN > 0 ? effectiveN : nz(quest.maxAttemptsDefault, 33);
      const times = cnt === 1 ? "once" : cnt === 2 ? "twice" : "three times";
      goal = `${times} in ${n}${NB}attempts`;
    }
    return `${target}, ${goal}`;
  }

  // Recap-openers — short "human" lead-ins per trigger, rotated by phraseIndex.
  // Rewritten (native, Alex 2026-08-17): ≤6 words, one sentence — the older
  // versions below were multi-clause prose and read as out of rhythm with the
  // one-line quest block beneath them. All "substance" now lives in the quest
  // block; the opener is just a short nod (see cheerSpecificText/renderCheerBubble).
  const CHEER_OPENERS = {
    AFTER_QUEST: [
      "Quest cleared.",
      "Quest done — nailed it.",
      "That's the quest, cleared.",
      "Quest done — good control."
    ],
    AFTER_LEVEL: [
      "Level cleared.",
      "That's a level down.",
      "Level done — bar moves up."
    ],
    AFTER_GAME: [
      "All levels cleared.",
      "Full game — done."
    ],
    AFTER_SUBQUEST_TO_BASELINE: [
      "Clean threes — ladder skipped.",
      "Landed clean — quest counts as done.",
      "Eased version, cracked clean."
    ],
    AFTER_SIMPLIFIED_QUEST_DONE: [
      "Back at the original — and it fell.",
      "Full loop closed.",
      "Climb paid off — you're there."
    ],
    AFTER_SUBQUEST_ENTER: [
      "Window missed — easing one step.",
      "Not quite — one step easier.",
      "Short this round — widening the mark."
    ],
    AFTER_SUBQUEST_DEEPER: [
      "Still scattering — easing once more.",
      "One more notch of ease.",
      "Loosening the mark again."
    ],
    AFTER_SUBQUEST_CAPPED: [
      "Full ease reached.",
      "As easy as it gets.",
      "Widest the mark goes."
    ],
    AFTER_SUBQUEST_STALLED: [
      "Floor reached — rest up.",
      "Take a break — that's fatigue, not skill.",
      "Nothing left to soften — step away."
    ],
    AFTER_SUBQUEST_STEPBACK: [
      "Good pass — tightening one step.",
      "Nice hits — one notch tighter.",
      "Well done — mark snugs in."
    ]
  };

  function pickOpener(trigger, index) {
    const bank = CHEER_OPENERS[trigger];
    if (!bank || !bank.length) return "";
    return bank[((index % bank.length) + bank.length) % bank.length];
  }

  function cheerFmt(n) {
    return n >= 1000 ? String(n).replace(/\B(?=(\d{3})+(?!\d))/g, " ") : String(n);
  }

  // Typographic pass ported from native tidyLineBreaks() (CheerUpMessages.kt) —
  // glues a space AFTER a short function word to the word that follows it (so
  // "a"/"of"/"in"/etc. never hang alone at a line end), and glues a space
  // BEFORE a lone "—" or "·" to the word before it (so those never start a
  // line). Only touches plain U+0020 spaces — any NBSP already placed on
  // purpose (describePhase's "±" spacing) is left alone since it isn't a
  // regular space to begin with.
  function tidyLineBreaks(text) {
    const glueAfter = new Set([
      "a", "an", "the", "and", "or", "but", "of", "in", "on", "at",
      "to", "for", "per", "as", "by", "is", "are", "was", "it", "no"
    ]);
    const tokens = text.split(" ");
    if (tokens.length < 2) return text;
    const nbsp = " ";
    let out = tokens[0];
    for (let i = 1; i < tokens.length; i++) {
      const prev = tokens[i - 1];
      const curr = tokens[i];
      const prevEndsSentence = /[.!?:]$/.test(prev);
      const gluePrev = !prevEndsSentence && glueAfter.has(prev.toLowerCase().replace(/^[,;]+|[,;]+$/g, ""));
      const glueCurr = curr === "—" || curr === "·";
      out += (gluePrev || glueCurr ? nbsp : " ") + curr;
    }
    return out;
  }

  // "Quest #L.T" / "SubQuest#L.T(step)" tag for the cheer-up bubble — ported
  // from native composeBubbleText() (Alex 2026-08-14/15): computed for EVERY
  // trigger now, not just advance-side ones, and always reflects the live
  // current quest (post any advance/indulgence-step change).
  function cheerQuestTag() {
    const quest = getQuest();
    const step = subquestStep();
    return isSubquest() && step > 0
      ? `SubQuest#${quest.levelId}.${quest.taskId}(${step})`
      : `Quest #${quest.levelId}.${quest.taskId}`;
  }

  function cheerSpecificText(trigger, phase, index, tag, extra = {}) {
    const opener = pickOpener(trigger, index);
    // "{tag}: hit {phase}." — one block, own line, no more **bold** markup
    // (Alex 2026-08-14/15 — see cheerQuestTag/renderCheerBubble: the tag
    // onward is now rendered larger instead of background-highlighted).
    const questBlock = `${tag}: hit ${phase}.`;
    switch (trigger) {
      case "AFTER_QUEST": return `${opener}\n${questBlock}`;
      case "AFTER_LEVEL": return `${opener}\n${questBlock}`;
      case "AFTER_GAME":
        return `${opener} All 15 quests done: ${cheerFmt(extra.attempts || 0)} attempts, ` +
          `${cheerFmt(extra.trueThrees || 0)} clean threes, ${extra.totalTimeStr || ""}. Take a bow! ` +
          "Export and share your Excel (anonymised) at playoftime@gmail.com · " +
          "fresh news at www.playoftime.online";
      case "AFTER_SUBQUEST_ENTER": return `${opener}\n${questBlock} Beat this and the original quest returns.`;
      case "AFTER_SUBQUEST_DEEPER": return `${opener}\n${questBlock}`;
      case "AFTER_SUBQUEST_CAPPED": return `${opener}\n${questBlock}`;
      case "AFTER_SUBQUEST_STALLED": return `${opener} When you come back:\n${questBlock}`;
      case "AFTER_SUBQUEST_STEPBACK": return `${opener}\n${questBlock}`;
      case "AFTER_SUBQUEST_TO_BASELINE": return `${opener}\n${questBlock}`;
      case "AFTER_SIMPLIFIED_QUEST_DONE": return `${opener}\n${questBlock}`;
      default: return "";
    }
  }

  const PRAISE = {
    "1.2": ["Nice start!", "Good opening pass.", "Off the mark cleanly — keep going."],
    "1.3": ["Excellent!", "Well played.", "Coming together.", "That was a clean pass."],
    "1.4": ["You're on a roll!", "Rhythm found.", "Fluent now — nice."],
    "1.5": ["That was impressive.", "Level 1 owned.", "Last one of the tier — clean."],
    "2.1": ["First level done — the stopwatch tightens now.", "Precision moved up. You're in the hundredths league.", "The stopwatch has just grown sharper. You're ready for it."],
    "2.2": ["Quest done — keep it up!", "That's another one done.", "Steady work. Next."],
    "2.3": ["And done! Keep it rolling.", "Solid pass. On we go.", "Ticked off. Next up."],
    "2.4": ["Nicely done! Time to raise the bar.", "Well earned. The next mark tightens.", "Neatly landed — stepping up."],
    "2.5": ["Two in a row? No sweat for you. But can you do three?", "Level 2 nearly closed — one final push.", "Almost through the tier. One quest to go."],
    "3.1": ["Level cleared — welcome to the top league.", "Second level done. Precision goes to the thousandth now.", "You're into the sharpest tier. Enjoy it."],
    "3.2": ["Welcome to the millisecond league.", "First thousandth-level quest, done.", "The smallest units — and you landed them."],
    "3.3": ["You make it look easy.", "Smooth pass. Rare precision.", "Cleanly done at the sharpest scale."],
    "3.4": ["Steady hands! Now the real test.", "Three quests deep at the top tier. Two to go.", "Poise under pressure. Final stretch ahead."],
    "3.5": ["One step from the crown.", "Last quest before the finish line.", "Everything you've built — one quest away from complete."]
  };

  function praiseFor(levelId, taskId) {
    const bank = PRAISE[`${levelId}.${taskId}`] || ["Quest done!"];
    const idx = Math.trunc(Date.now() / 10000);
    return bank[((idx % bank.length) + bank.length) % bank.length];
  }

  function currentWindowLimit() {
    const n = getEffectiveN();
    return n > 0 ? n : null;
  }

  // Progress bar (spec §6.4): window 3.000 ± half. Scale = Auto (per level) or a
  // manual preset from Settings (±ms half-widths, or the wide 2–4 s / 0–4 s ranges).
  // Auto scale: pick a clean half-window that keeps recent results (the beak) in
  // view. Values snap to per-level "nice" steps so the edge labels stay tidy.
  const HALF_STEPS = {
    0: [300, 400, 500, 600, 800, 1000],
    1: [300, 400, 500, 600, 800, 1000],
    2: [30, 50, 80, 100, 150, 200, 300],
    3: [10, 15, 20, 30, 50, 80]
  };

  function autoTargetHalfMs() {
    const acc = getQuest().accuracy;
    const steps = HALF_STEPS[acc] || HALF_STEPS[1];
    const sess = currentSession() ? attemptsBySessionId(currentSession().id) : [];
    const recent = sess.slice(-8).map((a) => Math.abs(signedErrorMs(a)));
    const want = (recent.length ? Math.max(...recent) : 0) * 1.25;
    return steps.find((s) => s >= want) || steps[steps.length - 1];
  }

  function progressWindow() {
    const scale = db.settings.progressScale || "auto";
    if (scale === "2-4s") return { lo: 2000, hi: 4000 };
    if (scale === "0-4s") return { lo: 0, hi: 4000 };
    let half;
    if (scale === "auto") half = pbDisplayHalfMs != null ? pbDisplayHalfMs : autoTargetHalfMs();
    else half = Number(scale) || 600;
    return { lo: AIM_TIME - half, hi: AIM_TIME + half };
  }

  // Native: 10 equal segments — a tick every 10% (edges are the labelled ends,
  // centre 50% carries the green serenity line drawn over it).
  function progressTicks() {
    const pos = [10, 20, 30, 40, 50, 60, 70, 80, 90];
    return pos.map((p) => `<span class="pb-tick" style="left:${p}%"></span>`).join("");
  }

  // Smoothly ease the auto half-window toward its target over ~1 s, updating the
  // edge numbers and marker each frame (only in Auto mode).
  function animateProgressHalf() {
    if ((db.settings.progressScale || "auto") !== "auto") return;
    const target = autoTargetHalfMs();
    if (pbDisplayHalfMs == null || Math.abs(pbDisplayHalfMs - target) < 0.5) {
      pbDisplayHalfMs = target;
      updateProgressBarDom();
      return;
    }
    cancelAnimationFrame(pbAnimRaf);
    const from = pbDisplayHalfMs, start = performance.now(), dur = 1000;
    const step = (t) => {
      const k = Math.min(1, (t - start) / dur);
      const e = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2; // easeInOutQuad
      pbDisplayHalfMs = from + (target - from) * e;
      updateProgressBarDom();
      if (k < 1) pbAnimRaf = requestAnimationFrame(step);
    };
    pbAnimRaf = requestAnimationFrame(step);
  }

  function updateProgressBarDom() {
    const acc = getQuest().accuracy;
    const { lo, hi } = progressWindow();
    const l = document.getElementById("pbNumL");
    const r = document.getElementById("pbNumR");
    if (l) l.textContent = formatTime(lo, acc);
    if (r) r.textContent = formatTime(hi, acc);
    const cNum = document.getElementById("pbNumC");
    if (cNum) { cNum.textContent = formatTime(AIM_TIME, acc); cNum.style.left = (progressCenterFrac() * 100) + "%"; }
    const frac = (progressFrac(timer.elapsed || 0) * 100) + "%";
    const fill = document.getElementById("pbFill");
    if (fill) fill.style.width = frac;
    const beak = document.getElementById("pbBeak");
    if (beak) beak.style.left = frac;
    const c = document.querySelector(".pb-center");
    if (c) c.style.left = (progressCenterFrac() * 100) + "%";
    const zone = document.getElementById("pbWinZone");
    if (zone) {
      const wz = winZoneFrac();
      zone.style.left = (wz.left * 100) + "%";
      zone.style.width = (wz.width * 100) + "%";
    }
  }

  function progressFrac(ms) {
    // Derive the snapped position from displayErrorSeconds() — the exact
    // same function that produces the on-screen digits and the win/loss
    // colour — rather than an independently-rounded ms/cell snap. The two
    // used to sometimes disagree at boundary values (e.g. exactly half a
    // cell) because toFixed() and Math.round(ms/cell)*cell round such
    // values differently in some cases; that let a result shown as an exact
    // win (3.00 in green) land the marker off the centre notch. Routing
    // through displayErrorSeconds makes that impossible: whenever the shown
    // error is exactly 0, snapped is exactly AIM_TIME, on every level.
    const snapped = AIM_TIME + displayErrorSeconds(ms, getQuest().accuracy) * 1000;
    const { lo, hi } = progressWindow();
    return Math.max(0, Math.min(1, (snapped - lo) / (hi - lo)));
  }

  function progressCenterFrac() {
    const { lo, hi } = progressWindow();
    return (AIM_TIME - lo) / (hi - lo);
  }

  // On an indulged (tolerance-widened) quest, anything within ±toleranceMs of
  // the aim counts as a win, not just the exact centre — paint that whole
  // span green on the bar instead of just the thin centre notch. On a
  // baseline (tolerance 0) quest this collapses to zero width, so nothing
  // extra is drawn and the plain centre tick is all that shows, same as before.
  function winZoneFrac() {
    const tol = getEffectiveToleranceMs();
    const { lo, hi } = progressWindow();
    const span = hi - lo;
    const left = Math.max(0, Math.min(1, (AIM_TIME - tol - lo) / span));
    const right = Math.max(0, Math.min(1, (AIM_TIME + tol - lo) / span));
    return { left, width: Math.max(0, right - left) };
  }

  function render() {
    // If a newer build was detected while an attempt was mid-run, this is
    // where it actually reloads — the first safe point after every state
    // change (see checkForUpdate/applyUpdateIfSafe near the top of the file).
    if (updatePending) applyUpdateIfSafe();
    // Measurement version: Play, Ranks, Analytics and Settings.
    if (view === "leaderboard") renderLeaderboard();
    else if (view === "analytics") renderAnalytics();
    else if (view === "settings") renderSettings();
    else renderPlay();
  }

  // Old iOS Safari sizes `100vh` to the LARGEST possible viewport (as if the
  // address bar were hidden), not what's actually visible — so anything laid
  // out with `100vh` runs taller than the screen and its bottom gets clipped
  // with no way to scroll to it. `window.innerHeight` always reports the true
  // current height, so mirror it into a CSS var and use that instead (modern
  // browsers still prefer the `dvh` line that comes after it in the CSS).
  function setViewportHeightVar() {
    // innerHeight НЕ учитывает полоски, которые браузер накладывает поверх
    // страницы: «файл скачан», «приложение запущено» и подобные. Они не
    // меняют layout viewport и не вызывают resize — страница о них не знает
    // и продолжает считать, что экран прежней высоты. Нижнее меню при этом
    // оказывается ПОД полоской (Alex 2026-09-27: «перекрывает нижнее меню»).
    //
    // visualViewport знает про них: это та часть окна, которую человек
    // реально видит. Берём меньшее из двух.
    let h = window.innerHeight;
    const vv = window.visualViewport;
    if (vv && vv.scale <= 1.01) {
      const visible = Math.round(vv.height);
      // Отсекаем экранную клавиатуру. Она тоже урезает visualViewport, но
      // сильно — на треть экрана и больше, — и подстраивать под неё всю
      // раскладку не надо: поле ввода браузер сам подскроллит. Полоски
      // браузера забирают заметно меньше, поэтому порог в 25% их пропускает,
      // а клавиатуру нет. При зуме (scale > 1) не вмешиваемся вовсе.
      if (visible >= window.innerHeight * 0.75) h = Math.min(h, visible);
    }
    document.documentElement.style.setProperty("--vh100", h + "px");
  }
  setViewportHeightVar();
  window.addEventListener("resize", setViewportHeightVar);
  window.addEventListener("orientationchange", setViewportHeightVar);
  // Полоски браузера появляются и исчезают без window.resize — только
  // visualViewport сообщает об этом.
  if (window.visualViewport) {
    window.visualViewport.addEventListener("resize", setViewportHeightVar);
    window.visualViewport.addEventListener("scroll", setViewportHeightVar);
  }
  // The Fullscreen API transition is asynchronous — window.innerHeight at the
  // moment openGame() fires can still reflect the pre-transition size (chrome
  // still shown, or iOS mid-layout on a cold home-screen launch). If that
  // stale, too-small value gets locked into --vh100 and nothing re-measures
  // afterwards, .app-shell is sized too short and the bottom nav ends up
  // below the real visible area — reported as "some launches" because it
  // only bites browsers without `dvh` support, where this JS var is load-
  // bearing. Re-read once the transition actually completes.
  document.addEventListener("fullscreenchange", setViewportHeightVar);
  document.addEventListener("webkitfullscreenchange", setViewportHeightVar);

  // See OWN_SCRIPT_SRC comment at the top of the file. `updatePending` is set
  // once a check finds a newer build; the actual reload is deferred to
  // render() (see applyUpdateIfSafe below) so it can never yank the stopwatch
  // away mid-attempt — it only fires once the timer is back at "ready".
  let updatePending = false;

  function checkForUpdate() {
    if (!OWN_SCRIPT_SRC) return; // can't identify our own script — skip rather than risk a bad reload
    fetch("./play.html?_v=" + Date.now(), { cache: "no-store" })
      .then((res) => (res.ok ? res.text() : Promise.reject(new Error(String(res.status)))))
      .then((html) => {
        const m = html.match(/<script\s+src="([^"]+\.js)"/i);
        if (!m) return; // unexpected index.html shape — don't guess, just skip this cycle
        const freshSrc = new URL(m[1], location.href).href;
        if (freshSrc !== OWN_SCRIPT_SRC) {
          updatePending = true;
          applyUpdateIfSafe();
        }
      })
      .catch(() => { /* offline, or the fetch itself was blocked — try again next cycle, never reload on a failed check */ });
  }

  // Called both right when an update is first detected, and from render()
  // on every subsequent state change — reloads the moment it's safe (timer
  // not mid-attempt), even if that's much later than detection.
  function applyUpdateIfSafe() {
    if (updatePending && timer.state !== "running") location.reload();
  }

  // Checked on load (catches a tab that was already open when a new build
  // went out), on tab refocus (the common "left it open overnight" case),
  // and on a slow periodic timer as a fallback for a tab that's never
  // backgrounded. Never checks so often that it could matter for load.
  setTimeout(checkForUpdate, 4000);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") checkForUpdate();
  });
  setInterval(checkForUpdate, 10 * 60 * 1000);

  function bindHostPage() {
    const launcher = document.getElementById("gameLauncher");
    const overlay = document.getElementById("gameOverlay");
    if (!launcher || !overlay || overlay.dataset.bound === "true") return;
    overlay.dataset.bound = "true";
    // Swap the browser status-bar / theme colour to black while the app runs,
    // and restore the light site colour on close.
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    const siteThemeColor = themeMeta ? themeMeta.getAttribute("content") : "#f4f3ef";
    const setThemeColor = (c) => { if (themeMeta) themeMeta.setAttribute("content", c); };
    const enterFullscreen = () => {
      const el = overlay;
      const req = el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen;
      if (req) { try { req.call(el).catch(() => {}); } catch (e) {} }
    };
    const exitFullscreen = () => {
      if (document.fullscreenElement || document.webkitFullscreenElement) {
        const ex = document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen;
        if (ex) { try { ex.call(document).catch(() => {}); } catch (e) {} }
      }
    };
    const openGame = () => {
      unlockAudioPlayback(); // synchronous, inside the real tap — see comment above the function
      overlay.classList.add("is-open");
      overlay.setAttribute("aria-hidden", "false");
      document.documentElement.classList.add("game-open");
      document.body.classList.add("game-open");
      setViewportHeightVar(); // address-bar state can change right as we open
      // Re-read once this frame's layout has actually settled (double rAF —
      // a single rAF can still land before the browser chrome/PWA frame has
      // finished resolving, particularly on a cold home-screen launch). A
      // plain timer backs this up in case rAF is throttled/delayed (some
      // mobile chrome-collapse animations also just take longer than a
      // couple of frames), so the correction lands either way.
      requestAnimationFrame(() => requestAnimationFrame(setViewportHeightVar));
      setTimeout(setViewportHeightVar, 350);
      // Old iPhone Safari paints <video> in a native layer that ignores z-index and
      // punches through the fixed overlay ("jumps to the video"). Pause it — CSS
      // (body.game-open) also hides it — so the game shows on top. Android is fine.
      document.querySelectorAll(".page video").forEach((v) => { try { v.pause(); } catch (e) {} });
      setThemeColor("#000000");
      enterFullscreen();
      view = "play";
      render();
    };
    const closeGame = () => {
      stopMetronome();
      flushPlayTicker();
      exitFullscreen();
      setThemeColor(siteThemeColor);
      overlay.classList.remove("is-open");
      overlay.setAttribute("aria-hidden", "true");
      document.documentElement.classList.remove("game-open");
      document.body.classList.remove("game-open");
      if (timer.state === "running") {
        cancelAnimationFrame(timer.raf);
        timer.state = "ready";
        timer.elapsed = 0;
        timer.resultType = "none";
        render();
      }
    };
    launcher.addEventListener("click", openGame);
    // In-app close button removed site-wide (Alex, 2026-09-04) — closing now
    // only happens via Escape or a real fullscreen-exit gesture, both still
    // handled below.
    // Top-nav "Play" and the hero's "Play now →" both used to just scroll to
    // the #play section — now they launch the app immediately, same as
    // tapping the shortcut itself.
    document.querySelectorAll(".nav-play, .hero-cta").forEach((el) => {
      el.addEventListener("click", (event) => {
        event.preventDefault();
        openGame();
      });
    });
    // Home Screen shortcut → straight into the game, skipping the marketing
    // page. Without this, tapping the icon always opened the landing page
    // (hero video at the top) — the shortcut never actually launched anything.
    // Two independent signals, either is enough (manifest start_url carries
    // ?play=1, but standalone/fullscreen display-mode also catches icons that
    // were added before this fix, or any launcher that ignores the query).
    const launchedFromHomeScreen =
      /[?&]play=1(&|$)/.test(location.search) ||
      navigator.standalone === true ||
      (window.matchMedia && (
        window.matchMedia("(display-mode: standalone)").matches ||
        window.matchMedia("(display-mode: fullscreen)").matches
      ));
    if (launchedFromHomeScreen) openGame();
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && overlay.classList.contains("is-open")) closeGame();
    });
    // If the user leaves fullscreen via a browser gesture while playing, close the app too.
    const onFsChange = () => {
      const fs = document.fullscreenElement || document.webkitFullscreenElement;
      if (fs || !overlay.classList.contains("is-open")) return;
      if (suppressFullscreenClose) { suppressFullscreenClose = false; return; }
      closeGame();
    };
    document.addEventListener("fullscreenchange", onFsChange);
    document.addEventListener("webkitfullscreenchange", onFsChange);
  }

  function renderPlay() {
    startPlayTicker();
    if (displayGreeting && db.greeting) {
      renderGreeting();
      return;
    }
    if (failCard) {
      renderFailCard();
      return;
    }
    const quest = getQuest();
    db.settings.accuracy = quest.accuracy;
    // Main screen always shows TODAY (native v12.x — the period toggle moved to Analytics only).
    const MAIN_PERIOD = "TODAY";
    const metrics = mainMetrics(MAIN_PERIOD);
    // Fallback scale/geom values for when nothing is measurably scrolled into
    // view yet (fresh render) — refreshDynamicScale() takes over right after.
    mainScaleFallback = metrics.scaleMax;
    // The DOM about to be built below is drawn at the fallback scale — reset
    // the "currently applied" tracker to match so refreshDynamicScale() can
    // correctly detect whether the visible rows call for something different.
    mainDynamicScaleMax = metrics.scaleMax;
    mainGeomEarly = metrics.geomEarly;
    mainGeomAbs = metrics.geomAbs;
    mainGeomLate = metrics.geomLate;
    mainAccuracy = quest.accuracy;
    const todayScm = attemptsForPeriod("TODAY").reduce((sum, attempt) => sum + (attempt.scm || 0), 0);
    const sessionAttempts = currentSession() ? attemptsBySessionId(currentSession().id) : [];
    const face = faceStateFor(sessionAttempts);
    // Measurement: every hit in this measurement takes one target.
    const landed = sessionAttempts.filter(isQuestWinAttempt).length;
    const questLimitNow = currentWindowLimit();
    // Pips-row total (2026-09-04, native sync): windowLimit when there is one
    // (33/66/99), else however many attempts already exist this session --
    // matches native's `windowLimit ?: pipsColors.size` (QuestProgress.kt).
    const pipsTotal = questLimitNow != null ? questLimitNow : sessionAttempts.length;
    // No row cap — the whole day's sessions are shown and the log scrolls.
    const logRowsData = buildLogRows(MAIN_PERIOD);
    const logRows = logRowsData
      .map((r) => renderLogRow(r.type === "attempt" ? { ...r, scaleMaxMs: metrics.scaleMax } : r))
      .join("");
    // Date and session-number are two SEPARATE sticky elements, not one merged
    // line — that's what lets the browser's native sticky positioning slide
    // the session-row in and out on its own as you scroll, while the date
    // (a single instance, rendered once, outside the per-session loop) never
    // moves at all because nothing ever replaces it.
    const firstSessionRow = logRowsData.find((r) => r.type === "session");
    const logDate = firstSessionRow ? firstSessionRow.date : "";
    // maxCount floored at 5 (native) so a lone attempt doesn't slam full height.
    const maxBucket = Math.max(5, ...metrics.buckets.map((bucket) => bucket.count));
    const barPx = HIST_BAR_PX;
    const axisTicks = [-metrics.scaleMax, -metrics.scaleMax / 2, 0, metrics.scaleMax / 2, metrics.scaleMax]
      .map((value) => `<span>${Math.abs(value) < 0.0001 ? "0" : `${value > 0 ? "+" : ""}${formatAxis(value, quest.accuracy)}`}</span>`)
      .join("");
    const HIST_H = 54; // bar/gridline area (px) inside the 58px histogram
    const histogram = metrics.buckets.map((bucket) => {
      // Green ONLY for the exact-3.000 centre bucket (native); else red/blue by side.
      const cls = bucket.centerMs === 0 ? "win" : bucket.centerMs < 0 ? "early" : "late";
      const drop = bucket.count && justLandedMs != null && bucket.centerMs === justLandedMs ? " drop-in" : "";
      const h = bucket.count ? Math.max(2, bucket.count / maxBucket * HIST_H) : 0;
      const x = metrics.scaleMax > 0
        ? ((bucket.centerMs / metrics.scaleMax + 1) / 2) * 100
        : 50;
      return `<span class="bar ${cls}${drop}" data-center-ms="${bucket.centerMs}" title="${bucket.centerMs} ms: ${bucket.count}" style="height:${h}px;left:calc(${x}% - ${barPx / 2}px);width:${barPx}px"></span>`;
    }).join("");
    const histAxis = histYAxis(maxBucket, HIST_H);
    const geomTriangles = geomTriangleLayer(metrics);
    const timeClass = timer.resultType === "early" ? "is-early" : timer.resultType === "late" ? "is-late" : timer.resultType === "win" ? "is-win" : "";
    const timerMode = timer.state === "ready" ? "neutral" : timer.resultType === "early" ? "early" : timer.resultType === "late" ? "late" : timer.resultType === "win" ? "win" : "neutral";
    const statusText = timer.state === "running" ? "STOP" : timer.state === "ready" ? "RUN" : (awaitingHurray ? "HOORAY!" : "RESET");
    const toast = toastMessage(timer);
    app.innerHTML = `
      <section class="screen android-screen">
        <div class="quest-strip measure-strip">
          <div class="quest-panel">
            <div class="quest-level-line">Try to hit as many targets as you can</div>
            ${renderPips(pipsTotal, sessionAttempts)}
            <div class="quest-center">
              <div class="quest-targets-full">${renderTargets(fitTargetsCount(), landed, false)}</div>
            </div>
          </div>
        </div>
        <div class="timer-zone" data-help="stopwatch">
          <button id="runButton" class="timer-card timer-${timerMode} ${timer.state === "running" ? "running" : ""} ${timer.state === "running" && timer.elapsed >= MIN_ATTEMPT ? "stop-active" : ""}">
            ${face.visible ? `<span class="face-badge face-${face.kind} ${face.animClass}"><i></i><b></b><em></em></span>` : ""}
            ${db.settings.progressBarOn !== false ? `<span class="progress-bar"><span class="pb-track">${progressTicks()}<span id="pbWinZone" class="pb-winzone" style="left:${winZoneFrac().left * 100}%;width:${winZoneFrac().width * 100}%"></span><span id="pbFill" class="pb-fill ${timeClass}" style="width:${progressFrac(timer.elapsed || 0) * 100}%"></span><span class="pb-center" style="left:${progressCenterFrac() * 100}%"></span><span id="pbBeak" class="pb-beak ${timeClass}" style="left:${progressFrac(timer.elapsed || 0) * 100}%"></span></span><span class="pb-num pb-num-l" id="pbNumL">${formatTime(progressWindow().lo, quest.accuracy)}</span><span class="pb-num pb-num-c" id="pbNumC" style="left:${progressCenterFrac() * 100}%">${formatTime(AIM_TIME, quest.accuracy)}</span><span class="pb-num pb-num-r" id="pbNumR">${formatTime(progressWindow().hi, quest.accuracy)}</span></span>` : ""}
            <span id="timeDisplay" class="time-display ${timeClass}">${timer.state === "running" ? (db.settings.numericTime === false ? "•••" : formatTime(timer.elapsed, quest.accuracy)) : formatTime(timer.elapsed || 0, quest.accuracy)}</span>
            <span class="timer-action">${actionIcon(timer)} ${statusText}</span>
          </button>
          ${toast ? `<div class="cheer-toast ${toast.kind}">${toast.text}</div>` : ""}
        </div>
        <div class="diagram-stack">
        <div class="android-log-wrap" data-help="log">
          <div class="android-log-scroll">
            ${logRows ? `<div class="log-date">${escapeHtml(logDate)}</div>` : ""}
            <div class="attempt-log">${logRows || `<div class="android-empty">No data for this period</div>`}</div>
          </div>
          ${logRows ? `<div class="log-scrollbar"><div class="log-scrollbar-track"></div><div class="log-scrollbar-thumb"></div></div>` : ""}
        </div>
        ${metrics.attempts.length ? `<div class="android-summary" data-help="distribution">
          <div class="tries-box">
            <div class="tb-count"><span>Total tries</span><strong>${metrics.attempts.length}</strong></div>
            <div class="tb-distrib">&nbsp;</div>
          </div>
          <div class="summary-chart">
            <div class="histogram" data-bar-px="${barPx}">${histAxis}${histogram}</div>
            <div class="axis-labels">${axisTicks}</div>
            <div class="geom-band" data-help="geom">${geomTriangles}</div>
          </div>
          <div class="geom-strip" data-help="geom">
            <span>Geom. Mean</span>
            <span><b class="early">${formatMs(-metrics.geomEarly, true)}</b></span>
            <span><b>${formatMs(metrics.geomAbs, true)}</b></span>
            <span><b class="late">${formatMs(metrics.geomLate, true)}</b></span>
          </div>
        </div>` : ""}
        ${metrics.attempts.length ? geomGuides(metrics) : ""}
        </div>
        ${nav()}
        ${measurementDone ? measurementDoneOverlay() : ""}
      </section>
    `;
    bindPlay();
    justLandedMs = null; // one-shot: the grow animation is now baked into the DOM
    if (face.animClass && db.face) {
      db.face.animationConsumed = true;
      saveDb();
    }
  }

  function buttonText() {
    if (timer.state === "running") return "STOP";
    if (timer.state === "result") return timer.pendingMessage ? "OK" : "RESET";
    return "RUN";
  }

  function buttonResultClass() {
    if (timer.resultType === "early") return "result-early";
    if (timer.resultType === "late") return "result-late";
    if (timer.resultType === "win") return "result-win";
    return "";
  }

  function faceStateFor(sessionAttempts) {
    if (!sessionAttempts.length) return { visible: false, kind: "nice", animClass: "" };
    let kind = "nice";
    if (sessionAttempts.length > 1) {
      const last = sessionAttempts[sessionAttempts.length - 1];
      const prev = sessionAttempts[sessionAttempts.length - 2];
      kind = Math.abs(displayErrorSeconds(last.endTime - last.startTime, last.accuracy)) <=
        Math.abs(displayErrorSeconds(prev.endTime - prev.startTime, prev.accuracy))
        ? "nice"
        : "sad";
    }
    const latestAttempt = sessionAttempts[sessionAttempts.length - 1];
    const stored = db.face && db.face.sessionId === sessionAttempts[0].sessionId ? db.face : null;
    const canAnimate = stored && stored.attemptId === latestAttempt.id && !stored.animationConsumed;
    const animClass = canAnimate && stored.changed
      ? `face-slide-${kind}`
      : canAnimate && stored.repeat
        ? `face-repeat-${kind}`
        : "";
    return { visible: true, kind, animClass };
  }

  function windowCaption(sessionAttempts) {
    const limit = getEffectiveN();
    const wins = sessionAttempts.filter(isQuestWinAttempt).length;
    if (limit > 0) return `${sessionAttempts.length}/${limit} attempts used · ${wins} wins in this session`;
    return `${sessionAttempts.length}/${SESSION_AUTO_CLOSE_ATTEMPTS} session attempts · ${wins} wins`;
  }

  function renderQuestIcons(total, landed) {
    return Array.from({ length: total }, (_, idx) => `
      <span class="target-icon ${idx < landed ? "hit" : ""}">
        <i></i><b></b>${idx < landed ? "<em>✓</em>" : ""}
      </span>
    `).join("");
  }

  function renderFence() {
    return Array.from({ length: 8 }, (_, idx) => `<span style="--i:${idx}"></span>`).join("");
  }

  // ── Quest archer (ported from native QuestProgress.kt) ──────────────────────
  // Bullseye targets: gap for IN_N/ONE_ONLY, 25% overlap for ROW. Hit targets
  // turn into the spinning .pot-logo brand mark (2026-08-23, Alex: "мишени
  // по мере выбивания должны превращаться в анимированные логотипы") — same
  // component already used on the About screen, reused as-is here (no new
  // CSS/animation needed, just a smaller sized+clipped instance). The old
  // green-wash + check badge on hit targets is removed to match native's
  // same-day change ("зелёные чеки в кружочке надо убрать, всё равно
  // полностью они не помещаются") — the logo itself already reads as
  // "taken". Unhit tiles are painted first (background), hit tiles second
  // (foreground) so a hit tile's logo isn't clipped by an overlapping
  // unhit neighbour on ROW quests — same ordering/reasoning as native's
  // TargetsRow (two passes over the same index range, not two loops that
  // could be merged, precisely so DOM order controls the stack).
  // Measurement version: the targets row spans the whole strip, as many
  // targets as fit; a hit target turns into the spinning logo (ring,
  // triangle and letter t, as in the game). Hits beyond the row's
  // length keep every target turned.
  const TARGET_D = 30, TARGET_GAP = 4;
  function fitTargetsCount() {
    const strip = app.clientWidth || window.innerWidth || 360;
    const usable = Math.min(strip, 572) - 40; // .quest-strip has 20px side padding
    return Math.max(1, Math.floor((usable + TARGET_GAP) / (TARGET_D + TARGET_GAP)));
  }

  function renderTargets(count, landed, isRow) {
    const D = TARGET_D, r = D / 2, gap = isRow ? -7 : TARGET_GAP;
    const step = D + gap;
    const w = D * count + gap * (count - 1);
    const bullseye = () => {
      const circle = (frac, fill, extra = "") =>
        `<circle cx="${r}" cy="${r}" r="${(r * frac).toFixed(2)}" fill="${fill}" ${extra}/>`;
      return `<svg class="target-svg" width="${D}" height="${D}" viewBox="0 0 ${D} ${D}">` +
        circle(1, "#FFF6E6") +
        circle(1, "none", 'stroke="#2A1E00" stroke-width="1" vector-effect="non-scaling-stroke"') +
        circle(15 / 19, "#2D2D2D") + circle(11 / 19, "#378ADD") +
        circle(7 / 19, "#D14848") + circle(2.5 / 19, "#FFD500") +
        `</svg>`;
    };
    const triangle = () =>
      `<div class="pot-logo">` +
      `<img class="pot-layer pot-ring" src="${SALUTE_LOGO_RING_SRC}" alt="">` +
      `<img class="pot-layer pot-triangle" src="${SALUTE_LOGO_TRIANGLE_SRC}" alt="">` +
      `<img class="pot-layer pot-letter" src="${SALUTE_LOGO_LETTER_SRC}" alt="">` +
      `</div>`;
    let unhitHtml = "", hitHtml = "";
    for (let i = 0; i < count; i += 1) {
      const left = i * step;
      if (i < landed) hitHtml += `<div class="target-tile target-hit" style="left:${left}px">${triangle()}</div>`;
      else unhitHtml += `<div class="target-tile" style="left:${left}px">${bullseye()}</div>`;
    }
    return `<div class="quest-targets-row" style="width:${w}px;height:${D}px">${unhitHtml}${hitHtml}</div>`;
  }

  // Hand-drawn checkmark (ported from QuestProgress.kt's HandDrawnCheck,
  // Alex 2026-08-14): shown to the right of the quest-conditions text only
  // while awaitingHurray is true — i.e. between winning and tapping through
  // GreetingView, while the top of the screen still shows the just-completed
  // quest (see commitQuestAdvance). Re-created fresh every render() the same
  // way native remounts the composable on false→true, so the stroke-draw CSS
  // animation below restarts every time it appears.
  function renderQuestCheck() {
    return `<svg class="quest-check" width="18" height="18" viewBox="0 0 26 26" aria-hidden="true">` +
      `<path d="M4.16 13.52 L10.92 20.28 L22.88 5.72" pathLength="1"/>` +
      `</svg>`;
  }

  // ── Quest level/quest-number line (ported from QuestProgress.kt, Alex
  // 2026-09-01/03): "Level X · Quest X.Y[.Z]" — the third (substep) segment
  // appears ONLY while a subquest indulgence is live, omitted at baseline.
  function questLevelLine(quest) {
    const step = subquestStep();
    return `Level ${quest.levelId} · Quest ${quest.levelId}.${quest.taskId}` + (step > 0 ? `.${step}` : "");
  }

  // ── Attempt "pips" row (ported from QuestProgress.kt's AttemptPipsRow,
  // Alex 2026-08-25/09-01 native, ported to web 2026-09-04) — replaces the
  // old arrow-bundle visualization entirely. One dot per attempt-slot in the
  // current window: played -> filled circle colored by result (oldest on the
  // left), unplayed -> small hollow ring. Windows above 33 (indulged, 66/99)
  // wrap into additional 33-wide rows stacked downward; every odd row (2nd,
  // 4th, ...) is shifted right by half a cell so its dots sit over the GAPS
  // of the row above/below (masonry stagger, not a plain grid) -- Alex's own
  // phrasing: "точка над пространством между точками".
  const PIPS_COLS = 33;
  function pipResultClass(attempt) {
    if (isQuestWinAttempt(attempt)) return "win";
    return signedErrorMs(attempt) < 0 ? "early" : "late";
  }
  function renderPips(total, sessionAttempts) {
    if (total <= 0) return "";
    const rows = Math.ceil(total / PIPS_COLS);
    let rowsHtml = "";
    for (let row = 0; row < rows; row += 1) {
      const rowStart = row * PIPS_COLS;
      const rowCount = Math.min(PIPS_COLS, total - rowStart);
      let cellsHtml = "";
      for (let i = 0; i < PIPS_COLS; i += 1) {
        if (i >= rowCount) { cellsHtml += `<span class="pip-cell"></span>`; continue; }
        const attempt = sessionAttempts[rowStart + i];
        cellsHtml += attempt
          ? `<span class="pip-cell"><span class="pip pip-${pipResultClass(attempt)}"></span></span>`
          : `<span class="pip-cell"><span class="pip pip-empty"></span></span>`;
      }
      rowsHtml += `<div class="pips-row${row % 2 === 1 ? " pips-row-odd" : ""}">${cellsHtml}</div>`;
    }
    return `<div class="quest-pips">${rowsHtml}</div>`;
  }

  // ── Aim time (green, bold) + dimmer tolerance suffix ──────────────────
  function renderQuestAim(quest) {
    const acc = quest.accuracy;
    const tol = getEffectiveToleranceMs();
    const aimStr = formatTime(AIM_TIME, acc);
    const tolStr = tol > 0 ? `± ${formatTime(tol, acc)}` : "";
    return `<span class="quest-aim"><b class="quest-aim-main">${aimStr}</b>` +
      (tolStr ? `<b class="quest-aim-tol"> ${tolStr}</b>` : "") + `</span>`;
  }

  // ── Mechanic-count label ("2× in a row" / "2× in total") ──────────────
  function renderMechanicLabel(quest) {
    const isRow = quest.victory.condition === "ROW";
    return `<span class="quest-mech"><b class="quest-mech-count">${quest.victory.count}×</b>` +
      `<span class="quest-mech-suffix">${isRow ? " in a row" : " in total"}</span></span>`;
  }

  function victoryShortLabel(quest) {
    if (quest.victory.condition === "ROW") return "in row ×";
    if (quest.victory.condition === "IN_N") return "·";
    return "·";
  }

  function limitLabel(sessionAttempts) {
    const limit = getEffectiveN();
    if (limit > 0) return ` · left ${Math.max(0, limit - sessionAttempts.length)}/${limit}`;
    return " · no limit";
  }

  function actionIcon() {
    if (timer.state === "running") return "■";
    if (timer.state === "ready") return "▶";
    return "↺";
  }

  function toastMessage(currentTimer) {
    return null; // measurement version: no cheer-ups, twins or neighbors
    if (!db.settings.cheerUpsEnabled) return null;
    if (db.lastMessage && currentTimer.state !== "running") {
      return { kind: "gold", text: "✦ " + escapeHtml(db.lastMessage) };
    }
    if (db.twinShotVisible) {
      return { kind: "green", text: "✦ wow, twins!" };
    }
    if (db.neighborShotVisible) {
      return { kind: "gold", text: "✦ wow, neighbors!" };
    }
    if (db.itWasCloseVisible) {
      return { kind: "gold", text: "✦ it was close!" };
    }
    const patternToast = errorPatternToast(db.errorPattern || "NONE");
    if (patternToast) return patternToast;
    return null;
  }

  function formatComma(value, digits = 2) {
    if (!Number.isFinite(value)) return "0,00";
    return value.toFixed(digits).replace(".", ",");
  }

  // Native formatCoinsTotal: tiered precision + thin-space thousands separator.
  // Stack-of-coins pictogram — replaces the "ΣSC" text label next to the coin total.
  function coinIcon() {
    return `<svg class="coin-ico" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
      <ellipse cx="12" cy="16" rx="8" ry="3.4"/>
      <ellipse cx="12" cy="12" rx="8" ry="3.4"/>
      <ellipse cx="12" cy="8" rx="8" ry="3.4"/>
    </svg>`;
  }

  function formatCoins(value) {
    if (!Number.isFinite(value)) return "0";
    if (value < 10) return value.toFixed(2).replace(".", ",");
    if (value < 100) return value.toFixed(1).replace(".", ",");
    if (value < 1000) return String(Math.round(value));
    return String(Math.round(value)).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  }

  // Dynamic Y scale: nice-step ticks on the RIGHT + faint data-aligned gridlines.
  function histYAxis(maxCount, areaPx) {
    const target = maxCount / 4;
    const mag = Math.pow(10, Math.floor(Math.log10(Math.max(1, target))));
    const norm = target / mag;
    const s = norm <= 1.5 ? 1 : norm <= 3.5 ? 2 : norm <= 7.5 ? 5 : 10;
    const step = Math.max(1, Math.round(s * mag));
    let html = "";
    for (let v = step; v <= maxCount; v += step) {
      const bottom = (v / maxCount) * areaPx;
      html += `<span class="hist-grid" style="bottom:${bottom}px"></span>` +
        `<span class="hist-tick" style="bottom:${bottom - 4}px">${v}</span>`;
    }
    return html;
  }

  function formatAxis(value, accuracy) {
    const seconds = value / 1000;
    const digits = accuracy === ACCURACY.MILLISECONDS ? 3 : accuracy === ACCURACY.CENTISECONDS ? 2 : 1;
    return seconds.toFixed(digits).replace(".", ",");
  }

  // Shared vertical geom-mean guides. One overlay spanning BOTH the error diagram
  // (log rows) and the histogram — they use the same dynamic scale + plot band, so
  // a single dashed line per mean lines up with the error bars above and the
  // distribution bars below. Red = early, white = all, blue = late.
  function geomGuides(metrics) {
    const scale = Math.max(1, metrics.scaleMax || 1);
    const line = (name, valueMs) => {
      const clamped = Math.max(-scale, Math.min(scale, valueMs || 0));
      const x = ((clamped / scale + 1) / 2) * 100;
      return `<span class="geom-guide ${name}" data-value-ms="${valueMs || 0}" style="left:${x}%"></span>`;
    };
    return `<div class="geom-guides">
      <span class="geom-zero"></span>
      ${line("early", -Math.abs(metrics.geomEarly || 0))}
      ${line("absolute", metrics.geomAbs || 0)}
      ${line("late", Math.abs(metrics.geomLate || 0))}
    </div>`;
  }

  function geomMarkerLayer(metrics) {
    const scale = Math.max(1, metrics.scaleMax || 1);
    const marker = (name, valueMs) => {
      const clamped = Math.max(-scale, Math.min(scale, valueMs || 0));
      const x = ((clamped / scale + 1) / 2) * 100;
      return `<span class="geom-marker ${name}" style="left:${x}%"></span>`;
    };
    return `<span class="geom-markers">
      ${marker("early", -Math.abs(metrics.geomEarly || 0))}
      ${marker("absolute", metrics.geomAbs || 0)}
      ${marker("late", Math.abs(metrics.geomLate || 0))}
    </span>`;
  }

  function geomTriangleLayer(metrics) {
    const scale = Math.max(1, metrics.scaleMax || 1);
    const marker = (name, valueMs) => {
      const clamped = Math.max(-scale, Math.min(scale, valueMs || 0));
      const x = ((clamped / scale + 1) / 2) * 100;
      return `<span class="geom-triangle ${name}" data-value-ms="${valueMs || 0}" style="left:${x}%"></span>`;
    };
    return `
      ${marker("early", -Math.abs(metrics.geomEarly || 0))}
      ${marker("absolute", metrics.geomAbs || 0)}
      ${marker("late", Math.abs(metrics.geomLate || 0))}
    `;
  }

  function formatGeom(value, accuracy, signed = false) {
    if (!Number.isFinite(value) || Math.abs(value) < 0.0001) {
      return signed ? "−0,0" : "—";
    }
    const seconds = value / 1000;
    const digits = accuracy === ACCURACY.MILLISECONDS ? 3 : 1;
    const sign = signed && seconds > 0 ? "+" : "";
    return sign + seconds.toFixed(digits).replace("-", "−").replace(".", ",");
  }

  function trailingWins(attempts) {
    const reversed = [...attempts].reverse();
    const result = [];
    for (const attempt of reversed) {
      if (!isQuestWinAttempt(attempt)) break;
      result.push(attempt);
    }
    return result;
  }

  function instantAttemptFlags(attempts) {
    if (!attempts.length) return { twinShot: false, neighborShot: false, itWasClose: false };
    const last = attempts[attempts.length - 1];
    const lastError = displayErrorSeconds(last.endTime - last.startTime, last.accuracy);
    const stepSec = last.accuracy === ACCURACY.SECONDS
      ? Number.POSITIVE_INFINITY
      : last.accuracy === ACCURACY.DECISECONDS
        ? 0.1
        : last.accuracy === ACCURACY.CENTISECONDS
          ? 0.01
          : 0.001;
    let twinShot = false;
    let neighborShot = false;
    if (attempts.length >= 2) {
      const prev = attempts[attempts.length - 2];
      const prevError = displayErrorSeconds(prev.endTime - prev.startTime, prev.accuracy);
      const bothMiss = !isQuestWinAttempt(last) && !isQuestWinAttempt(prev);
      // Twins: two consecutive misses with the same |error|.
      twinShot = bothMiss && Math.abs(lastError - prevError) < 0.0005;
      // Neighbors: two consecutive misses one accuracy cell apart.
      neighborShot = bothMiss && !twinShot &&
        Math.abs(Math.abs(Math.abs(lastError) - Math.abs(prevError)) - stepSec) < 0.0005;
    }
    const toleranceSec = (last.toleranceMs || 0) / 1000;
    const itWasClose = !twinShot && !neighborShot && !isQuestWinAttempt(last) &&
      Math.abs(Math.abs(lastError) - (toleranceSec + stepSec)) < 0.0005;
    return { twinShot, neighborShot, itWasClose };
  }

  function detectErrorPattern(errors, pausesMs) {
    if (!errors.length) return "NONE";
    if (errors.length >= 9) {
      const last9 = errors.slice(-9);
      if (last9.every((value) => value < 0)) return "SYSTEMATIC_EARLY";
      if (last9.every((value) => value > 0)) return "SYSTEMATIC_LATE";
    }
    if (errors.length >= 6) {
      const last6 = errors.slice(-6).map(Math.abs);
      if (last6.slice(1).every((value, idx) => value > last6[idx])) return "INCREASING_DRIFT";
    }
    if (errors.length >= 3) {
      const last3 = errors.slice(-3);
      const growing = last3.slice(1).every((value, idx) => Math.abs(value) > Math.abs(last3[idx]));
      if (growing && last3.every((value) => value < 0)) return "RED_GROWING";
      if (growing && last3.every((value) => value > 0)) return "BLUE_GROWING";
    }
    if (errors.length >= 4) {
      const [a, b, c, d] = errors.slice(-4);
      if (Math.abs(a - b) < 0.0005 && Math.abs(b - c) < 0.0005 && Math.abs(c - d) >= 0.0005) {
        return "TRIPLE_IDENTICAL";
      }
    }
    if (errors.length >= 3 && pausesMs.length >= 2) {
      const lastErrors = errors.slice(-3);
      const lastPauses = pausesMs.slice(-2);
      const allShort = lastPauses.every((value) => value > 0 && value < 300);
      const allLong = lastPauses.every((value) => value > 300);
      if (allShort && lastErrors.every((value) => value < 0)) return "PAUSE_SHORT";
      if (allLong && lastErrors.every((value) => value > 0)) return "PAUSE_LONG";
    }
    if (errors.length >= 3) {
      const last3Abs = errors.slice(-3).map(Math.abs);
      if (last3Abs.slice(1).every((value, idx) => value < last3Abs[idx])) return "JAGGED_RHYTHM";
    }
    return "NONE";
  }

  function errorPatternToast(pattern) {
    switch (pattern) {
      case "SYSTEMATIC_EARLY": return { kind: "red", text: "← too tense — try to relax" };
      case "SYSTEMATIC_LATE": return { kind: "blue", text: "→ too relaxed — try to focus" };
      case "INCREASING_DRIFT": return { kind: "gold", text: "↗ errors growing — breathe and refocus" };
      case "TRIPLE_IDENTICAL": return { kind: "green", text: "✦ three identical — now improve accuracy" };
      case "PAUSE_SHORT": return { kind: "red", text: "← too tense — lengthen the pause" };
      case "PAUSE_LONG": return { kind: "blue", text: "→ too relaxed — shorten the pause" };
      case "JAGGED_RHYTHM": return { kind: "gold", text: "↯ approaching to the balance" };
      case "RED_GROWING": return { kind: "red", text: "← easy, easy…" };
      case "BLUE_GROWING": return { kind: "blue", text: "→ wake up!!!" };
      default: return null;
    }
  }

  function buildLogRows(period) {
    const attempts = attemptsForPeriod(period);
    const sessions = [...db.sessions].sort((a, b) => a.startTime - b.startTime);
    // Sessions are numbered 1..N WITHIN their calendar day (resets each day).
    const perDay = {};
    const numberMap = new Map();
    sessions.forEach((session) => {
      const dayKey = new Date(session.startTime).toDateString();
      perDay[dayKey] = (perDay[dayKey] || 0) + 1;
      numberMap.set(session.id, perDay[dayKey]);
    });
    const newest = [...attempts].sort((a, b) => b.startTime - a.startTime);
    const order = [...new Set(newest.map((attempt) => attempt.sessionId))];
    const rows = [];
    order.forEach((sid) => {
      const session = db.sessions.find((item) => item.id === sid);
      const group = newest.filter((attempt) => attempt.sessionId === sid);
      // Deepest indulgence step in the session, by max toleranceMs (ported from
      // native GetLogAttemptsUseCase.kt, 2026-07-30 — SubQuest N.k numbering).
      // resolutionFloorMs() is always 0 today, so the native filter reduces to
      // "toleranceMs > 0" here.
      const maxTolAttempt = group
        .filter((attempt) => attempt.toleranceMs > 0)
        .reduce((max, attempt) => (!max || attempt.toleranceMs > max.toleranceMs ? attempt : max), null);
      const sessionIsSubquest = maxTolAttempt !== null;
      const subStep = maxTolAttempt
        ? Math.max(1, Math.trunc(maxTolAttempt.toleranceMs /
            (maxTolAttempt.accuracy === 0 ? 1000 : maxTolAttempt.accuracy === 1 ? 100 : maxTolAttempt.accuracy === 2 ? 10 : 1)))
        : 0;
      const sQuestLevelId = (session && session.questLevelId) || 0;
      const sQuestTaskId = (session && session.questTaskId) || 0;
      // Native parity fix (Alex 2026-09-25, "сделай как в нативе"): native's
      // GetLogAttemptsUseCase.kt formats this exact sticky log-date header
      // with SimpleDateFormat("dd/MM/yyyy") — a fixed numeric literal pattern,
      // not locale-dependent (Locale only affects things like month names,
      // and this pattern has none). The previous "15 Aug '26" style below was
      // NOT native parity, just a pre-existing web-only choice — confirmed via
      // direct source read, not assumed; it predates today and was never
      // touched in the 2026-09-25 FailCard/SessionSummary/Analytics deploy.
      // Kept manual (no toLocaleDateString) so day/month/year ORDER and the
      // 4-digit year can never vary by the viewer's locale, matching native
      // exactly. Still comfortably under .log-date's 78px max-width (see
      // styles.css) — "25/09/2026" measures narrower than the old
      // "Aug 15, 26" it replaces.
      function nativeLogDateFmt(ts) {
        const d = new Date(ts);
        const dd = String(d.getDate()).padStart(2, "0");
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        return `${dd}/${mm}/${d.getFullYear()}`;
      }
      const sDate = session ? nativeLogDateFmt(session.startTime) : "";
      const questLabel = sessionIsSubquest
        ? `SubQuest ${sQuestTaskId}.${subStep}`
        : `Quest ${sQuestTaskId} of ${getTasksCountForLevel(sQuestLevelId || 1)}`;
      // Measurement version: a session is a measurement; an unfinished one
      // (fewer than 33 attempts, e.g. level switched midway) says so.
      const unfinished = Boolean(session && session.closed && !session.isQuestComplete);
      rows.push({ type: "session", date: sDate, label: `Measurement #${numberMap.get(sid) || sid} · ${levelPrecisionName(sQuestLevelId)}${unfinished ? " · unfinished" : ""}` });
      group.forEach((attempt) => {
        const oldest = attemptsBySessionId(sid);
        const number = oldest.findIndex((item) => item.id === attempt.id) + 1;
        rows.push({ type: "attempt", attempt, number, twin: false });
      });
    });
    // Group RUNS of any length (not just pairs): consecutive non-win attempts in
    // the same session that are all identical |error| → twin run; or where each
    // adjacent pair differs by exactly one level cell → neighbor run.
    //
    // Twins and neighbors are found in two INDEPENDENT passes, not one sweep
    // that consumes rows into whichever kind it meets first — a shared row
    // sitting between (say) a twin pair and a neighbor pair belongs to both,
    // and both hand-drawn outlines get drawn even where they overlap. A row
    // can never anchor more than one oval itself: whether a run starts at a
    // given row depends only on its fixed relationship to the NEXT row (equal
    // → twin, exactly one cell apart → neighbor, never both at once), so the
    // two passes can only ever pick different start rows, not the same one.
    const absErr = (r) => Math.abs(displayErrorSeconds(r.attempt.endTime - r.attempt.startTime, r.attempt.accuracy));
    const eligible = (r, sid) => r && r.type === "attempt" && !isQuestWinAttempt(r.attempt) && r.attempt.sessionId === sid;
    // The freehand oval is drawn once, on the TOP row of the run, spanning groupHeight rows.
    const mark = (from, to, kind) => {
      rows[from].groupKind = kind;
      rows[from].groupHeight = to - from + 1;
      rows[from].groupSeed = rows[from].attempt.startTime;
    };
    // Twin run: every member equals the first member's |error|.
    (function twinPass() {
      let i = 0;
      while (i < rows.length) {
        const a = rows[i];
        if (!eligible(a, a && a.attempt && a.attempt.sessionId)) { i += 1; continue; }
        const sid = a.attempt.sessionId;
        let j = i;
        while (eligible(rows[j + 1], sid) && Math.abs(absErr(rows[j + 1]) - absErr(a)) < 0.0005) j += 1;
        if (j > i) { mark(i, j, "twin"); i = j + 1; } else { i += 1; }
      }
    })();
    // Neighbor run: each adjacent pair differs by exactly one cell. Independent
    // of the twin pass above — rows already covered by a twin oval are still
    // eligible to start or extend a neighbor run.
    (function neighborPass() {
      let i = 0;
      while (i < rows.length) {
        const a = rows[i];
        if (!eligible(a, a && a.attempt && a.attempt.sessionId)) { i += 1; continue; }
        const sid = a.attempt.sessionId;
        const stepSec = a.attempt.accuracy === ACCURACY.MILLISECONDS ? 0.001
          : a.attempt.accuracy === ACCURACY.CENTISECONDS ? 0.01
            : a.attempt.accuracy === ACCURACY.DECISECONDS ? 0.1 : 1;
        let j = i;
        while (eligible(rows[j + 1], sid) && Math.abs(Math.abs(absErr(rows[j + 1]) - absErr(rows[j])) - stepSec) < 0.0005) j += 1;
        if (j > i) {
          // This row already anchors a twin oval (from the pass above) — can't
          // happen per the comment above (a row's relationship to the next row
          // is single-valued), but if it ever did, don't clobber that mark.
          if (!rows[i].groupKind) mark(i, j, "neighbor");
          i = j + 1;
        } else {
          i += 1;
        }
      }
    })();
    // Newest attempt row → hide its SC (not final until the next attempt arrives).
    const firstAttempt = rows.find((r) => r.type === "attempt");
    if (firstAttempt) firstAttempt.isNewest = true;
    return rows;
  }

  // Deterministic seeded RNG for the freehand oval jitter (mulberry32).
  function mulberry32(seed) {
    let a = seed | 0;
    return () => {
      a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  // Port of native drawFreehandOval — a hand-drawn "marker oval" around the # and
  // time columns, spanning `groupHeight` rows. 12 seeded forms + jittered beziers.
  const OVAL_ROW_H = 22; // log row height (px)
  // 2026-09-10 native parity (item 1, round 2 in native_changes_2026-09-09_to_10.md):
  // Alex's follow-up on the twin/neighbor outline — keep it at normal
  // brightness everywhere, but dim it specifically where the stroke crosses
  // the "#"/time text (so the outline doesn't visually fight the digits).
  // Native clips the same stroke path twice (outside the text rect at full
  // alpha, inside it at 30%). Ported the same way here: the text rect is the
  // oval's own [rectLeft..rectRight] band (already exactly "the # and time
  // columns" per the comment above) at full canvas height, split via two
  // <clipPath>s and rendered as two overlaid <path>s with the same "d".
  let ovalClipCounter = 0;
  function freehandOvalSvg(seed, groupHeight, kind) {
    const rectLeft = 10, rectRight = 78, rectTop = 2;
    const rectBottom = groupHeight * OVAL_ROW_H - 2;
    const W = 92, H = groupHeight * OVAL_ROW_H + 6;
    const gap = 5;
    const cx = (rectLeft + rectRight) / 2, cy = (rectTop + rectBottom) / 2;
    const rxBase = (rectRight - rectLeft) / 2, ryBase = (rectBottom - rectTop) / 2;
    const rng = mulberry32(seed);
    // tilt · rxFactor · ryFactor · startAtBottom · doubleLoop  (native's 12 configs)
    const configs = [
      [-12, 1.00, 1.00, false, false], [0, 1.00, 1.00, false, true], [0, 1.00, 1.00, true, false],
      [0, 1.00, 0.92, false, false], [-10, 1.00, 0.92, false, false], [9, 1.00, 0.92, false, false],
      [-10, 1.00, 1.00, true, false], [0, 1.00, 1.00, true, true], [-13, 1.00, 1.00, false, true],
      [0, 1.00, 0.88, false, false], [0, 0.92, 1.00, false, false], [10, 1.00, 1.00, true, false]
    ];
    const [tiltDeg, rxF, ryF, startAtBottom, doubleLoop] = configs[((seed % 12) + 12) % 12];
    const rx = rxBase * rxF, ry = ryBase * ryF;
    const tiltRad = tiltDeg * Math.PI / 180;
    const nPoints = doubleLoop ? 22 : 14;
    const totalArc = doubleLoop ? Math.PI * 2 * 1.4 : Math.PI * 2;
    const baseStart = startAtBottom ? Math.PI / 2 + 0.3 : -Math.PI / 2 + 0.3;
    const cosT = Math.cos(tiltRad), sinT = Math.sin(tiltRad);
    const pts = [];
    for (let i = 0; i <= nPoints; i += 1) {
      const baseAngle = baseStart + (i / nPoints) * totalArc;
      const jitterAngle = baseAngle + (rng() - 0.5) * 0.10;
      const rf = doubleLoop ? 1 - (i / nPoints) * 0.35 : 1;
      const rxJ = rx * rf * (1 + (rng() - 0.5) * 0.10);
      const ryJ = ry * rf * (1 + (rng() - 0.5) * 0.10);
      const xL = rxJ * Math.cos(jitterAngle), yL = ryJ * Math.sin(jitterAngle);
      pts.push([cx + (xL * cosT - yL * sinT), cy + (xL * sinT + yL * cosT)]);
    }
    let d = `M ${pts[0][0].toFixed(1)} ${pts[0][1].toFixed(1)}`;
    for (let i = 0; i < pts.length - 1; i += 1) {
      const a = pts[i], b = pts[i + 1];
      d += ` Q ${a[0].toFixed(1)} ${a[1].toFixed(1)} ${((a[0] + b[0]) / 2).toFixed(1)} ${((a[1] + b[1]) / 2).toFixed(1)}`;
    }
    const last = pts[pts.length - 1];
    const tailDx = (rng() - 0.5) * 14, tailDy = startAtBottom ? gap + 2 : -gap - 2;
    d += ` L ${last[0].toFixed(1)} ${last[1].toFixed(1)} L ${(last[0] + tailDx).toFixed(1)} ${(last[1] + tailDy).toFixed(1)}`;
    const clipId = `oval-clip-${ovalClipCounter++}`;
    return `<svg class="freehand-oval ${kind}" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">` +
      `<clipPath id="${clipId}-out"><path fill-rule="evenodd" d="M0,0 H${W} V${H} H0 Z M${rectLeft},0 H${rectRight} V${H} H${rectLeft} Z"/></clipPath>` +
      `<clipPath id="${clipId}-in"><rect x="${rectLeft}" y="0" width="${rectRight - rectLeft}" height="${H}"/></clipPath>` +
      `<path d="${d}" fill="none" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" clip-path="url(#${clipId}-out)" opacity="0.95"/>` +
      `<path d="${d}" fill="none" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" clip-path="url(#${clipId}-in)" opacity="0.30"/>` +
      `</svg>`;
  }

  function renderLogRow(row) {
    if (row.type === "session") return `<div class="session-row"><b>${escapeHtml(row.label)}</b><span></span></div>`;
    const attempt = row.attempt;
    const duration = attempt.endTime - attempt.startTime;
    const error = displayErrorSeconds(duration, attempt.accuracy);
    const rawErrMs = signedErrorMs(attempt);
    const cls = isQuestWinAttempt(attempt) ? "win-row" : "";
    // Freehand marker oval around the run (drawn on its top row, spans groupHeight rows).
    const oval = (db.settings.twinOutlineOn && row.groupHeight) ? freehandOvalSvg(row.groupSeed, row.groupHeight, row.groupKind) : "";
    const side = isQuestWinAttempt(attempt) ? "win" : error < 0 ? "early" : error > 0 ? "late" : "";
    const dispErrMs = Math.round(error * 1000);          // = the bucket centre
    let width;
    // Capped well under the theoretical 50% half-width (was 50/48) — at the
    // old cap the bar's value label, anchored past the cap with its own text
    // width added on top, ran straight past .attempt-graph's right edge into
    // the SC column (no clipping between them). Capping the throw distance
    // itself leaves that label room to render before it gets there.
    if (row.scaleMaxMs) {
      // Tree: share the distribution histogram's scale + 0-centre so the error
      // bar ends exactly under its histogram bucket. 0 at 50%, ±scaleMax at 0/100%.
      const s = Math.max(1, row.scaleMaxMs);
      width = Math.max(0, Math.min(38, Math.abs(dispErrMs) / s * 50));
    } else {
      const scale = Math.max(100, Math.abs(rawErrMs) * 1.15, getQuest().accuracy === ACCURACY.MILLISECONDS ? 140 : 300);
      width = Math.min(36, Math.abs(rawErrMs) / scale * 48);
    }
    const label = Math.abs(error).toFixed(attempt.accuracy).replace(/^0/, "").replace(".", ",");
    return `
      <div class="attempt-row ${cls}" data-err-ms="${dispErrMs}">
        ${oval}
        <span class="attempt-number">${row.number}</span>
        <span class="attempt-time ${side}">${formatTime(duration, attempt.accuracy)}</span>
        <span class="attempt-graph">
          ${isQuestWinAttempt(attempt)
            ? `<b class="hit-dot"></b>`
            : `<b class="err-line ${side}" style="width:${width}%;${rawErrMs < 0 ? "right:50%" : "left:50%"}"></b><em class="${side}" style="${rawErrMs < 0 ? `right:${50 + width}%` : `left:${50 + width}%`}">${rawErrMs < 0 ? "−" : ""}${label}</em>`}
        </span>
        <span class="attempt-scm"></span>
      </div>
    `;
  }

  // Attempt-log scrollbar (ported from AttemptLogScrollbar in MainScreen.kt,
  // Alex 2026-08-13/14, jerkiness fix 2026-08-15): thin thumb on the right
  // edge, visible only while actually scrolling, fades ~600ms after it stops.
  // Position/size use the wrap's own scrollTop/scrollHeight/clientHeight —
  // no per-row measurement needed (unlike native's LazyColumn, the web log is
  // one continuous scrollable block, so there's no "average row size"
  // jerkiness to work around in the first place).
  let logScrollbarHideTimer = 0;
  // `scrollEl` is `.android-log-scroll` (the element that actually scrolls).
  // `.log-scrollbar`/`.log-scrollbar-thumb` live one level up, as siblings of
  // `scrollEl` inside `.android-log-wrap` — NOT inside scrollEl itself. That
  // split matters: an absolutely-positioned overlay that's a *descendant* of
  // the scrolling element scrolls away with the content (tried that first;
  // it made the thumb drift off-screen instead of staying pinned to the
  // track). Keeping the overlay as a sibling, anchored to the non-scrolling
  // outer wrap, is what makes it behave like a real scrollbar rail.
  function updateLogScrollbar(scrollEl) {
    const outerWrap = scrollEl.parentElement;
    const bar = outerWrap.querySelector(".log-scrollbar");
    const thumb = outerWrap.querySelector(".log-scrollbar-thumb");
    if (!bar || !thumb) return;
    const trackH = scrollEl.clientHeight;
    const scrollH = scrollEl.scrollHeight;
    if (trackH <= 0 || scrollH <= 0) return;
    const thumbFrac = Math.min(1, Math.max(0.08, trackH / scrollH));
    const thumbH = Math.max(24, trackH * thumbFrac);
    const maxScroll = Math.max(0, scrollH - trackH);
    const scrollFrac = maxScroll > 0 ? Math.min(1, Math.max(0, scrollEl.scrollTop / maxScroll)) : 0;
    const maxOffset = Math.max(0, trackH - thumbH);
    thumb.style.height = thumbH + "px";
    thumb.style.top = (scrollFrac * maxOffset) + "px";
    bar.classList.add("visible");
    clearTimeout(logScrollbarHideTimer);
    logScrollbarHideTimer = setTimeout(() => bar.classList.remove("visible"), 600);
  }

  // Native dynamicScaleMaxMs port: the histogram + error bars zoom to whatever
  // attempts are currently scrolled into view in the log (not the whole day).
  // Recomputed on scroll; falls back to the full-period scale when nothing is
  // visible yet (e.g. right after render, before any scroll measurement).
  function refreshDynamicScale() {
    const wrap = document.querySelector(".android-log-scroll");
    if (!wrap) return;
    const wrapRect = wrap.getBoundingClientRect();
    const rows = wrap.querySelectorAll(".attempt-row[data-err-ms]");
    let visibleMax = null;
    for (let i = 0; i < rows.length; i += 1) {
      const row = rows[i];
      const r = row.getBoundingClientRect();
      if (r.bottom > wrapRect.top && r.top < wrapRect.bottom) {
        const v = Math.abs(Number(row.dataset.errMs));
        if (visibleMax === null || v > visibleMax) visibleMax = v;
      }
    }
    const newScale = Math.max(10, visibleMax !== null ? visibleMax : mainScaleFallback);
    if (newScale === mainDynamicScaleMax) return;
    mainDynamicScaleMax = newScale;
    applyDynamicScale(newScale);
  }

  function applyDynamicScale(scaleMs) {
    const s = Math.max(1, scaleMs);
    const histogram = document.querySelector(".histogram");
    if (histogram) {
      const barPx = Number(histogram.dataset.barPx) || 1;
      histogram.querySelectorAll(".bar[data-center-ms]").forEach((bar) => {
        const centerMs = Number(bar.dataset.centerMs);
        const x = ((centerMs / s + 1) / 2) * 100;
        bar.style.left = `calc(${x}% - ${barPx / 2}px)`;
      });
    }
    const axisEl = document.querySelector(".axis-labels");
    if (axisEl) {
      const ticks = [-s, -s / 2, 0, s / 2, s].map((v) =>
        Math.abs(v) < 0.0001 ? "0" : `${v > 0 ? "+" : ""}${formatAxis(v, mainAccuracy)}`);
      [...axisEl.children].forEach((span, i) => { if (ticks[i] !== undefined) span.textContent = ticks[i]; });
    }
    document.querySelectorAll(".attempt-row[data-err-ms]").forEach((row) => {
      const errMs = Number(row.dataset.errMs);
      const errLine = row.querySelector(".err-line");
      const label = row.querySelector(".attempt-graph em");
      if (!errLine && !label) return; // win rows (hit-dot) have no bar to rescale
      const width = Math.max(0, Math.min(50, Math.abs(errMs) / s * 50));
      if (errLine) errLine.style.width = width + "%";
      if (label) {
        const negative = errMs < 0;
        label.style.right = negative ? `${50 + width}%` : "";
        label.style.left = negative ? "" : `${50 + width}%`;
      }
    });
    document.querySelectorAll(".geom-guide[data-value-ms], .geom-triangle[data-value-ms]").forEach((el) => {
      const clamped = Math.max(-s, Math.min(s, Number(el.dataset.valueMs) || 0));
      el.style.left = ((clamped / s + 1) / 2) * 100 + "%";
    });
  }

  function bindPlay() {
    const runButton = document.getElementById("runButton");
    if (runButton) {
      runButton.addEventListener("pointerdown", onRunPointerDown);
      runButton.addEventListener("pointermove", onRunPointerMove);
      // Old iOS Safari (< 13) never shipped the Pointer Events API at all, so
      // pointerdown/pointermove above silently never fire there — the only
      // thing left was the click fallback, which (being a click) only ever
      // fires on RELEASE. For a stopwatch that's exactly backwards: RUN/STOP
      // must react the instant the finger touches down. Touch Events
      // (touchstart) have existed since the first iPhone, so bind that too as
      // a press-time trigger; onRunPointerDown's own dedupe (lastRunPointerAt)
      // stops it from double-firing together with pointerdown on devices that
      // support both.
      runButton.addEventListener("touchstart", onRunPointerDown, { passive: false });
      runButton.addEventListener("click", onRunClickFallback);
    }
    document.querySelectorAll("[data-hand]").forEach((button) => {
      button.addEventListener("click", () => {
        db.settings.selectedHand = button.dataset.hand;
        saveDb();
        render();
      });
    });
    // Histogram + error bars zoom to whatever's scrolled into view (native
    // dynamicScaleMaxMs) — recompute on every scroll tick, rAF-throttled so
    // fast flicks don't queue up redundant layout work.
    const logWrapForScale = document.querySelector(".android-log-scroll");
    if (logWrapForScale) {
      let scaleScrollTicking = false;
      logWrapForScale.addEventListener("scroll", () => {
        if (scaleScrollTicking) return;
        scaleScrollTicking = true;
        requestAnimationFrame(() => { scaleScrollTicking = false; refreshDynamicScale(); });
        updateLogScrollbar(logWrapForScale);
      }, { passive: true });
      // Measure once right after render — the log may already be scrolled
      // (e.g. re-render mid-session), or simply not start at a scale that
      // matches what's actually visible at the top.
      refreshDynamicScale();
    }
    document.getElementById("welcomeClose") && document.getElementById("welcomeClose").addEventListener("click", () => {
      db.settings.welcomeShown = true;
      saveDb();
      render();
    });
    document.querySelector(".help-button") && document.querySelector(".help-button").addEventListener("click", (e) => {
      // "?" toggles help mode (it stays above the overlay, so a second tap exits).
      e.stopPropagation();
      helpMode = !helpMode;
      helpTopic = null;
      render();
    });
    bindHelpTap();
    document.getElementById("measurementDoneCard") && document.getElementById("measurementDoneCard").addEventListener("click", () => {
      measurementDone = null;
      timer.state = "ready";
      timer.elapsed = 0;
      timer.resultType = "none";
      render();
    });
    document.getElementById("sessionSummaryCard") && document.getElementById("sessionSummaryCard").addEventListener("click", () => {
      sessionSummary = null;
      timer.state = "ready";
      timer.elapsed = 0;
      timer.resultType = "none";
      render();
    });
    // No more "×" tap-to-dismiss (2026-09-04, native sync) -- the bubble is
    // now a plain non-interactive highlight, see renderCheerBubble().
    bindNav();
  }

  function activateCheerBubble(trigger) {
    if (trigger === "NONE" || !db.settings.cheerUpsEnabled) {
      cheerBubble = null;
      return;
    }
    const arrival = getQuest();
    const phase = describePhase(arrival, currentWindowLimit(), getEffectiveToleranceMs());
    const idx = db.settings.cheerPhraseIndex || 0;
    db.settings.cheerPhraseIndex = idx + 1;
    let extra = {};
    if (trigger === "AFTER_GAME" || trigger === "AFTER_LEVEL") {
      extra = {
        attempts: db.attempts.length,
        trueThrees: db.attempts.filter(isTrueThree).length,
        totalTimeStr: formatDuration(db.sessions.reduce((sum, s) => sum + (s.activeTimeMs || 0), 0))
      };
    }
    const tag = cheerQuestTag();
    const text = cheerSpecificText(trigger, phase, idx, tag, extra);
    // CTA "See your progress →" removed entirely (native, Alex 2026-08-14 —
    // see MainScreen.kt's "CTA убрана целиком" comment); no more offersLink.
    cheerBubble = { trigger, text, attemptsLeft: 1 };
    // The "?" help-tap overlay and the bubble occupy the same screen area —
    // if help mode was already on when the bubble appears, drop it so a tap
    // meant to close the bubble can't land on a help zone instead.
    helpMode = false;
    helpTopic = null;
  }

  // Plain fade highlight (2026-09-04, native sync — was a text bubble with
  // a "×" close button through 2026-08-23; MainScreen.kt now replaces it
  // with a wordless amber glow BEHIND the quest panel, Alex 01.09: "бабл
  // удаляется полностью"). cheerSpecificText()/tidyLineBreaks() etc. above
  // still compute and store `cheerBubble.text` on every trigger — same
  // "disable but don't delete" as native's CheerUpBubble.kt/
  // CheerUpMessages.kt (kept in case Alex wants the text back) — it's just
  // no longer read by anything that renders to the DOM.
  //
  // This app re-renders by replacing app.innerHTML wholesale, so a real
  // element can't be kept alive across renders() to animate its own
  // removal — dismissCheerBubble() nulls `cheerBubble` and triggers a real
  // re-render (which removes it), THEN hands a visual clone ("ghost") of
  // the just-removed element to spawnCheerBubbleGhost(), which lives
  // outside #app (appended to <body>) so later renders/ticks can't touch or
  // interrupt it while it plays out its own exit fade. Native's dismiss is
  // opacity-only (AnimatedVisibility's plain fadeOut, no shrink) — matched
  // here instead of the old shrink-from-bottom animation, which no longer
  // makes sense for a borderless/paddingless highlight.
  function dismissCheerBubble() {
    if (!cheerBubble) return;
    const live = document.querySelector(".cheer-bubble");
    spawnCheerBubbleGhost(live);
    cheerBubble = null;
  }

  function spawnCheerBubbleGhost(liveEl) {
    let rect = liveEl ? liveEl.getBoundingClientRect() : null;
    if (rect && (rect.width <= 0 || rect.height <= 0)) {
      // First bad read: force a reflow and measure once more before giving
      // up — cheap, and covers a layout-not-yet-committed race.
      void liveEl.offsetHeight;
      rect = liveEl.getBoundingClientRect();
    }
    const validRect = rect && rect.width > 0 && rect.height > 0;
    const ghost = document.createElement("div");
    ghost.className = "cheer-bubble cheer-bubble-ghost cheer-ghost-run";
    if (validRect) {
      ghost.style.left = rect.left + "px";
      ghost.style.top = rect.top + "px";
      ghost.style.width = rect.width + "px";
      ghost.style.height = rect.height + "px";
    } else {
      // Fallback geometry: the quest-strip's own rect, since the highlight
      // fills it exactly (see .cheer-bubble's inset:0). Covers the same
      // "couldn't measure it on a real device" case the old text-bubble
      // ghost defended against (Alex, 2026-08-23).
      const anchor = document.querySelector(".quest-strip");
      const anchorRect = anchor ? anchor.getBoundingClientRect() : null;
      if (anchorRect) {
        ghost.style.left = anchorRect.left + "px";
        ghost.style.top = anchorRect.top + "px";
        ghost.style.width = anchorRect.width + "px";
        ghost.style.height = anchorRect.height + "px";
      } else {
        ghost.style.left = "12px";
        ghost.style.right = "12px";
        ghost.style.top = "2px";
        ghost.style.height = "60px";
      }
    }
    document.body.appendChild(ghost);
    void ghost.offsetHeight; // force layout so the class-added transition below actually animates
    requestAnimationFrame(() => ghost.classList.add("cheer-ghost-out"));
    setTimeout(() => ghost.remove(), 1500 + 80);
  }

  function renderCheerBubble() {
    // Plain amber highlight, no text, no close button — see the
    // dismissCheerBubble() comment above for why the old bubble markup was
    // retired. pointer-events:none (CSS) so it never intercepts taps meant
    // for the panel above it.
    return `<div class="cheer-bubble" aria-hidden="true"></div>`;
  }

  // Salute celebration (ported from SaluteCelebration.kt, Alex 2026-08-16/17):
  // the same ring/triangle/letter art as the About-screen .pot-logo, reused here
  // as standalone constants because the greeting-screen instance needs its
  // rotation driven frame-by-frame from JS (locked to the same clock as the
  // firework particles) instead of the independent CSS @keyframes the About
  // screen/homepage launcher use -- see renderSaluteLogo()/startSalute() below.
  const SALUTE_LOGO_RING_SRC = "data:image/png;base64,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";
  const SALUTE_LOGO_TRIANGLE_SRC = "data:image/png;base64,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";
  const SALUTE_LOGO_LETTER_SRC = "data:image/png;base64,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";

  // Firework "salute" celebration (ported from SaluteCelebration.kt, Alex
  // 2026-08-16/17) -- a synced two-touch rocket+burst particle system
  // overlaid on the same ring/triangle/letter brand mark used elsewhere,
  // replacing the plain .party-icon on the post-quest greeting screen (native
  // comment: "replaces the old pulsing ic_win icon"). Ring/triangle/letter
  // here are driven frame-by-frame from this same clock (not the independent
  // CSS @keyframes .pot-logo uses on the About screen/homepage) so the
  // rockets launch exactly when the ring/triangle visually "touch".
  const SALUTE_CYCLE_MS = 4000;
  const SALUTE_P0_DEG = 60.515;
  const SALUTE_RING_SWEEP_DEG = 360;
  const SALUTE_T_TOUCH_MS = 2062.2;
  const SALUTE_T_TOUCH_R_MS = 3895.6;
  const SALUTE_ROCKET_RISE_MS = 550;
  const SALUTE_BURST_MS = 950;
  const SALUTE_H_ROCKET_BASE = 170;
  const SALUTE_APEX_TARGET_X = 160;
  const SALUTE_OX_L = 41.875, SALUTE_OY_L = 156.875;
  const SALUTE_OX_R = 278.125, SALUTE_OY_R = 156.25;
  const SALUTE_EXPLODE_Y_L = SALUTE_OY_L - SALUTE_H_ROCKET_BASE;
  const SALUTE_EXPLODE_Y_R = SALUTE_OY_R - SALUTE_H_ROCKET_BASE;
  const SALUTE_EXPLODE_MS_L = (SALUTE_T_TOUCH_MS + SALUTE_ROCKET_RISE_MS) % SALUTE_CYCLE_MS;
  const SALUTE_EXPLODE_MS_R = (SALUTE_T_TOUCH_R_MS + SALUTE_ROCKET_RISE_MS) % SALUTE_CYCLE_MS;
  const SALUTE_ROCKET_PIECES = 5;
  const SALUTE_BURST_PIECES = 34;
  const SALUTE_MARGIN_SIDE = 60, SALUTE_MARGIN_TOP = 170, SALUTE_MARGIN_BOTTOM = 30;
  const SALUTE_CANVAS_W = 320 + 2 * SALUTE_MARGIN_SIDE;
  const SALUTE_CANVAS_H = 320 + SALUTE_MARGIN_TOP + SALUTE_MARGIN_BOTTOM;
  const SALUTE_ROCKET_COLORS = ["#FFE07A", "#FFD35E", "#FFFFFF"];
  const SALUTE_BURST_COLORS = ["#E63946", "#FF5C5C", "#3A86FF", "#4CC9F0", "#2ECC71", "#6BCB77"];

  // Small seedable PRNG (mulberry32) -- NOT bit-identical to Kotlin's
  // Random(seed) (different algorithm entirely), just a stable, reproducible
  // "looks the same every time, but not literally identical to native"
  // generator, since exact particle-for-particle parity isn't the point --
  // a plausible, consistent scatter is.
  function saluteRng(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function saluteProjectileY(height, apexFrac, tFrac) {
    const u = tFrac / apexFrac;
    return height * u * (2 - u);
  }

  function saluteWobbleRotation(tFrac, rot0, rot1, amp, freq, phase) {
    return rot0 + (rot1 - rot0) * tFrac + amp * Math.sin(2 * Math.PI * freq * tFrac + phase);
  }

  function saluteWindowTFrac(phase, start, dur) {
    const end = start + dur;
    if (end <= SALUTE_CYCLE_MS) {
      return (phase >= start && phase <= end) ? (phase - start) / dur : null;
    }
    const wrappedEnd = end - SALUTE_CYCLE_MS;
    if (phase >= start) return (phase - start) / dur;
    if (phase < wrappedEnd) return (phase + SALUTE_CYCLE_MS - start) / dur;
    return null;
  }

  function saluteBuildRockets(rnd, originX, originY, launchMs) {
    const out = [];
    for (let i = 0; i < SALUTE_ROCKET_PIECES; i++) {
      const rot0 = rnd() * 360;
      const delta = (300 + rnd() * 320) * (rnd() < 0.5 ? -1 : 1);
      out.push({
        originX, originY, launchMs,
        height: SALUTE_H_ROCKET_BASE + rnd() * 40 - 20,
        dxLaunch: rnd() * 20 - 10,
        width: 3 + rnd() * 2,
        heightPx: 7 + rnd() * 5,
        color: SALUTE_ROCKET_COLORS[Math.floor(rnd() * SALUTE_ROCKET_COLORS.length)],
        rot0, rot1: rot0 + delta,
        wAmp: 10 + rnd() * 12,
        wFreq: 1.2 + rnd() * 1.2,
        wPhase: rnd() * 2 * Math.PI
      });
    }
    return out;
  }

  function saluteBuildBursts(rnd, originX, originY, explodeMs) {
    const out = [];
    for (let i = 0; i < SALUTE_BURST_PIECES; i++) {
      const sideSign = rnd() < 0.5 ? -1 : 1;
      const rot0 = rnd() * 360;
      const delta = (260 + rnd() * 300) * (rnd() < 0.5 ? -1 : 1);
      out.push({
        originX, originY, explodeMs,
        dxTotal: sideSign * (60 + rnd() * 150),
        height: 30 + rnd() * 100,
        apexFrac: 0.25 + rnd() * 0.2,
        width: 5 + rnd() * 6,
        heightPx: 3 + rnd() * 3,
        color: SALUTE_BURST_COLORS[Math.floor(rnd() * SALUTE_BURST_COLORS.length)],
        rot0, rot1: rot0 + delta,
        wAmp: 20 + rnd() * 25,
        wFreq: 1.5 + rnd() * 1.7,
        wPhase: rnd() * 2 * Math.PI
      });
    }
    return out;
  }

  function saluteDrawParticle(ctx, canvasX, canvasY, wUnits, hUnits, rotDeg, particleScale, color) {
    ctx.save();
    ctx.translate(canvasX, canvasY);
    ctx.rotate(rotDeg * Math.PI / 180);
    ctx.fillStyle = color;
    const w = wUnits * particleScale, h = hUnits * particleScale;
    ctx.fillRect(-w / 2, -h / 2, w, h);
    ctx.restore();
  }

  // Two-tier sound (ported from SaluteSoundPlayer, Alex 2026-08-16): short
  // "hit" clips at the two touch instants, plus a continuous quieter ambient
  // bed for as long as the greeting screen is up. Clips are cloned per play
  // (not reused/currentTime-reset) -- same "avoid a stuck shared element on a
  // fast repeat trigger" reasoning as playOnce() elsewhere in this file.
  const SALUTE_CLIP_COUNT = 9;
  let saluteAudioPool = null;
  function saluteAudio() {
    if (saluteAudioPool) return saluteAudioPool;
    const salute = [], ambient = [];
    for (let i = 0; i < SALUTE_CLIP_COUNT; i++) {
      const s = new Audio(`./assets/salute_clip_${i}.m4a`);
      s.preload = "auto";
      salute.push(s);
      const a = new Audio(`./assets/ambient_clip_${i}.m4a`);
      a.preload = "auto";
      ambient.push(a);
    }
    saluteAudioPool = { salute, ambient };
    return saluteAudioPool;
  }
  let saluteAmbientTimer = 0;
  let saluteAmbientCurrent = null;
  // Service/celebration sounds (hooray, sorry, salute) play unconditionally,
  // like playSuccessSound()/playSorrySound() elsewhere in this file — `Sound`
  // in Settings only gates the per-second metronome tick (playTone/
  // startMetronome). Gating these two on db.settings.soundOn (2026-08-17
  // port) was a mistake — fixed 2026-08-23, Alex confirmed the intended
  // split explicitly. (2026-09-10): now gated on the separate
  // `saluteResultSoundsOn` setting instead — still intentionally independent
  // from the metronome's `soundOn`.
  function salutePlayHit() {
    if (!db.settings.saluteResultSoundsOn) return;
    const pool = saluteAudio();
    const inst = pool.salute[Math.floor(Math.random() * pool.salute.length)].cloneNode();
    inst.volume = 1;
    keepAudioAlive(inst); // 2026-09-10 native parity, item 6 — see playOnce() above
    inst.play().catch((err) => logPlayRejection("salute hit", err));
  }
  function saluteStartAmbient() {
    if (!db.settings.saluteResultSoundsOn) return;
    const pool = saluteAudio();
    const playNext = () => {
      const inst = pool.ambient[Math.floor(Math.random() * pool.ambient.length)].cloneNode();
      inst.volume = 0.4;
      saluteAmbientCurrent = inst;
      keepAudioAlive(inst); // 2026-09-10 native parity, item 6 — see playOnce() above
      inst.play().catch((err) => logPlayRejection("salute ambient", err));
      inst.onended = () => {
        saluteAmbientTimer = setTimeout(playNext, 1400 + Math.random() * 1800);
      };
    };
    playNext();
  }
  function saluteStopAmbient() {
    clearTimeout(saluteAmbientTimer);
    if (saluteAmbientCurrent) {
      saluteAmbientCurrent.onended = null;
      saluteAmbientCurrent.pause();
      saluteAmbientCurrent = null;
    }
  }

  let saluteRaf = 0;
  let saluteElapsedMs = 0;
  let saluteLastPhase = -1;
  let saluteLastTs = 0;
  let saluteRockets = null;
  let saluteBursts = null;

  function renderSaluteLogo() {
    return `
      <div class="salute-wrap">
        <canvas class="salute-canvas"></canvas>
        <div class="salute-logo">
          <img class="pot-layer salute-ring" src="${SALUTE_LOGO_RING_SRC}">
          <img class="pot-layer salute-triangle" src="${SALUTE_LOGO_TRIANGLE_SRC}">
          <img class="pot-layer salute-letter" src="${SALUTE_LOGO_LETTER_SRC}">
        </div>
      </div>`;
  }

  function startSalute() {
    const wrap = document.querySelector(".salute-wrap");
    if (!wrap) return;
    const canvas = wrap.querySelector(".salute-canvas");
    const ring = wrap.querySelector(".salute-ring");
    const triangle = wrap.querySelector(".salute-triangle");
    const letter = wrap.querySelector(".salute-letter");
    if (!canvas || !ring || !triangle || !letter) return;

    const rndR = saluteRng(31);
    saluteRockets = saluteBuildRockets(rndR, SALUTE_OX_L, SALUTE_OY_L, SALUTE_T_TOUCH_MS)
      .concat(saluteBuildRockets(rndR, SALUTE_OX_R, SALUTE_OY_R, SALUTE_T_TOUCH_R_MS));
    const rndB = saluteRng(37);
    saluteBursts = saluteBuildBursts(rndB, SALUTE_APEX_TARGET_X, SALUTE_EXPLODE_Y_L, SALUTE_EXPLODE_MS_L)
      .concat(saluteBuildBursts(rndB, SALUTE_APEX_TARGET_X, SALUTE_EXPLODE_Y_R, SALUTE_EXPLODE_MS_R));

    saluteElapsedMs = 0;
    saluteLastPhase = -1;
    saluteLastTs = 0;
    const ctx = canvas.getContext("2d");
    saluteStartAmbient();

    function frame(ts) {
      // Greeting screen may have been dismissed (onTakeNext/render) since
      // the last frame was scheduled -- stop cleanly instead of drawing into
      // a canvas that's no longer attached to anything on screen.
      if (!document.body.contains(canvas)) { saluteRaf = 0; return; }
      if (!saluteLastTs) saluteLastTs = ts;
      const dt = Math.min(50, ts - saluteLastTs); // clamp: a bg-tab throttle
      saluteLastTs = ts;                          // shouldn't send one huge jump
      saluteElapsedMs += dt;
      const phase = saluteElapsedMs % SALUTE_CYCLE_MS;

      if (saluteLastPhase >= 0) {
        const crossedLeft = (saluteLastPhase < SALUTE_T_TOUCH_MS && phase >= SALUTE_T_TOUCH_MS) ||
          (phase < saluteLastPhase && SALUTE_T_TOUCH_MS >= saluteLastPhase);
        const crossedRight = (saluteLastPhase < SALUTE_T_TOUCH_R_MS && phase >= SALUTE_T_TOUCH_R_MS) ||
          (phase < saluteLastPhase && SALUTE_T_TOUCH_R_MS >= saluteLastPhase);
        if (crossedLeft || crossedRight) salutePlayHit();
      }
      saluteLastPhase = phase;

      const ringAngle = SALUTE_P0_DEG + (phase / SALUTE_CYCLE_MS) * SALUTE_RING_SWEEP_DEG;
      const triAngle = (phase / SALUTE_CYCLE_MS) * 360;
      const letAngle = -(phase / SALUTE_CYCLE_MS) * 360;
      ring.style.transform = `rotate(${ringAngle}deg)`;
      triangle.style.transform = `rotateY(${triAngle}deg)`;
      letter.style.transform = `rotateY(${letAngle}deg)`;

      const rect = canvas.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      const wantW = Math.max(1, Math.round(rect.width * dpr));
      const wantH = Math.max(1, Math.round(rect.height * dpr));
      if (canvas.width !== wantW || canvas.height !== wantH) {
        canvas.width = wantW;
        canvas.height = wantH;
      }
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.scale(canvas.width / SALUTE_CANVAS_W, canvas.height / SALUTE_CANVAS_H);

      saluteRockets.forEach((spec) => {
        const tFrac = saluteWindowTFrac(phase, spec.launchMs, SALUTE_ROCKET_RISE_MS);
        if (tFrac == null) return;
        const dx = spec.dxLaunch * (1 - tFrac) + (SALUTE_APEX_TARGET_X - spec.originX) * tFrac;
        const dy = -saluteProjectileY(spec.height, 1, tFrac);
        const rot = saluteWobbleRotation(tFrac, spec.rot0, spec.rot1, spec.wAmp, spec.wFreq, spec.wPhase);
        saluteDrawParticle(ctx, SALUTE_MARGIN_SIDE + spec.originX + dx, SALUTE_MARGIN_TOP + spec.originY + dy,
          spec.width, spec.heightPx, rot, 1 - 0.15 * tFrac, spec.color);
      });
      saluteBursts.forEach((spec) => {
        const tFrac = saluteWindowTFrac(phase, spec.explodeMs, SALUTE_BURST_MS);
        if (tFrac == null) return;
        const dx = spec.dxTotal * tFrac;
        const dy = -saluteProjectileY(spec.height, spec.apexFrac, tFrac);
        const rot = saluteWobbleRotation(tFrac, spec.rot0, spec.rot1, spec.wAmp, spec.wFreq, spec.wPhase);
        saluteDrawParticle(ctx, SALUTE_MARGIN_SIDE + spec.originX + dx, SALUTE_MARGIN_TOP + spec.originY + dy,
          spec.width, spec.heightPx, rot, 1.2 - 0.5 * tFrac, spec.color);
      });

      saluteRaf = requestAnimationFrame(frame);
    }
    saluteRaf = requestAnimationFrame(frame);
  }

  function stopSalute() {
    if (saluteRaf) cancelAnimationFrame(saluteRaf);
    saluteRaf = 0;
    saluteStopAmbient();
  }

  function renderGreeting() {
    const g = db.greeting;
    const minimal = g.levelId < 2;
    const showThrees = g.levelId >= 3;
    const goldBlock = g.isLevelComplete ? `
      <div class="gold-block">
        <div class="gold-title">★ LEVEL ${g.levelId} COMPLETE</div>
        <div class="gold-sub">${g.isGameComplete ? "All 15 quests done — game finished!" : "Precision moves to " + g.nextPrecisionLabel}</div>
        ${g.levelAttempts > 0 ? `<div class="gold-stats">5/5 quests · ${g.levelAttempts} attempts${showThrees ? " · " + g.levelTrueThrees + " clean threes" : ""}</div>` : ""}
      </div>` : "";
    app.innerHTML = `
      <section class="greeting-screen">
        <div class="greeting-content completion-content">
          ${renderSaluteLogo()}
          ${g.congrats && !minimal ? `<h1 class="greet-congrats">${escapeHtml(g.congrats)}</h1>` : ""}
          <div class="greet-badge">Level ${g.levelId} · Quest ${g.taskId} — Complete!</div>
          ${goldBlock}
          <p class="greet-time">You spent ${formatDuration(g.playTimeMs)} of pure play time on this quest.</p>
          ${!minimal ? `<p class="greet-coins">Your coins: ${formatCoins(g.earnedScm)} Serenity.</p>` : ""}
          ${g.interpUnlocked ? buildInterpTable(g.sessionId) : `<div class="greet-chips">
            <div><b>${g.sessionAttempts}</b><span>Attempts</span></div>
            <div><b>${g.sessionWins}</b><span>Wins</span></div>
            <div><b>${g.winRatePct}%</b><span>Hit Rate</span></div>
          </div>`}
          <p class="greet-praise">${escapeHtml(praiseFor(g.arrivalLevelId, g.arrivalTaskId))}</p>
          <button id="takeNextGreeting" class="take-next">TAKE NEXT QUEST</button>
        </div>
      </section>`;
    document.getElementById("takeNextGreeting") && document.getElementById("takeNextGreeting").addEventListener("click", onTakeNext);
    stopSalute();
    startSalute();
  }

  function onTakeNext() {
    stopSalute();
    // Actually switch the quest pointer NOW (Alex 2026-07-30 native behavior,
    // ported 2026-08-17) — the moment the player has seen GreetingView and
    // tapped through, not back when they won. Must happen BEFORE
    // activateCheerBubble() below, which reads the (now current) quest to
    // build the "what's next" bubble text.
    if (db.greeting && db.greeting.pendingQuest) {
      const g = db.greeting;
      const next = g.pendingNext ? currentQuestFrom(g.pendingNext.levelId, g.pendingNext.taskId) : null;
      const quest = currentQuestFrom(g.pendingQuest.levelId, g.pendingQuest.taskId);
      // Close the just-won session HERE, not at win time (see the "session
      // is deliberately left OPEN" comment in onQuestWindowSuccess) — same
      // moment the pointer itself advances, so the targets/text/arrows on
      // screen hold the completed quest's fully-landed state right up
      // until this tap, then flip to the next quest all at once.
      if (g.sessionId != null) closeSession(g.sessionId, true);
      commitQuestAdvance(next, quest);
    }
    displayGreeting = false;
    db.greeting = null;
    activateCheerBubble(pendingTrigger);
    pendingTrigger = "NONE";
    timer.state = "ready";
    timer.elapsed = 0;
    timer.resultType = "none";
    saveDb();
    render();
  }

  // FailCard (Alex 2026-09-22 native, ported 2026-09-25) -- full-screen
  // window-failure card shown for ANY level (no level gating in native either
  // -- confirmed by direct source read). Symmetric to GreetingView on the
  // fail side: face of the failed window (report.html?mode=face, same engine
  // as the Analytics face card), attempts/wins, task description, reframe
  // copy, next-phase line. Dismiss ONLY via the button -- background tap does
  // NOT dismiss (native rule, Alex 2026-07-13). Copy text below is native's
  // exact strings (failCopy() in FailCard.kt), not adapted.
  const FAIL_CARD_COPY = {
    AFTER_SUBQUEST_ENTER: [
      "The mark widens one step",
      "The window ran out this pass, so the mark eases a notch. The aim stays the " +
        "same — you just have more room to land it.",
      "Everything you've built so far is still yours. Beat this easier setting cleanly " +
        "and the original quest counts as done."
    ],
    AFTER_SUBQUEST_DEEPER: [
      "One more notch of ease",
      "The threes still scattered. We're loosening the mark once more — no rush, we're " +
        "not going anywhere.",
      "Try slowing the tempo between attempts. Fatigue and rush cost more accuracy than " +
        "the extra tolerance can give back."
    ],
    AFTER_SUBQUEST_CAPPED: [
      "As easy as it gets",
      "The mark is at its widest now. From here on there's nothing left to loosen — " +
        "it's just you and the rhythm.",
      "Slow your breath before each Run. Let the fingers rest between attempts. Precision " +
        "comes from calm, not from effort."
    ],
    AFTER_SUBQUEST_STALLED: [
      "Time to step away",
      "Full ease and the threes still scatter — this is a fatigue signal, not a skill signal. " +
        "The window will be here fresh whenever you come back.",
      "Take a real break. Sleep on it. When you return, the same 99 attempts wait — and the " +
        "clarity that comes with rest will carry you through them."
    ]
  };

  function failCopy(trigger) {
    return FAIL_CARD_COPY[trigger] || [
      "Task paused",
      "The window closed — we're easing the conditions to keep you moving.",
      "Come back with fresh focus."
    ];
  }

  function renderFailCard() {
    const d = failCard;
    const copy = failCopy(d.trigger);
    const title = copy[0], opener = copy[1], coach = copy[2];
    const faceUrl = identity.playerId && d.fromMs > 0 && d.toMs > 0
      ? `https://playoftime.online/report.html?playerId=${encodeURIComponent(identity.playerId)}&token=${encodeURIComponent(identity.syncToken)}&mode=face&fromMs=${d.fromMs}&toMs=${d.toMs}&min=1`
      : "";
    const btnLabel = d.trigger === "AFTER_SUBQUEST_STALLED" ? "REST — I'LL BE BACK" : "GOT IT";
    app.innerHTML = `
      <section class="fail-card-screen">
        <div class="fail-card-content">
          <div class="fc-face">${faceUrl ? `<iframe id="failCardFace" src="${faceUrl}"></iframe>` : ""}</div>
          <div class="fc-face-label">Session face</div>
          <h1 class="fc-title">${escapeHtml(title)}</h1>
          <div class="fc-stats">
            <div><b>${d.attempts}</b><span>Attempts</span></div>
            <div><b>${d.wins}</b><span>Wins</span></div>
          </div>
          <p class="fc-task">Task: ${escapeHtml(d.prevPhaseDescription)}</p>
          <p class="fc-opener">${escapeHtml(opener)}</p>
          <p class="fc-coach">${escapeHtml(coach)}</p>
          <p class="fc-now">Now: ${escapeHtml(d.newPhaseDescription)}.</p>
          <button id="failCardGotIt" class="fc-btn" type="button">${escapeHtml(btnLabel)}</button>
        </div>
      </section>`;
    document.getElementById("failCardGotIt") && document.getElementById("failCardGotIt").addEventListener("click", () => {
      failCard = null;
      activateCheerBubble(pendingTrigger);
      pendingTrigger = "NONE";
      saveDb();
      render();
    });
  }

  function onRunPointerDown(event) {
    // Bound to BOTH "pointerdown" and "touchstart" (see bindPlay) so old iOS
    // Safari — which never implemented Pointer Events — still fires on press.
    // Devices that support both fire both events for the same physical touch;
    // skip the second one so RUN/STOP doesn't trigger twice per tap.
    const now = Date.now();
    if (now - lastRunPointerAt < 200) return;
    lastRunPointerAt = now;
    event.preventDefault();
    feedTouchFromEvent(event);
    onRunButton();
  }

  function onRunPointerMove(event) {
    feedTouchFromEvent(event);
  }

  // Estimate the input cell from coalesced pointer samples (honest panel rate).
  function feedTouchFromEvent(event) {
    const times = [];
    if (typeof event.getCoalescedEvents === "function") {
      for (const e of event.getCoalescedEvents()) times.push(e.timeStamp);
    }
    if (times.length >= 2) touchFeed(times);
  }

  function onRunClickFallback(event) {
    if (Date.now() - lastRunPointerAt < 500) {
      event.preventDefault();
      return;
    }
    onRunButton();
  }

  // Firm, clearly-felt haptic for the RUN / STOP presses (Android ignores <~20 ms).
  // UNCONDITIONAL — this is the start/stop button feedback, NOT the Settings
  // "Vibration" toggle (that one is the per-second metronome buzz).
  const PRESS_VIBRATE_MS = 60;
  function pressHaptic() {
    if (navigator.vibrate) navigator.vibrate(PRESS_VIBRATE_MS);
  }

  function onRunButton() {
    if (timer.state === "ready") {
      // RUN pressed while a cheer-up bubble is showing dismisses it right
      // away (Alex 2026-08-14 native, ported 2026-08-17) — with a slow 1.5s
      // "deflate" (shrinks from the bottom up) instead of the quick fade used
      // by the "×"/next-attempt paths. The timer itself still starts
      // instantly below; the bubble's exit animation runs independently (see
      // dismissCheerBubble/spawnCheerBubbleGhost) so it doesn't block RUN.
      if (cheerBubble) dismissCheerBubble();
      db.lastMessage = "";
      timer.state = "running";
      timer.resultType = "none";
      timer.elapsed = 0;
      timer.startWall = Date.now();
      timer.startPerf = performance.now();
      if (!currentSession()) makeSession(timer.startWall);
      startPlayTicker();
      startMetronome();
      pressHaptic();   // AFTER startMetronome so its first tick can't override it
      saveDb();
      tick();
      render();
      return;
    }
    if (timer.state === "running") {
      stopTimer();
      return;
    }
    // Result state tap.
    if (awaitingHurray) {
      // HOORAY! → show the GreetingView card.
      awaitingHurray = false;
      displayGreeting = true;
      render();
      return;
    }
    // Fail / stepback RESET tap → arm the subquest cheer-up bubble for the next session.
    // Native FailCard port (Alex 2026-09-25): a window-failure trigger shows a
    // full-screen FailCard (face + stats + reframe copy) FIRST; the plain-text
    // bubble is deferred until FailCard's GOT IT/REST tap (see renderFailCard).
    if (pendingFailCard) {
      failCard = pendingFailCard;
      pendingFailCard = null;
      db.lastMessage = "";
      timer.state = "ready";
      timer.elapsed = 0;
      timer.resultType = "none";
      saveDb();
      render();
      return;
    }
    if (pendingTrigger !== "NONE") {
      activateCheerBubble(pendingTrigger);
      pendingTrigger = "NONE";
    }
    db.lastMessage = "";
    timer.state = "ready";
    timer.elapsed = 0;
    timer.resultType = "none";
    saveDb();
    render();
  }

  function tick() {
    if (timer.state !== "running") return;
    timer.elapsed = performance.now() - timer.startPerf;
    if (timer.elapsed >= MAX_ATTEMPT) {
      stopMetronome();
      timer.state = "ready";
      timer.elapsed = 0;
      timer.resultType = "none";
      render();
      return;
    }
    const display = document.getElementById("timeDisplay");
    if (display) display.textContent = db.settings.numericTime === false ? "•••" : formatTime(timer.elapsed, getQuest().accuracy);
    const frac = (progressFrac(timer.elapsed) * 100) + "%";
    const fill = document.getElementById("pbFill");
    if (fill) fill.style.width = frac;
    const beak = document.getElementById("pbBeak");
    if (beak) beak.style.left = frac;
    const button = document.getElementById("runButton");
    if (button) button.classList.toggle("stop-active", timer.elapsed >= MIN_ATTEMPT);
    timer.raf = requestAnimationFrame(tick);
  }

  function stopTimer() {
    const elapsed = performance.now() - timer.startPerf;
    if (elapsed < MIN_ATTEMPT) {
      timer.tooEarlyUntil = Date.now() + 180;
      playNack();
      return;
    }
    cancelAnimationFrame(timer.raf);
    stopMetronome();
    if (elapsed > MAX_ATTEMPT) {
      timer.state = "ready";
      timer.elapsed = 0;
      render();
      return;
    }
    const rawDuration = Math.round(elapsed);
    const attemptDuration = applyDither(rawDuration);
    const endTime = timer.startWall + attemptDuration;
    const { attempt, result } = registerAttempt(timer.startWall, endTime);
    // Bucket centre of this attempt → one-shot "drop" grow of that histogram bar.
    const landTick = accuracyStepMs();
    justLandedMs = Math.round(Math.round(displayErrorSeconds(attemptDuration, attempt.accuracy) * 1000) / landTick) * landTick;
    animateProgressHalf(); // adapt the auto scale so the beak stays in view (smooth 1 s)
    timer.elapsed = attempt.endTime - attempt.startTime;
    timer.state = "result";
    timer.resultType = isQuestWinAttempt(attempt) ? "win" : signedErrorMs(attempt) < 0 ? "early" : "late";
    // Firm STOP/fixation pulse — UNCONDITIONAL (start/stop feedback). Fires after
    // stopMetronome()'s vibrate(0), so it is the last vibrate call and isn't cancelled.
    pressHaptic();
    // Measurement version: every hit (target taken) plays the hooray sound —
    // there is no quest to complete. The 33rd attempt opens the completion card.
    if (timer.resultType === "win") playSuccessSound();
    if (result.type === "measurement") { measurementDone = result.record; syncMeasurements(); }
    saveDb();
    render();
  }

  function luckyIndexOf(attempts) {
    if (!attempts.length) return null;
    const errs = attempts.map(signedErrorMs);
    const wins = attempts.filter(isTrueThree).length;
    if (!wins) return null; // "no luck" — no clean threes at all
    const accCounts = {};
    attempts.forEach((a) => { accCounts[a.accuracy] = (accCounts[a.accuracy] || 0) + 1; });
    const majAcc = Number(Object.entries(accCounts).sort((a, b) => b[1] - a[1])[0][0]);
    const slot = majAcc === 3 ? 1 : majAcc === 2 ? 10 : 100;
    const range = Math.max(...errs) - Math.min(...errs);
    const outcomeSlots = Math.max(1, Math.trunc(range / slot)) + 1;
    const dispersion = 1 / outcomeSlots;
    return (wins / attempts.length) / dispersion;
  }

  function meanCell(attempts) {
    const errs = attempts.map(signedErrorMs);
    const early = geometricMean(errs.filter((v) => v < 0).map(Math.abs));
    const late = geometricMean(errs.filter((v) => v > 0));
    const e = early ? "−" + Math.round(early) : "·";
    const l = late ? "+" + Math.round(late) : "·";
    return `<span class="early">${e}</span> / <span class="late">${l}</span>`;
  }

  // Wins with tolerance, the same rule as "Attempts per win" on the card
  // (Alex 2026-10-06) -- it used to count precise hits only.
  function att4winCell(attempts) {
    const wins = countTrueThreeTol(attempts);
    return wins ? (attempts.length / wins).toFixed(1) : "—";
  }

  function luckyCell(attempts) {
    const l = luckyIndexOf(attempts);
    return l == null ? `<span class="muted-cell">no luck</span>` : l.toFixed(2);
  }

  function treeNodeRow(cls, indentPad, label, finger, attempts, sub, caret, toggleAttr) {
    const car = caret ? `<span class="tn-caret">${caret} </span>` : "";
    return `<div class="tree-row ${cls}${toggleAttr ? " clickable" : ""}" style="padding-left:${indentPad}px"${toggleAttr ? " " + toggleAttr : ""}>
      <span class="tn-name">${car}${label}${finger}${sub ? `<span class="tn-sub">${sub}</span>` : ""}</span>
      <span class="tn-lucky">${luckyCell(attempts)}</span>
      <span class="tn-mean">${meanCell(attempts)}</span>
      <span class="tn-a4w">${att4winCell(attempts)}</span>
    </div>`;
  }

  function toggleSet(set, key) { if (set.has(key)) set.delete(key); else set.add(key); }

  function bindPaceMetric() {
    const sel = document.getElementById("paceMetric");
    if (!sel) return;
    sel.addEventListener("change", () => {
      const scroller = document.querySelector(".analytics-content");
      const top = scroller ? scroller.scrollTop : 0;
      paceMetric = sel.value;
      render();
      const after = document.querySelector(".analytics-content");
      if (after) after.scrollTop = top;
    });
  }

  function bindTree() {
    if (!treeExpanded) return;
    const wire = (attr, set, cast) => document.querySelectorAll(`[data-${attr}]`).forEach((el) =>
      el.addEventListener("click", () => {
        // Keep the scroll position — a full re-render otherwise jumps to the top.
        const scroller = document.querySelector(".analytics-content");
        const top = scroller ? scroller.scrollTop : 0;
        toggleSet(set, cast(el.dataset[attr]));
        render();
        const after = document.querySelector(".analytics-content");
        if (after) after.scrollTop = top;
      }));
    wire("tyear", treeExpanded.years, Number);
    wire("tmonth", treeExpanded.months, String);
    wire("tweek", treeExpanded.weeks, String);
    wire("tday", treeExpanded.days, String);
    wire("tsession", treeExpanded.sessions, Number);
  }

  function buildTreeAttemptRows(attempts) {
    const sorted = [...attempts].sort((a, b) => a.startTime - b.startTime);
    // Same scale as the histogram below → error bars share its 0-axis + scale.
    const scaleMaxMs = buildDistribution(attempts).scaleMax;
    const rows = sorted.map((a, i) => renderLogRow({ type: "attempt", attempt: a, number: i + 1, isNewest: false, scaleMaxMs })).join("");
    // The distribution histogram sits DIRECTLY BELOW the error diagram, aligned to
    // the same plot band (padding matches the attempt-row graph column).
    const hist = distributionChartHtml(attempts, db.settings.accuracy);
    return `<div class="tree-attempts">${rows}${hist ? `<div class="tree-distrib">${hist}</div>` : ""}</div>`;
  }

  function finger(up) {
    if (up == null) return "";
    const path = up
      ? "M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-1.91l-.01-.01L23 10z"
      : "M15 3H6c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v1.91l.01.01L1 14c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 23l6.59-6.59c.36-.36.58-.86.58-1.41V5c0-1.1-.9-2-2-2zm4 0v12h4V3h-4z";
    return ` <svg class="thumb ${up ? "up" : "down"}" viewBox="0 0 24 24" width="12" height="12" aria-hidden="true"><path d="${path}"/></svg>`;
  }

  function buildSessionTree(sessions) {
    if (!sessions.length) return `<div class="tree-empty">No sessions yet</div>`;
    const attemptsOf = (list) => {
      const ids = new Set(list.map((s) => s.id));
      return db.attempts.filter((a) => ids.has(a.sessionId));
    };
    const sorted = [...sessions].sort((a, b) => b.startTime - a.startTime);
    // Session number WITHIN its calendar day, restarting at #1 each day — same
    // as native perDaySessionNumbers() (Utils.kt, 2026-09-22) and the attempt
    // log above. A lifetime count showed "Session #130" on a 15-session day.
    const sessNum = new Map();
    const perDayCount = {};
    [...db.sessions].sort((a, b) => a.startTime - b.startTime).forEach((s) => {
      const dayKey = new Date(s.startTime).toDateString();
      perDayCount[dayKey] = (perDayCount[dayKey] || 0) + 1;
      sessNum.set(s.id, perDayCount[dayKey]);
    });
    const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    const years = new Map();
    sorted.forEach((s) => {
      const d = new Date(s.startTime);
      const y = d.getFullYear(), m = d.getMonth(), day = d.getDate();
      if (!years.has(y)) years.set(y, new Map());
      const months = years.get(y);
      if (!months.has(m)) months.set(m, new Map());
      const days = months.get(m);
      if (!days.has(day)) days.set(day, []);
      days.get(day).push(s);
    });
    // Thumb, play time and hit % on every level above the session (native
    // NodeSummary, Alex 2026-10-06). A day: thumb up if at least one quest was
    // taken (full or indulged), time = played that day, % over all its
    // attempts. Week/month/year: average over the days that were played;
    // thumb up if at least half of those days are up. null = nothing to judge
    // (only the session still in progress).
    const daySummary = (list) => {
      const at = attemptsOf(list);
      const thumb = list.some((s) => s.isQuestComplete) ? true
        : list.every((s) => !s.closed) ? null : false;
      return {
        playMs: list.reduce((t, s) => t + (s.activeTimeMs || 0), 0),
        winPct: at.length ? countTrueThreeTol(at) / at.length * 100 : 0,
        thumbUp: thumb
      };
    };
    const averageOfDays = (days) => {
      const n = Math.max(1, days.length);
      const judged = days.filter((d) => d.thumbUp !== null);
      return {
        playMs: days.reduce((t, d) => t + d.playMs, 0) / n,
        winPct: days.reduce((t, d) => t + d.winPct, 0) / n,
        thumbUp: judged.length ? judged.filter((d) => d.thumbUp).length * 2 >= judged.length : null
      };
    };
    const summarySub = (sum) => `${formatDuration(Math.round(sum.playMs))} · ${Math.round(sum.winPct)}%`;
    // Monday of the day's calendar week — groups days into weeks INSIDE their
    // month, like native SessionWeekNode (a week straddling two months is two nodes).
    const weekKeyOf = (y, m, day) => {
      const d = new Date(y, m, day);
      d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
      return d.getTime();
    };
    // Opened down to the month rows by default (Alex 2026-10-07): years are
    // unfolded once, on the first render that has data; what the player opens
    // or folds by hand then stays across re-renders and period changes.
    if (!treeExpanded && years.size) {
      treeExpanded = { years: new Set(years.keys()), months: new Set(), weeks: new Set(), days: new Set(), sessions: new Set() };
    }
    if (!treeExpanded) return "";
    const exp = treeExpanded;
    const caret = (open) => (open ? "▾" : "▸");
    let html = "";
    [...years.keys()].sort((a, b) => b - a).forEach((y) => {
      const months = years.get(y);
      // .flat()/.flatMap() need Safari 12+ — concat arrays manually to be safe
      // on the oldest engines too.
      const yearSessions = [];
      [...months.values()].forEach((d) => { [...d.values()].forEach((list) => { yearSessions.push(...list); }); });
      const yOpen = exp.years.has(y);
      const yDays = [];
      [...months.values()].forEach((d) => { [...d.values()].forEach((list) => { yDays.push(daySummary(list)); }); });
      const ySum = averageOfDays(yDays);
      html += treeNodeRow("year", 0, String(y), finger(ySum.thumbUp), attemptsOf(yearSessions), summarySub(ySum), caret(yOpen), `data-tyear="${y}"`);
      if (!yOpen) return;
      [...months.keys()].sort((a, b) => b - a).forEach((m) => {
        const days = months.get(m);
        const monthSessions = [];
        [...days.values()].forEach((list) => { monthSessions.push(...list); });
        const mKey = `${y}-${m}`;
        const mOpen = exp.months.has(mKey);
        const mSum = averageOfDays([...days.values()].map(daySummary));
        html += treeNodeRow("month", 14, monthNames[m], finger(mSum.thumbUp), attemptsOf(monthSessions), summarySub(mSum), caret(mOpen), `data-tmonth="${mKey}"`);
        if (!mOpen) return;
        const weeks = new Map();
        [...days.keys()].forEach((day) => {
          const wk = weekKeyOf(y, m, day);
          if (!weeks.has(wk)) weeks.set(wk, []);
          weeks.get(wk).push(day);
        });
        [...weeks.keys()].sort((a, b) => b - a).forEach((wk) => {
          const wDays = weeks.get(wk).sort((a, b) => b - a);
          const wSessions = [];
          wDays.forEach((day) => { wSessions.push(...days.get(day)); });
          const minDay = wDays[wDays.length - 1], maxDay = wDays[0];
          const wKey = `${mKey}-${wk}`;
          const wOpen = exp.weeks.has(wKey);
          const wSum = averageOfDays(wDays.map((day) => daySummary(days.get(day))));
          html += treeNodeRow("week", 21, minDay === maxDay ? `${minDay}` : `${minDay}–${maxDay}`, finger(wSum.thumbUp), attemptsOf(wSessions), summarySub(wSum), caret(wOpen), `data-tweek="${wKey}"`);
          if (!wOpen) return;
        wDays.forEach((day) => {
          const list = days.get(day);
          const d = new Date(list[0].startTime);
          const dow = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][d.getDay()];
          const dKey = `${y}-${m}-${day}`;
          const dOpen = exp.days.has(dKey);
          const dSum = daySummary(list);
          html += treeNodeRow("day", 28, `${dow} ${day}`, finger(dSum.thumbUp), attemptsOf(list), summarySub(dSum), caret(dOpen), `data-tday="${dKey}"`);
          if (!dOpen) return;
          list.sort((a, b) => b.startTime - a.startTime).forEach((s) => {
            const at = attemptsBySessionId(s.id);
            const time = new Date(s.startTime).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
            const wins = countTrueThreeTol(at);
            const hitPct = at.length ? Math.round(wins / at.length * 100) : 0;
            const sub = `${formatDuration(s.activeTimeMs || 0)} · ${hitPct}%${s.isQuestComplete ? " ✓" : ""} · LVL ${s.questLevelId} · task ${s.questTaskId}`;
            const sOpen = exp.sessions.has(s.id);
            html += treeNodeRow("session", 42, `Session #${sessNum.get(s.id) || "?"} · ${time}`, finger(s.isQuestComplete), at, sub, caret(sOpen), `data-tsession="${s.id}"`);
            if (sOpen && at.length) html += buildTreeAttemptRows(at);
          });
        });
        });
      });
    });
    return html;
  }

  // White tick marks on the range strip at the mode (most-common deviation)
  // positions — 0 sits at the centre (50%), edges at ±max(|P3|,|P97|).
  function modeTicks(data) {
    const halfSpan = Math.max(1, Math.abs(data.rangeEarlyMs), Math.abs(data.rangeLateMs));
    const tick = (list, side) => {
      if (!list || !list.length) return "";
      const off = Math.abs(list[0].errorMs) / (2 * halfSpan);
      const frac = Math.max(0.02, Math.min(0.98, side === "early" ? 0.5 - off : 0.5 + off));
      return `<span class="rd-mode-tick ${side}" style="left:${(frac * 100).toFixed(1)}%"></span>`;
    };
    return tick(data.topEarly, "early") + tick(data.topLate, "late");
  }

  function modeStr(list, accuracy) {
    if (!list || !list.length) return "—";
    const digits = accuracy === ACCURACY.MILLISECONDS ? 3 : accuracy === ACCURACY.CENTISECONDS ? 2 : accuracy === ACCURACY.DECISECONDS ? 1 : 0;
    const m = list[0];
    return `${(Math.abs(m.errorMs) / 1000).toFixed(digits)} × ${formatCount(m.count)}`;
  }

  function formatCount(n) {
    return n >= 1000 ? String(n).replace(/\B(?=(\d{3})+(?!\d))/g, " ") : String(n);
  }

  function rangeDeviationCard(data, accuracy) {
    const total = Math.max(1, data.earlyCount + data.periodWins + data.lateCount);
    const earlyPct = data.earlyCount / total * 100;
    const winPct = Math.max(4, data.periodWins / total * 100);
    const latePct = data.lateCount / total * 100;
    return `
        <div class="rd-caption">attempts ratio</div>
        <div class="rd-ratio">
          <span class="seg early" style="flex:${earlyPct}">${data.earlyCount ? formatCount(data.earlyCount) : ""}</span>
          <span class="seg win" style="flex:${winPct}">${data.periodWins ? formatCount(data.periodWins) : ""}</span>
          <span class="seg late" style="flex:${latePct}">${data.lateCount ? formatCount(data.lateCount) : ""}</span>
        </div>
        <div class="rd-caption">range of deviation</div>
        <div class="rd-strip">
          <span class="rd-serenity"></span>
          ${modeTicks(data)}
          <span class="rd-p3">${formatMs(data.rangeEarlyMs, true)}</span>
          <span class="rd-p97">${formatMs(data.rangeLateMs, true)}</span>
        </div>
        <div class="rd-modes"><span class="muted-cell">Top-frequency error</span>
          <span class="early">${modeStr(data.topEarly, accuracy)}</span> ·
          <span class="late">${modeStr(data.topLate, accuracy)}</span>
        </div>
        <p class="formula">The range of your results, 97% on both sides: the extreme 3% is cut off at each end.</p>`;
  }

  // Native "÷ SPREAD" factor = geometric standard deviation = 1 + CV%/100.
  function spreadFactor(stdPct) {
    return (1 + (stdPct || 0) / 100).toFixed(1);
  }

  // Native peakScmForAccuracy (AnalyticsUseCase.kt): max possible SC at a given
  // display accuracy (denominator=1, zero error) — used to normalize raw SC into
  // an efficiency 0..1 comparable across levels (see bug #2 below).
  function peakScmForAccuracy(accuracy) {
    return 99 * Math.pow(10, accuracy - 3);
  }

  function pad2(n) { return String(n).padStart(2, "0"); }
  function hourKey(t) { const d = new Date(t); return `${d.getFullYear()}${pad2(d.getMonth() + 1)}${pad2(d.getDate())}${pad2(d.getHours())}`; }
  function dayKey(t) { const d = new Date(t); return `${d.getFullYear()}${pad2(d.getMonth() + 1)}${pad2(d.getDate())}`; }
  function monthKey(t) { const d = new Date(t); return `${d.getFullYear()}${pad2(d.getMonth() + 1)}`; }
  function stepHour(d) { const nd = new Date(d); nd.setHours(nd.getHours() + 1); return nd; }
  function stepDay(d) { const nd = new Date(d); nd.setDate(nd.getDate() + 1); return nd; }
  function stepMonth(d) { const nd = new Date(d); nd.setMonth(nd.getMonth() + 1); return nd; }
  function timeLabel(t) { const d = new Date(t); return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`; }
  function hourLabel(t) { return `${pad2(new Date(t).getHours())}:00`; }
  function dayLabel(t) { return new Date(t).toLocaleDateString(undefined, { day: "numeric", month: "short" }); }
  function monthLabel(t) { return new Date(t).toLocaleDateString(undefined, { month: "short" }); }

  // Calendar-stepped bucket grid from the first to the last attempt (ported
  // from native's local bucket() fn, Alex 2026-08-14 bug #4): a bucket with no
  // attempts gets v:null instead of being silently skipped or treated as 0,
  // so the chart can break its line across real gaps instead of pretending
  // progress was continuous. Bucket value = mean EFFICIENCY (scm / peak for
  // that attempt's accuracy) over only the attempts in the bucket that have a
  // computed SC (scm > 0) — matches native bug #1 + bug #2 fixes.
  // Bucket values for the chart's metric dropdown (Alex 2026-10-07). Pace =
  // mean efficiency; Hit rate = % of wins with tolerance (as on Chance of Win);
  // Lucky = the headline Lucky formula on the bucket. < 2 attempts = no point.
  function paceValue(list) {
    const scored = list.filter((a) => a.scm > 0).map((a) => a.scm / peakScmForAccuracy(a.accuracy));
    return scored.length ? scored.reduce((s, x) => s + x, 0) / scored.length : null;
  }
  function hitValue(list) {
    return list.length >= 2 ? countTrueThreeTol(list) / list.length * 100 : null;
  }
  function luckyValue(list) {
    if (list.length < 2) return null;
    const errs = list.map(signedErrorMs).sort((a, b) => a - b);
    const at = (q) => errs[Math.min(errs.length - 1, Math.max(0, Math.trunc(errs.length * q)))];
    const slots = Math.max(1, Math.trunc(Math.abs(at(0.03)) + Math.abs(at(0.97)))) + 1;
    return errs.filter((e) => Math.abs(e) < 0.5).length / errs.length * slots;
  }
  const PACE_METRICS = {
    pace: { title: "Pace of learning", legend: "efficiency", value: paceValue, fmt: (v) => (v * 100).toFixed(1) + "%" },
    hit: { title: "Hit rate", legend: "hit rate", value: hitValue, fmt: (v) => Math.round(v) + "%" },
    lucky: { title: "Lucky index", legend: "lucky", value: luckyValue, fmt: (v) => v.toFixed(2) }
  };

  function buildBuckets(ordered, keyFn, labelFn, stepFn, maxN, valueFn = paceValue) {
    const groups = new Map();
    ordered.forEach((a) => {
      const key = keyFn(a.startTime);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(a);
    });
    const seq = [];
    let cur = new Date(ordered[0].startTime);
    const endKey = keyFn(ordered[ordered.length - 1].startTime);
    let guard = 0;
    while (guard++ < 5000) {
      const key = keyFn(cur.getTime());
      if (!seq.length || seq[seq.length - 1].key !== key) seq.push({ key, date: new Date(cur) });
      if (key === endKey) break;
      cur = stepFn(cur);
    }
    if (seq[seq.length - 1].key !== endKey) seq.push({ key: endKey, date: new Date(ordered[ordered.length - 1].startTime) });
    const trimmed = seq.slice(-maxN);
    return trimmed.map(({ key, date }) => {
      const bucketAttempts = groups.get(key);
      if (bucketAttempts && bucketAttempts.length) {
        const v = valueFn(bucketAttempts);
        return { t: bucketAttempts[0].startTime, v, label: labelFn(bucketAttempts[0].startTime) };
      }
      return { t: null, v: null, label: labelFn(date.getTime()) };
    });
  }

  // PACE OF LEARNING series — ported from native's Pace of Learning chart prep
  // (AnalyticsUseCase.kt, Alex 2026-08-13/14, 4 bugs fixed): granularity adapts
  // to the selected period, and to the ALL_TIME history's actual span (bug #3)
  // instead of being hardcoded to months.
  function paceLearningSeries(attempts, period, metric = "pace") {
    if (!attempts.length) return [];
    const ordered = [...attempts].sort((a, b) => a.startTime - b.startTime);
    const valueFn = (PACE_METRICS[metric] || PACE_METRICS.pace).value;
    if (period === "SESSION") {
      // Raw points by attempt, not calendar buckets — sequential attempts in
      // one session have no real gaps, so no null-breaks are needed here.
      // Hit rate and Lucky of one attempt mean nothing (0 or 100%), so they
      // use a sliding window of the last 10 attempts up to this one.
      const scored = [];
      ordered.forEach((a, idx) => {
        if (!(a.scm > 0)) return;
        const v = metric === "pace" ? a.scm / peakScmForAccuracy(a.accuracy)
          : valueFn(ordered.slice(Math.max(0, idx - 9), idx + 1));
        scored.push({ t: a.startTime, v, label: `#${idx + 1}` });
      });
      return scored.slice(-20);
    }
    if (period === "MONTH") return buildBuckets(ordered, dayKey, dayLabel, stepDay, 16, valueFn);
    // Day and week: points by groups of attempts, not by the calendar (Alex
    // 2026-10-07) -- hours of a day and days of a week are mostly empty, so
    // the line fell apart into 2-3 pieces. Week = one point per sitting (play
    // with no break over 10 min), the last 24; day = equal blocks of attempts
    // in order, up to 16 points, at least 10 attempts each.
    const fromGroups = (groups, labelFn) => groups.map((g) => ({
      t: g[0].startTime,
      v: metric === "pace" ? (g.filter((a) => a.scm > 0).length >= 2 ? valueFn(g) : null) : valueFn(g),
      label: labelFn(g[0].startTime)
    }));
    if (period === "WEEK") {
      const sittings = [];
      ordered.forEach((a) => {
        const cur = sittings[sittings.length - 1];
        if (!cur || a.startTime - cur[cur.length - 1].startTime > 600000) sittings.push([a]);
        else cur.push(a);
      });
      return fromGroups(sittings.slice(-24), (t) => new Date(t).toLocaleDateString(undefined, { weekday: "short" }) + "\n" + timeLabel(t));
    }
    if (period === "TODAY") {
      const size = Math.max(10, Math.ceil(ordered.length / 16));
      const blocks = [];
      for (let i = 0; i < ordered.length; i += size) blocks.push(ordered.slice(i, i + size));
      return fromGroups(blocks, timeLabel);
    }
    // ALL_TIME: granularity picked from the real date span of the history.
    const spanDays = (ordered[ordered.length - 1].startTime - ordered[0].startTime) / 86400000;
    if (spanDays <= 1.5) return buildBuckets(ordered, hourKey, hourLabel, stepHour, 12, valueFn);
    if (spanDays <= 45.0) return buildBuckets(ordered, dayKey, dayLabel, stepDay, 16, valueFn);
    return buildBuckets(ordered, monthKey, monthLabel, stepMonth, 8, valueFn);
  }

  // Bars from zero with the date/time under them (Alex 2026-10-07: «сделай
  // столбиковой диаграммой с указанием даты или времени»). A null point keeps
  // its slot and label, just with no bar.
  function paceChartSvg(series, metric = "pace") {
    const m = PACE_METRICS[metric] || PACE_METRICS.pace;
    const withVal = series.filter((s) => s.v !== null && s.v !== undefined);
    if (series.length < 2 || withVal.length < 1) return "";
    const W = 320, padL = 4, axisW = 30, padR = 4, padT = 12, plotH = 64;
    const twoLine = series.some((s) => String(s.label).includes("\n"));
    const H = padT + plotH + (twoLine ? 24 : 14);
    const plotL = padL + axisW;
    const max = Math.max(...withVal.map((s) => s.v), 1e-6);
    const slot = (W - plotL - padR) / series.length;
    const barW = slot * 0.62;
    const base = padT + plotH;
    const step = series.length <= 8 ? 1 : series.length <= 16 ? 2 : 3;
    let lastReal = -1;
    series.forEach((s, i) => { if (s.v !== null && s.v !== undefined) lastReal = i; });
    const bars = series.map((s, i) => {
      if (s.v === null || s.v === undefined) return "";
      const h = Math.max(1, s.v / max * plotH);
      const x = plotL + slot * i + (slot - barW) / 2;
      return `<rect class="pc-bar${i === lastReal ? " pc-bar-last" : ""}" x="${x.toFixed(1)}" y="${(base - h).toFixed(1)}" width="${barW.toFixed(1)}" height="${h.toFixed(1)}" rx="1.5"/>`;
    }).join("");
    // Value over a bar only where its label fits (every step-th bar).
    const values = series.map((s, i) => {
      if (s.v === null || s.v === undefined || i % step) return "";
      const cx = plotL + slot * (i + 0.5);
      const y = base - Math.max(1, s.v / max * plotH) - 3;
      return `<text class="pc-point" x="${cx.toFixed(1)}" y="${Math.max(padT - 2, y).toFixed(1)}" text-anchor="middle">${m.fmt(s.v)}</text>`;
    }).join("");
    const labels = series.map((s, i) => {
      if (i % step) return "";
      const cx = (plotL + slot * (i + 0.5)).toFixed(1);
      const lines = String(s.label).split("\n");
      return `<text class="pc-x" x="${cx}" y="${base + 10}" text-anchor="middle">${lines.map((l, k) => `<tspan x="${cx}" dy="${k ? 10 : 0}">${escapeHtml(l)}</tspan>`).join("")}</text>`;
    }).join("");
    return `<svg class="pace-chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="${m.title} over time">
      <text class="pc-y" x="${padL}" y="${padT + 3}">${m.fmt(max)}</text>
      <text class="pc-y" x="${padL}" y="${base}">${m.fmt(0)}</text>
      <line class="pc-axis" x1="${plotL}" y1="${base}" x2="${W - padR}" y2="${base}"/>
      ${bars}
      <g class="pc-points">${values}</g>
      ${labels}
    </svg>
    <div class="pc-legend"><span class="pc-dot pc-dot-sq"></span> ${m.legend}</div>`;
  }

  // Native ReportFaceCard.kt port (Alex 2026-09-21, moved from Ranks to
  // Analytics; web port 2026-09-25). Native gates on a WebView JS bridge
  // (PotFace.ready) reporting how many attempts report.html found server-side;
  // web has no such bridge for a plain <iframe>; we already know the period's
  // attempt count locally (same source report.html would compute from), so
  // the gate uses that directly instead -- equivalent end state, no bridge
  // needed.
  const ANALYTICS_MIN_FACE_ATTEMPTS = 30;

  function analyticsReportCard(data) {
    const minAtt = ANALYTICS_MIN_FACE_ATTEMPTS;
    const count = data.attempts.length;
    const enough = count >= minAtt;
    const missing = Math.max(0, minAtt - count);
    const faceUrl = identity.playerId
      ? `https://playoftime.online/report.html?playerId=${encodeURIComponent(identity.playerId)}&token=${encodeURIComponent(identity.syncToken)}&mode=face&fromMs=${data.periodFromMs}&toMs=${data.periodToMs}&min=${minAtt}`
      : "";
    const label = enough
      ? "VIEW MY PROGRESS REPORT"
      : `Please make ${missing} more ${missing === 1 ? "attempt" : "attempts"} to see the report`;
    return `
      <div class="sh-report-card${enough ? " sh-report-active" : ""}" id="analyticsReportCard">
        <div class="sh-report-face">${faceUrl ? `<iframe src="${faceUrl}"></iframe>` : ""}</div>
        <div class="sh-report-label${enough ? "" : " sh-report-hint"}">${label}</div>
      </div>`;
  }

  // Measurement version (Alex 2026-10-14): Analytics shows ONE session —
  // the one picked in the drop-down next to "Session" (date · session number
  // · start time), the newest by default. Numbers: hits, hit rate, attempts
  // per win, pace and the three geometric means. No period filters.
  let analyticsSessionId = null; // null = the newest session with attempts

  function analyticsSessionsList() {
    const withAttempts = new Set(db.attempts.map((a) => a.sessionId));
    const ordered = db.sessions.filter((x) => withAttempts.has(x.id)).sort((a, b) => a.startTime - b.startTime);
    // Numbered within their calendar day, as in the attempt log.
    const perDay = {};
    const list = ordered.map((x) => {
      const day = new Date(x.startTime).toDateString();
      perDay[day] = (perDay[day] || 0) + 1;
      return { session: x, number: perDay[day] };
    });
    return list.reverse(); // newest first
  }

  function analyticsSessionLabel(item) {
    const d = new Date(item.session.startTime);
    const pad = (n) => String(n).padStart(2, "0");
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} · #${item.number} · ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function renderAnalytics() {
    const list = analyticsSessionsList();
    let picked = list.find((it) => it.session.id === analyticsSessionId) || list[0] || null;
    const attempts = picked ? attemptsBySessionId(picked.session.id) : [];
    const has = attempts.length > 0;
    const wins = attempts.filter(isQuestWinAttempt).length;
    const playMin = picked ? (picked.session.activeTimeMs || 0) / 60000 : 0;
    // Early / Late over misses only, the same rule as the Ranks averages:
    // a hit is not an early or late stop even if its raw ms is a little off.
    // Absolute stays over all attempts (overall lean).
    const missErrs = attempts.filter((a) => !isQuestWinAttempt(a)).map(signedErrorMs);
    const geomEarly = geometricMean(missErrs.filter((e) => e < 0).map((e) => -e));
    const geomLate = geometricMean(missErrs.filter((e) => e > 0));
    const geomAbs = has ? geometricMean(attempts.map((a) => a.endTime - a.startTime)) - AIM_TIME : 0;
    const picker = list.length
      ? `<select id="analyticsSession" class="session-select" aria-label="Session">${list.map((it) =>
          `<option value="${it.session.id}"${picked && it.session.id === picked.session.id ? " selected" : ""}>${analyticsSessionLabel(it)}</option>`).join("")}</select>`
      : `<span class="session-none">no sessions yet</span>`;
    app.innerHTML = `
      <section class="analytics-screen measure-analytics">
        <div class="sticky"><div class="analytics-pill session-pill"><span class="session-tag">Session</span>${picker}</div></div>
        <div class="analytics-content">
          ${statBlock("Hits", "", `
            ${statRow("Hits", `<span class="win">${formatCount(wins)}</span> of ${formatCount(attempts.length)}`)}
            ${statRow("Hit rate", has ? (wins / attempts.length * 100).toFixed(1) + "%" : "—")}
            ${statRow("Attempts per win", wins ? (attempts.length / wins).toFixed(1) : "—")}
            ${statRow("Pace", playMin > 0 && has ? (attempts.length / playMin).toFixed(1) + " att/min" : "—")}`)}
          ${statBlock("Geometric mean", "", `
            ${statRow("Early", geomEarly ? formatMs(-geomEarly, true) : "—", "", "early")}
            ${statRow("Absolute", has ? formatMs(geomAbs, true) : "—")}
            ${statRow("Late", geomLate ? formatMs(geomLate, true) : "—", "", "late")}`)}
        </div>
        ${nav()}
      </section>`;
    const sel = document.getElementById("analyticsSession");
    if (sel) sel.addEventListener("change", () => { analyticsSessionId = Number(sel.value); render(); });
    bindNav();
  }

  function renderCompletion() {
    const data = db.completionOverlay;
    app.innerHTML = `
      <section class="completion-screen">
        <div class="completion-content">
          <div class="party-icon"><span></span><i></i><b></b></div>
          <h1>Level ${data.levelId} · Quest ${data.taskId} — Complete!</h1>
          <p>You spent ${formatDuration(data.playTimeMs)} of pure play time on this quest.</p>
          <p><strong>You earned ${formatComma(data.earnedScm)} Serenity Coins.</strong></p>
          <div class="completion-card">
            <div><span>Early errors</span><b>${data.earlyMs || 0} ms ${deltaBadge(data.earlyDeltaMs, true, "ms")}</b></div>
            <div><span>Late errors</span><b>${data.lateMs || 0} ms ${deltaBadge(data.lateDeltaMs, true, "ms")}</b></div>
            <div><span>Hit rate</span><b>${data.hitRate}% ${deltaBadge(data.hitRateDelta, false, "%")}</b></div>
          </div>
          <p class="completion-note">You are making notable progress. In your next quest, try to develop the approaches to managing your inner state that you discovered in this one.</p>
          <button id="takeNextQuest" class="take-next">TAKE NEXT QUEST</button>
        </div>
      </section>
    `;
    document.getElementById("takeNextQuest") && document.getElementById("takeNextQuest").addEventListener("click", () => {
      db.completionOverlay = null;
      timer.state = "ready";
      timer.elapsed = 0;
      timer.resultType = "none";
      timer.pendingMessage = "";
      db.lastMessage = "";
      saveDb();
      render();
    });
  }

  function deltaBadge(delta, lowerIsBetter, unit) {
    if (!Number.isFinite(delta) || delta === 0) return `<em>→ 0 ${unit}</em>`;
    const improved = lowerIsBetter ? delta > 0 : delta > 0;
    const arrow = lowerIsBetter ? (improved ? "↓" : "↑") : (improved ? "↑" : "↓");
    const cls = improved ? "win" : "early";
    return `<em class="${cls}">${arrow} ${Math.abs(delta)} ${unit}</em>`;
  }

  // ── Leaderboard ────────────────────────────────────────────────────────
  const LB_SORTS = [["level", "Progress"], ["sc", "Coins"], ["hitrate", "Accuracy"], ["combined", "Luck"]];

  // ── Measurement leaderboard (shared, on the server) ──────────────────────
  // Identity as in the game: a random playerId + secret syncToken made once
  // on this device (loadIdentity). The first sync registers the player and
  // the server names them "Player 001", "Player 002", … by join order.
  // One row per player: their latest complete measurement at the selected
  // level, fewest attempts per win on top. Four numbers per row.
  let lbLevel = null;      // level shown; null = the current level
  let mlbData = null;      // rows for mlbDataLevel, from api/leaderboard.php
  let mlbDataLevel = null;
  let mlbLoading = false;
  let mlbError = false;
  let mSyncInFlight = false;

  function fmtAvgMs(v) { return v == null ? "—" : Math.round(v) + " ms"; }
  function fmtPerMin(v) { return v == null ? "—" : v.toFixed(1); }
  function fmtPerWin(v) { return v == null ? "—" : v.toFixed(1); }

  // Sends every measurement the server has not seen yet. Best effort: if
  // the network is down it just tries again after the next measurement or
  // on the next visit; the measurement itself is already saved locally.
  function syncMeasurements() {
    if (mSyncInFlight || !identity.playerId) return;
    const lastId = identity.lastSyncedMeasurementId || 0;
    const pending = db.measurements.filter((m) => m.id > lastId);
    if (!pending.length && identity.displayName) return;
    mSyncInFlight = true;
    fetch(API_BASE + "sync.php", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerId: identity.playerId, syncToken: identity.syncToken, measurements: pending })
    })
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(String(res.status)))))
      .then((data) => {
        mSyncInFlight = false;
        if (data && data.displayName) identity.displayName = data.displayName;
        if (pending.length) identity.lastSyncedMeasurementId = Math.max(...pending.map((m) => m.id));
        saveIdentity();
        if (view === "leaderboard") { mlbData = null; renderPreservingFocus(); }
      })
      .catch(() => { mSyncInFlight = false; });
  }

  function fetchMeasurementLeaderboard(level) {
    mlbLoading = true;
    mlbError = false;
    fetch(API_BASE + "leaderboard.php?level=" + level + "&playerId=" + encodeURIComponent(identity.playerId || ""), { cache: "no-store" })
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(String(res.status)))))
      .then((data) => {
        mlbData = Array.isArray(data.rows) ? data.rows : [];
        mlbDataLevel = level;
        mlbLoading = false;
        if (view === "leaderboard") renderPreservingFocus();
      })
      .catch(() => {
        mlbLoading = false;
        mlbError = true;
        mlbDataLevel = level;
        if (view === "leaderboard") renderPreservingFocus();
      });
  }

  // Own name (Alex 2026-10-14: «возможность сохранить себя под новым
  // именем»). Same as the game's Ranks: a field and Save. An empty field
  // gives back the player's own Player NNN.
  let mlbNameStatus = "";
  let mlbNameDraft = null; // typed name kept on screen until it is saved
  function saveMeasurementName() {
    const input = document.getElementById("lbNameInput");
    const name = (input.value || "").trim().slice(0, 24);
    mlbNameDraft = name;
    mlbNameStatus = "Saving…";
    render();
    fetch(API_BASE + "set_name.php", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerId: identity.playerId, syncToken: identity.syncToken, name })
    })
      .then((res) => res.json().catch(() => ({})).then((data) => (res.ok ? data : Promise.reject(new Error(res.status + " " + (data.error || ""))))))
      .then((data) => {
        if (data && data.displayName) identity.displayName = data.displayName;
        saveIdentity();
        mlbNameDraft = null;
        mlbNameStatus = "Saved.";
        mlbData = null;
        renderPreservingFocus();
      })
      // The reason is shown as is, so a failure on a real device can be told
      // apart from a dropped connection (Alex 2026-10-14: one instance could
      // not save a name).
      .catch((err) => {
        const why = err && err.message ? err.message.trim() : "";
        mlbNameStatus = why && !/fetch|network|load/i.test(why)
          ? "Could not save (" + why + "). Try again."
          : "Could not save. Check the connection and try again.";
        renderPreservingFocus();
      });
  }

  function renderLeaderboard() {
    const level = lbLevel || db.settings.currentLevelId || 1;
    if (mlbDataLevel !== level || (mlbData === null && !mlbError)) {
      if (!mlbLoading) fetchMeasurementLeaderboard(level);
    }
    const rows = mlbDataLevel === level ? mlbData : null;
    // Not in the table yet → say why in plain words (Alex 2026-10-14: a saved
    // name showed nowhere, and the reason was not obvious).
    const notInTable = Boolean(rows && !rows.some((r) => r.you));
    let body;
    if (mlbError && mlbDataLevel === level) body = `<div class="android-empty">Can't reach the leaderboard. Check the connection and open Ranks again.</div>`;
    else if (!rows) body = `<div class="android-empty">Loading…</div>`;
    else if (!rows.length) body = `<div class="android-empty">No measurements at this level yet. Finish ${MEASUREMENT_ATTEMPTS} attempts to see the first row.</div>`;
    else body = rows.map((r, i) => `
          <div class="mlb-row${r.you ? " mlb-you" : ""}">
            <span class="mlb-name"><span class="mlb-rank">${i + 1}</span><span class="mlb-nm">${escapeHtml(r.name || "—")}</span>${r.you ? `<span class="mlb-you-tag">(you)</span>` : ""}</span>
            <b class="late">${fmtAvgMs(r.lateAvgMs)}</b>
            <b class="early">${fmtAvgMs(r.earlyAvgMs)}</b>
            <b>${fmtPerMin(r.perMinute)}</b>
            <b>${fmtPerWin(r.perWin)}</b>
          </div>`).join("");
    app.innerHTML = `
      <section class="settings-screen leaderboard-screen measure-lb">
        <div class="sticky"><h1 class="section-title">Ranks</h1></div>
        <div class="settings-content">
          <div class="panel mlb-name-panel">
            <div class="lb-name-row">
              <input id="lbNameInput" type="text" maxlength="24" placeholder="Your name" value="${escapeHtml(mlbNameDraft != null ? mlbNameDraft : (identity.displayName || ""))}">
              <button id="lbNameSave" class="chip active" type="button">Save</button>
            </div>
            <p class="formula">${escapeHtml(mlbNameStatus || "Your name in the table. Leave it empty to go back to your Player number.")}</p>
          </div>
          <div class="chip-row level-chips">${[1, 2, 3].map((id) =>
            `<button class="chip ${level === id ? "active" : ""}" data-lb-level="${id}">${formatTime(AIM_TIME, currentQuestFrom(id).accuracy)}</button>`).join("")}</div>
          ${notInTable ? `<p class="mlb-notice">You are not in the table yet. Complete one session of ${MEASUREMENT_ATTEMPTS} attempts at ${formatTime(AIM_TIME, currentQuestFrom(level).accuracy)} first.</p>` : ""}
          <div class="panel mlb-table">
            <div class="mlb-row mlb-head">
              <span>Player</span>
              <span>Late avg</span>
              <span>Early avg</span>
              <span>Att/min</span>
              <span>Att/win</span>
            </div>
            ${body}
          </div>
          <p class="formula">Each player's latest measurement of ${MEASUREMENT_ATTEMPTS} attempts at this level. Fewest attempts per win on top.</p>
        </div>
        ${nav()}
      </section>`;
    document.querySelectorAll("[data-lb-level]").forEach((btn) => {
      btn.addEventListener("click", () => { lbLevel = Number(btn.dataset.lbLevel); render(); });
    });
    const saveBtn = document.getElementById("lbNameSave");
    if (saveBtn) saveBtn.addEventListener("click", saveMeasurementName);
    const nameInput = document.getElementById("lbNameInput");
    if (nameInput) nameInput.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); saveMeasurementName(); } });
    bindNav();
  }

  // "Measurement complete" card: the same four numbers, tap anywhere to close;
  // the main screen is back to its initial state (empty pips, fresh targets).
  function measurementDoneOverlay() {
    const m = measurementDone;
    return `
      <div class="overlay">
        <div class="dialog session-summary measure-done" id="measurementDoneCard">
          <div class="ss2-head">
            <div class="ss2-headline">Measurement complete</div>
            <span class="ss2-close">\u00d7</span>
          </div>
          <div class="ss2-table">
            ${ssSingleRow("Level", levelPrecisionName(m.levelId))}
            ${ssSingleRow("Late, average", fmtAvgMs(m.lateAvgMs))}
            ${ssSingleRow("Early, average", fmtAvgMs(m.earlyAvgMs))}
            ${ssSingleRow("Attempts per minute", fmtPerMin(m.perMinute))}
            ${ssSingleRow("Attempts per win", fmtPerWin(m.perWin))}
          </div>
          <div class="ss2-hint">Saved to Ranks. Tap anywhere to start a new measurement</div>
        </div>
      </div>`;
  }

  function exitAppFullscreen() {
    if (!document.fullscreenElement && !document.webkitFullscreenElement) return;
    suppressFullscreenClose = true;
    // Safety net: if fullscreenchange never fires for some reason, don't
    // leave this armed forever and swallow a real future close.
    setTimeout(() => { suppressFullscreenClose = false; }, 1500);
    const exit = document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen;
    if (exit) { try { exit.call(document).catch(() => { suppressFullscreenClose = false; }); } catch (_) { suppressFullscreenClose = false; } }
  }

  // window.confirm()/alert()/prompt() force mobile browsers to silently drop
  // out of the Fullscreen API for the dialog's duration (a security policy,
  // not something we trigger) -- same effect as exitAppFullscreen() above,
  // just browser-initiated instead of ours. Call this around any such dialog
  // so onFsChange() doesn't treat it as "player left via a browser gesture"
  // and close the app out from under them (Alex, 2026-09-04: erasing data
  // was kicking him out of the webapp entirely). Re-enters fullscreen on the
  // same element bindHostPage's enterFullscreen() targets.
  function guardFullscreenAcrossDialog(showDialog) {
    const wasFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement);
    if (wasFullscreen) {
      suppressFullscreenClose = true;
      setTimeout(() => { suppressFullscreenClose = false; }, 1500);
    }
    const result = showDialog();
    if (wasFullscreen) {
      const el = document.getElementById("gameOverlay");
      const req = el && (el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen);
      if (req) { try { req.call(el).catch(() => {}); } catch (_) {} }
    }
    return result;
  }

  // A background fetch resolving mid-render() would otherwise blow away the
  // name field's DOM node — and whatever the player was mid-typing into it —
  // since render() replaces #app's innerHTML wholesale. Used by any render()
  // call that an async callback (fetch, timer) might trigger while the
  // player could plausibly still be typing.
  function renderPreservingFocus() {
    const active = document.activeElement;
    const editingName = active && active.id === "lbNameInput";
    const savedValue = editingName ? active.value : null;
    const savedStart = editingName ? active.selectionStart : null;
    const savedEnd = editingName ? active.selectionEnd : null;
    render();
    if (!editingName) return;
    const fresh = document.getElementById("lbNameInput");
    if (!fresh) return;
    fresh.value = savedValue;
    fresh.focus();
    try { fresh.setSelectionRange(savedStart, savedEnd); } catch (_) { /* ignore */ }
  }

  const BPS_CHOICES = [1, 2, 4, 8, 16];

  function renderSettings() {
    const level = db.settings.currentLevelId || 1;
    const beatsOn = Boolean(db.settings.soundOn || db.settings.vibrationOn);
    const bps = db.settings.beatsPerSecond;
    app.innerHTML = `
      <section class="settings-screen">
        <div class="sticky"><h1 class="section-title">Settings</h1></div>
        <div class="settings-content">
          <h2 class="settings-heading">Level</h2>
          <div class="panel">
            <div class="chip-row level-chips">${[1, 2, 3].map((id) =>
              `<button class="chip ${level === id ? "active" : ""}" data-level="${id}">${formatTime(AIM_TIME, currentQuestFrom(id).accuracy)}</button>`).join("")}</div>
            <p class="formula">A hit is exactly ${formatTime(AIM_TIME, currentQuestFrom(level).accuracy)} on the stopwatch. One measurement is ${MEASUREMENT_ATTEMPTS} attempts; changing the level starts a new one.</p>
          </div>
          <div class="panel">
            <div class="switch-row"><span>Sound</span><input id="soundOn" type="checkbox" ${db.settings.soundOn ? "checked" : ""}></div>
            <div class="switch-row"><span>Vibration</span><input id="vibrationOn" type="checkbox" ${db.settings.vibrationOn ? "checked" : ""}></div>
            ${beatsOn
              ? `<div class="switch-row"><span>Beats per second</span><span class="chip-row bps-chips">${BPS_CHOICES.map((v) => `<button class="chip ${bps === v ? "active" : ""}" data-bps="${v}">${v}</button>`).join("")}</span></div>`
              : `<div class="switch-row locked-row"><span>🔒 Beats per second<br><small>(turn on Sound or Vibration)</small></span><span class="locked-value">${bps}</span></div>`}
            <div class="switch-row"><span>Playing hand</span>
              <span class="hand-toggle">
                <button data-hand="LEFT" class="${db.settings.selectedHand === "LEFT" ? "active" : ""}">Left</button>
                <button data-hand="RIGHT" class="${db.settings.selectedHand === "RIGHT" ? "active" : ""}">Right</button>
              </span>
            </div>
            <p class="formula">Playing hand is saved with every attempt so your stats can compare hands — it does not change the gameplay itself.</p>
          </div>
          <div class="start-over-section">
            <button id="exportSettings" class="settings-row-card" type="button">
              <span class="settings-row-title export">Export attempt history</span>
              <span class="settings-row-sub">Share your full history as an Excel file</span>
            </button>
          </div>
        </div>
        ${nav()}
      </section>`;
    bindSettings();
    bindNav();
  }

  function exportRows() {
    const orderedSessions = [...db.sessions].sort((a, b) => a.startTime - b.startTime);
    const sessionNumbers = new Map(orderedSessions.map((session, idx) => [session.id, idx + 1]));
    const attemptNumbers = new Map();
    return [...db.attempts].sort((a, b) => a.startTime - b.startTime).map((attempt) => {
      const sessionNumber = sessionNumbers.get(attempt.sessionId) || attempt.sessionId;
      const attemptNumber = (attemptNumbers.get(attempt.sessionId) || 0) + 1;
      attemptNumbers.set(attempt.sessionId, attemptNumber);
      const durationMs = attempt.endTime - attempt.startTime;
      const errMs = signedErrorMs(attempt);
      return {
        date: new Date(attempt.startTime).toLocaleDateString("en-GB"),
        time: new Date(attempt.startTime).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
        sessionNumber,
        attemptNumber,
        attemptResult: formatTime(durationMs, attempt.accuracy),
        errorTime: formatSignedErrorForExport(errMs, attempt.accuracy),
        accuracy: accuracyAbbr(attempt.accuracy),
        hand: attempt.hand || "RIGHT",
        serenityCoin: attempt.scm ? attempt.scm.toFixed(6) : "",
        complete: (db.sessions.find((x) => x.id === attempt.sessionId) || {}).isQuestComplete ? "yes" : "no"
      };
    });
  }

  function accuracyAbbr(accuracy) {
    if (accuracy === ACCURACY.SECONDS) return "s";
    if (accuracy === ACCURACY.DECISECONDS) return "ds";
    if (accuracy === ACCURACY.CENTISECONDS) return "cs";
    return "ms";
  }

  function formatSignedErrorForExport(errMs, accuracy) {
    const value = errMs / 1000;
    const digits = accuracy === ACCURACY.MILLISECONDS ? 3 : accuracy === ACCURACY.CENTISECONDS ? 2 : accuracy === ACCURACY.DECISECONDS ? 1 : 0;
    return (value > 0 ? "+" : "") + value.toFixed(digits);
  }

  function downloadBlob(filename, type, body) {
    const blob = new Blob([body], { type });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function exportJson() {
    downloadBlob("play-of-time-progress.json", "application/json", JSON.stringify(db, null, 2));
  }

  function exportCsv() {
    const headers = ["date", "time", "sessionNumber", "attemptNumber", "attemptResult", "errorTime", "accuracy", "hand", "serenityCoin"];
    const lines = [headers.join(",")];
    exportRows().forEach((row) => {
      lines.push(headers.map((key) => `"${String(row[key]).replace(/"/g, '""')}"`).join(","));
    });
    downloadBlob("play-of-time-export.csv", "text/csv;charset=utf-8", lines.join("\n"));
  }

  // ── Minimal dependency-free XLSX writer (2026-08-23) ───────────────────
  // Was an HTML <table> disguised as .xls (Excel's old "sniff the content"
  // trick) — triggered format-mismatch warnings and didn't render cleanly
  // everywhere ("some garbage is being downloaded" per Alex). This builds a
  // real .xlsx: an uncompressed ("stored") ZIP of OOXML spreadsheet parts,
  // with numeric columns as real typed number cells (not text) — matching
  // native's ExcelShareUtil.kt (Apache POI) reference. No external library:
  // app.js stays a single self-contained file, and STORE (no compression)
  // needs nothing beyond CRC32, which is ~10 lines by hand.
  const CRC32_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
    return table;
  })();

  function crc32(bytes) {
    let crc = 0xffffffff;
    for (let i = 0; i < bytes.length; i++) crc = CRC32_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
    return (crc ^ 0xffffffff) >>> 0;
  }

  function dosDateTime(date) {
    const dosTime = ((date.getHours() & 0x1f) << 11) | ((date.getMinutes() & 0x3f) << 5) | ((date.getSeconds() >> 1) & 0x1f);
    const dosDate = (((date.getFullYear() - 1980) & 0x7f) << 9) | (((date.getMonth() + 1) & 0xf) << 5) | (date.getDate() & 0x1f);
    return { dosTime, dosDate };
  }

  // Builds an uncompressed ZIP archive (method 0 = "stored") — fully valid
  // per the ZIP/OOXML spec, just skips the DEFLATE step entirely.
  function zipStore(files) {
    const encoder = new TextEncoder();
    const { dosTime, dosDate } = dosDateTime(new Date());
    const localParts = [];
    const centralParts = [];
    let offset = 0;

    files.forEach(({ name, data }) => {
      const nameBytes = encoder.encode(name);
      const dataBytes = typeof data === "string" ? encoder.encode(data) : data;
      const crc = crc32(dataBytes);

      const local = new DataView(new ArrayBuffer(30));
      local.setUint32(0, 0x04034b50, true);
      local.setUint16(4, 20, true);
      local.setUint16(6, 0, true);
      local.setUint16(8, 0, true);
      local.setUint16(10, dosTime, true);
      local.setUint16(12, dosDate, true);
      local.setUint32(14, crc, true);
      local.setUint32(18, dataBytes.length, true);
      local.setUint32(22, dataBytes.length, true);
      local.setUint16(26, nameBytes.length, true);
      local.setUint16(28, 0, true);
      localParts.push(new Uint8Array(local.buffer), nameBytes, dataBytes);

      const central = new DataView(new ArrayBuffer(46));
      central.setUint32(0, 0x02014b50, true);
      central.setUint16(4, 20, true);
      central.setUint16(6, 20, true);
      central.setUint16(8, 0, true);
      central.setUint16(10, 0, true);
      central.setUint16(12, dosTime, true);
      central.setUint16(14, dosDate, true);
      central.setUint32(16, crc, true);
      central.setUint32(20, dataBytes.length, true);
      central.setUint32(24, dataBytes.length, true);
      central.setUint16(28, nameBytes.length, true);
      central.setUint16(30, 0, true);
      central.setUint16(32, 0, true);
      central.setUint16(34, 0, true);
      central.setUint16(36, 0, true);
      central.setUint32(38, 0, true);
      central.setUint32(42, offset, true);
      centralParts.push(new Uint8Array(central.buffer), nameBytes);

      offset += local.buffer.byteLength + nameBytes.length + dataBytes.length;
    });

    const centralStart = offset;
    centralParts.forEach((part) => { offset += part.length; });
    const centralSize = offset - centralStart;

    const end = new DataView(new ArrayBuffer(22));
    end.setUint32(0, 0x06054b50, true);
    end.setUint16(4, 0, true);
    end.setUint16(6, 0, true);
    end.setUint16(8, files.length, true);
    end.setUint16(10, files.length, true);
    end.setUint32(12, centralSize, true);
    end.setUint32(16, centralStart, true);
    end.setUint16(20, 0, true);

    const allParts = [...localParts, ...centralParts, new Uint8Array(end.buffer)];
    const result = new Uint8Array(allParts.reduce((sum, part) => sum + part.length, 0));
    let pos = 0;
    allParts.forEach((part) => { result.set(part, pos); pos += part.length; });
    return result;
  }

  function xlsxColLetter(index) {
    let n = index + 1;
    let letters = "";
    while (n > 0) {
      const rem = (n - 1) % 26;
      letters = String.fromCharCode(65 + rem) + letters;
      n = Math.floor((n - 1) / 26);
    }
    return letters;
  }

  function xlsxCell(ref, value, numeric) {
    if (value === "" || value === null || value === undefined) return `<c r="${ref}"/>`;
    if (numeric) {
      const num = Number(value);
      if (Number.isFinite(num)) return `<c r="${ref}"><v>${num}</v></c>`;
    }
    return `<c r="${ref}" t="inlineStr"><is><t>${escapeHtml(value)}</t></is></c>`;
  }

  function buildXlsxBytes(headers, rows, numericCols) {
    const headerRow = `<row r="1">${headers.map((h, i) => xlsxCell(`${xlsxColLetter(i)}1`, h, false)).join("")}</row>`;
    const dataRows = rows.map((row, r) => {
      const rowNum = r + 2;
      const cells = row.map((value, i) => xlsxCell(`${xlsxColLetter(i)}${rowNum}`, value, numericCols.has(i))).join("");
      return `<row r="${rowNum}">${cells}</row>`;
    }).join("");
    const dims = `A1:${xlsxColLetter(headers.length - 1)}${rows.length + 1}`;
    const sheetXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
      `<dimension ref="${dims}"/><sheetViews><sheetView workbookViewId="0"/></sheetViews>` +
      `<sheetFormatPr defaultRowHeight="15"/><sheetData>${headerRow}${dataRows}</sheetData></worksheet>`;

    const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">` +
      `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>` +
      `<Default Extension="xml" ContentType="application/xml"/>` +
      `<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>` +
      `<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>` +
      `<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>` +
      `</Types>`;
    const rootRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
      `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>` +
      `</Relationships>`;
    const workbookXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">` +
      `<sheets><sheet name="Attempts" sheetId="1" r:id="rId1"/></sheets></workbook>`;
    const workbookRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
      `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>` +
      `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>` +
      `</Relationships>`;
    const stylesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
      `<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
      `<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>` +
      `<fills count="1"><fill><patternFill patternType="none"/></fill></fills>` +
      `<borders count="1"><border/></borders>` +
      `<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>` +
      `<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>` +
      `<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>` +
      `</styleSheet>`;

    return zipStore([
      { name: "[Content_Types].xml", data: contentTypes },
      { name: "_rels/.rels", data: rootRels },
      { name: "xl/workbook.xml", data: workbookXml },
      { name: "xl/_rels/workbook.xml.rels", data: workbookRels },
      { name: "xl/styles.xml", data: stylesXml },
      { name: "xl/worksheets/sheet1.xml", data: sheetXml }
    ]);
  }

  function exportTimestamp() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
  }

  function exportExcel() {
    // Measurement version: "Session" is a measurement; "Complete" says whether
    // it reached 33 attempts (only complete ones are on the leaderboard).
    // No Serenity Coin column — coins are not part of this version.
    const headers = ["Date", "Time", "Measurement", "Attempt", "Attempt result", "Error time", "Accuracy", "Hand", "Complete"];
    const numericCols = new Set([2, 3, 4, 5]);
    const rows = exportRows().map((row) => [
      row.date,
      row.time,
      row.sessionNumber,
      row.attemptNumber,
      row.attemptResult,
      row.errorTime,
      row.accuracy,
      row.hand,
      row.complete
    ]);
    const bytes = buildXlsxBytes(headers, rows, numericCols);
    const filename = `play_of_time_measurement_${exportTimestamp()}.xlsx`;
    downloadBlob(filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
  }

  function bindSettings() {
    const toggle = (id, key, rerender) => {
      document.getElementById(id) && document.getElementById(id).addEventListener("change", (event) => {
        db.settings[key] = event.target.checked;
        saveDb();
        if (rerender) render();
      });
    };
    // Sound / Vibration re-render: Beats per second unlocks only while one is on.
    toggle("soundOn", "soundOn", true);
    toggle("vibrationOn", "vibrationOn", true);
    document.querySelectorAll("[data-level]").forEach((btn) => {
      btn.addEventListener("click", () => setLevel(Number(btn.dataset.level)));
    });
    document.querySelectorAll("[data-bps]").forEach((btn) => {
      btn.addEventListener("click", () => {
        db.settings.beatsPerSecond = Number(btn.dataset.bps);
        saveDb();
        render();
      });
    });
    document.querySelectorAll("[data-hand]").forEach((btn) => {
      btn.addEventListener("click", () => {
        db.settings.selectedHand = btn.dataset.hand;
        saveDb();
        render();
      });
    });
    document.getElementById("exportSettings") && document.getElementById("exportSettings").addEventListener("click", () => {
      exportExcel();
    });
  }

  // Switching the level restarts the measurement: an open one is closed as
  // unfinished (it does not reach the leaderboard).
  function setLevel(levelId) {
    if (![1, 2, 3].includes(levelId) || levelId === db.settings.currentLevelId) return;
    const open = currentSession();
    if (open) closeSession(open.id, false);
    db.settings.currentLevelId = levelId;
    db.settings.currentTaskId = 1;
    db.settings.accuracy = currentQuestFrom(levelId).accuracy;
    resetQuestAdaptiveParams(currentQuestFrom(levelId));
    lastDitherCellIndex = null;
    pbDisplayHalfMs = null;
    saveDb();
    render();
  }

  function renderAbout() { view = "play"; renderPlay(); }
  function bindAbout() {}

  function startMeditationVibration() {
    stopMeditationVibration();
    const tick = () => { if (navigator.vibrate) navigator.vibrate(50); };
    tick();
    meditationVibrateTimer = setInterval(tick, 3000);
  }

  function stopMeditationVibration() {
    if (meditationVibrateTimer) { clearInterval(meditationVibrateTimer); meditationVibrateTimer = 0; }
  }

  function closeMeditation() {
    stopMeditationVibration();
    meditationOpen = false;
    render();
  }

  function periodToggle() {
    return `<div class="period-toggle">${PERIODS.map((period) => `<button data-period="${period}" class="${db.settings.periodFilter === period ? "active" : ""}">${periodLabel(period)}</button>`).join("")}</div>`;
  }

  function bindPeriodToggle() {
    document.querySelectorAll("[data-period]").forEach((button) => {
      button.addEventListener("click", () => {
        db.settings.periodFilter = button.dataset.period;
        saveDb();
        render();
      });
    });
  }

  // Nav icons — ported 1:1 from native vector drawables (fill = currentColor for tint).
  const NAV_ICONS = {
    play: '<img src="./assets/nav-play-glyph.png" alt="" />',
    settings: '<svg viewBox="0 0 24 24"><path fill-rule="evenodd" fill="currentColor" d="M10.62,3.379C10.675,3.156 10.875,3 11.105,3H12.895C13.125,3 13.325,3.156 13.38,3.379L13.756,4.881C13.8,5.057 13.937,5.195 14.111,5.249C14.518,5.376 14.909,5.539 15.281,5.734C15.442,5.819 15.636,5.818 15.792,5.725L17.12,4.928C17.317,4.81 17.569,4.841 17.731,5.003L18.997,6.269C19.159,6.431 19.19,6.683 19.072,6.88L18.275,8.208C18.182,8.364 18.181,8.558 18.266,8.719C18.461,9.091 18.624,9.482 18.751,9.889C18.805,10.063 18.942,10.2 19.119,10.244L20.621,10.62C20.844,10.675 21,10.875 21,11.105V12.895C21,13.125 20.844,13.325 20.621,13.38L19.119,13.756C18.942,13.8 18.805,13.937 18.751,14.111C18.624,14.518 18.461,14.909 18.266,15.281C18.181,15.442 18.182,15.636 18.275,15.792L19.072,17.12C19.19,17.317 19.159,17.569 18.997,17.731L17.731,18.997C17.569,19.159 17.317,19.19 17.12,19.072L15.792,18.275C15.636,18.182 15.442,18.181 15.281,18.266C14.909,18.461 14.518,18.624 14.111,18.751C13.937,18.805 13.8,18.942 13.756,19.119L13.38,20.621C13.325,20.844 13.125,21 12.895,21H11.105C10.875,21 10.675,20.844 10.62,20.621L10.244,19.119C10.2,18.942 10.063,18.805 9.889,18.751C9.482,18.624 9.091,18.461 8.719,18.266C8.558,18.181 8.364,18.182 8.208,18.275L6.88,19.072C6.683,19.19 6.431,19.159 6.269,18.997L5.003,17.731C4.841,17.569 4.81,17.317 4.928,17.12L5.725,15.792C5.818,15.636 5.819,15.442 5.734,15.281C5.539,14.909 5.376,14.518 5.249,14.111C5.195,13.937 5.057,13.8 4.881,13.756L3.379,13.38C3.156,13.325 3,13.125 3,12.895V11.105C3,10.875 3.156,10.675 3.379,10.62L4.881,10.244C5.057,10.2 5.195,10.063 5.249,9.889C5.376,9.482 5.539,9.091 5.734,8.719C5.819,8.558 5.818,8.364 5.725,8.208L4.928,6.88C4.81,6.683 4.841,6.431 5.003,6.269L6.269,5.003C6.431,4.841 6.683,4.81 6.88,4.928L8.208,5.725C8.364,5.818 8.558,5.819 8.719,5.734C9.091,5.539 9.482,5.376 9.889,5.249C10.063,5.195 10.2,5.057 10.244,4.881L10.62,3.379ZM7.05,12C7.05,14.734 9.266,16.95 12,16.95C14.734,16.95 16.95,14.734 16.95,12C16.95,9.266 14.734,7.05 12,7.05C9.266,7.05 7.05,9.266 7.05,12Z"/></svg>',
    about: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z"/></svg>',
    leaderboard: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M3,21H21V19H3V21ZM6,17H9V10H6V17ZM10.5,17H13.5V6H10.5V17ZM15,17H18V13H15V17Z"/></svg>',
    export: '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M9.75,18.5C9.336,18.5 9,18.836 9,19.25V20.994C9,22.371 10.117,23.488 11.494,23.488H20.506C21.883,23.488 23,22.371 23,20.994V19.25C23,18.836 22.664,18.5 22.25,18.5C21.836,18.5 21.5,18.836 21.5,19.25V20.6C21.5,21.373 20.873,22 20.1,22H11.9C11.127,22 10.5,21.373 10.5,20.6V19.25C10.5,18.836 10.164,18.5 9.75,18.5Z"/><path fill="currentColor" d="M16.886,12.779L18.5,14.488C18.844,14.832 19.481,15.05 19.825,14.706C20.169,14.362 20.169,13.804 19.825,13.46L16.623,10.258C16.446,10.082 16.214,9.996 15.983,10C15.752,9.996 15.52,10.082 15.343,10.258L12.141,13.46C11.797,13.804 11.797,14.362 12.141,14.706C12.485,15.05 13.156,14.832 13.5,14.488L15.157,12.779V19.488C15.157,20.04 15.448,20.488 16,20.488C16.552,20.488 16.886,20.006 16.886,19.454V12.779Z"/><path fill="currentColor" d="M2,6.75C2,6.336 2.336,6 2.75,6H21.25C21.664,6 22,6.336 22,6.75C22,7.164 21.664,7.5 21.25,7.5H2.75C2.336,7.5 2,7.164 2,6.75Z"/><path fill="currentColor" d="M2,2.75C2,2.336 2.336,2 2.75,2H21.25C21.664,2 22,2.336 22,2.75C22,3.164 21.664,3.5 21.25,3.5H2.75C2.336,3.5 2,3.164 2,2.75Z"/><path fill="currentColor" d="M2,2.75C2,2.336 2.336,2 2.75,2C3.164,2 3.5,2.336 3.5,2.75V21.25C3.5,21.664 3.164,22 2.75,22C2.336,22 2,21.664 2,21.25V2.75Z"/><path fill="currentColor" d="M8.25,2C8.664,2 9,2.336 9,2.75V16.25C9,16.664 8.664,17 8.25,17C7.836,17 7.5,16.664 7.5,16.25V2.75C7.5,2.336 7.836,2 8.25,2Z"/></svg>'
  };

  function navBalanceGauge() {
    const attempts = attemptsForPeriod("TODAY");
    let red = 0, blue = 0;
    attempts.forEach((a) => { const e = signedErrorMs(a); if (e < 0) red += 1; else if (e > 0) blue += 1; });
    const total = red + blue;
    const ratio = total ? Math.max(-1, Math.min(1, (blue - red) / total)) : 0;
    const cx = 12, cy = 12, r = 10.5;
    const ang = (270 + ratio * 80) * Math.PI / 180;
    const ex = cx + r * Math.cos(ang), ey = cy + r * Math.sin(ang);
    const tip = ratio < -0.15 ? "var(--early)" : ratio > 0.15 ? "var(--late)" : "var(--win)";
    return `<svg class="nav-balance" viewBox="0 0 24 12" width="24" height="12">
      <path d="M1.5 12 A10.5 10.5 0 0 1 12 1.5" fill="none" stroke="var(--early)" stroke-width="3" stroke-linecap="round" opacity="0.85"/>
      <path d="M12 1.5 A10.5 10.5 0 0 1 22.5 12" fill="none" stroke="var(--late)" stroke-width="3" stroke-linecap="round" opacity="0.85"/>
      <line x1="${cx}" y1="${cy}" x2="${ex.toFixed(2)}" y2="${ey.toFixed(2)}" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/>
      <circle cx="${cx}" cy="${cy}" r="1.6" fill="#fff" opacity="0.9"/>
      <circle cx="${ex.toFixed(2)}" cy="${ey.toFixed(2)}" r="2.2" fill="${tip}"/>
    </svg>`;
  }

  function navTab(viewName, label, iconHtml, id) {
    const active = viewName && view === viewName;
    const attrs = id ? `id="${id}"` : `data-view="${viewName}"`;
    return `<button ${attrs} class="nav-tab ${active ? "active" : ""}"><span class="nav-ico">${iconHtml}</span><span class="nav-label">${label}</span></button>`;
  }

  // Visual tab order (native, Alex 2026-08-22): Play–Ranks–Analytics–
  // Settings–About — puts the Analytics balance gauge dead-center of five
  // tabs. Export is no longer a nav tab at all (moved into Settings, next
  // to "Erase all data" — see renderSettings()). No per-tab offset trick —
  // native's own note is explicit that the row stays evenly split
  // (space-evenly / flex:1 each), the centering comes from tab ORDER only.
  function nav() {
    return `
      <nav class="bottom-nav">
        ${navTab("play", "Play", NAV_ICONS.play)}
        ${navTab("leaderboard", "Ranks", NAV_ICONS.leaderboard)}
        ${navTab("analytics", "Analytics", navBalanceGauge())}
        ${navTab("settings", "Settings", NAV_ICONS.settings)}
      </nav>`;
  }

  function bindNav() {
    document.querySelectorAll("[data-view]").forEach((button) => {
      button.addEventListener("click", () => {
        cancelAnimationFrame(timer.raf);
        stopMetronome();
        flushPlayTicker();
        // Leaving About via the bottom nav (not the tap-to-dismiss overlay)
        // never called closeMeditation() before -- the vibration interval
        // kept ticking forever in the background, and re-opening About would
        // jump straight back into the fullscreen view (Alex 2026-09-18, found
        // right after the meditation feature shipped).
        stopMeditationVibration();
        meditationOpen = false;
        // Same idea as meditationOpen above -- don't leave a compare popup
        // or a report iframe open in the background across tabs.
        leaderboardCompareRow = null;
        leaderboardReportUrl = null;
        leaderboardReportLoading = false;
        if (timer.state === "running") {
          timer.state = "ready";
          timer.elapsed = 0;
          timer.resultType = "none";
        }
        view = button.dataset.view;
        if (view === "play") startPlayTicker();
        // Re-entering the leaderboard tab should reflect anything that
        // synced while the player was elsewhere, not a stale earlier fetch
        // or a stale error from a previous visit.
        if (view === "leaderboard") { leaderboardData = null; leaderboardError = null; mlbData = null; mlbError = false; mlbDataLevel = null; syncMeasurements(); }
        render();
      });
    });
  }

  // Native GetSessionComparisonUseCase port (Alex 2026-09-25) -- exact stats
  // comparison against the immediately preceding session (ANY level/quest,
  // matches native's global session ordering by startTime), MEDIAN-based
  // early/late errors (native uses median -- the previous web version wrongly
  // used geometricMean here). No face on this screen (native has none).
  const SS_AIM_MS = AIM_TIME;

  function ssMedian(sortedArr) {
    return sortedArr.length ? sortedArr[Math.floor(sortedArr.length / 2)] : 0;
  }

  function ssAvgScm(attempts) {
    return attempts.length ? attempts.reduce((sum, a) => sum + (a.scm || 0), 0) / attempts.length : 0;
  }

  function ssEarlyMedianMs(attempts) {
    const errors = attempts
      .map((a) => (a.endTime - a.startTime) - SS_AIM_MS)
      .filter((e) => e < 0)
      .map((e) => -e)
      .sort((a, b) => a - b);
    return ssMedian(errors);
  }

  function ssLateMedianMs(attempts) {
    const errors = attempts
      .map((a) => (a.endTime - a.startTime) - SS_AIM_MS)
      .filter((e) => e > 0)
      .sort((a, b) => a - b);
    return ssMedian(errors);
  }

  function ssHitRatePct(attempts) {
    if (!attempts.length) return 0;
    const wins = attempts.filter(isTrueThree).length;
    return Math.floor(wins * 100 / attempts.length);
  }

  // Native formatDuration(durationMs, accuracyOrdinal) -- ROUNDS (unlike
  // formatTime()'s SECONDS branch, which truncates); kept separate on purpose.
  function ssFormatAttempt(durationMs, accuracyOrdinal) {
    const factor = Math.pow(10, accuracyOrdinal);
    const rounded = Math.round(durationMs / 1000 * factor) / factor;
    if (accuracyOrdinal === 0) return rounded.toFixed(0);
    if (accuracyOrdinal === 1) return rounded.toFixed(1);
    if (accuracyOrdinal === 2) return rounded.toFixed(2);
    return rounded.toFixed(3);
  }

  function buildSessionSummary(sessionId) {
    const allSessions = [...db.sessions].sort((a, b) => a.startTime - b.startTime);
    const currentIdx = allSessions.findIndex((x) => x.id === sessionId);
    const currentAttempts = attemptsBySessionId(sessionId);
    const prevSession = currentIdx > 0 ? allSessions[currentIdx - 1] : null;
    const prevAttempts = prevSession ? attemptsBySessionId(prevSession.id) : [];
    const hasPrevious = prevAttempts.length > 0;

    const scmCurrent = ssAvgScm(currentAttempts);
    const scmPrev = ssAvgScm(prevAttempts);
    const earlyCurrent = ssEarlyMedianMs(currentAttempts);
    const earlyPrev = ssEarlyMedianMs(prevAttempts);
    const lateCurrent = ssLateMedianMs(currentAttempts);
    const latePrev = ssLateMedianMs(prevAttempts);
    const hitCurrent = ssHitRatePct(currentAttempts);
    const hitPrev = ssHitRatePct(prevAttempts);

    const improvedCount = !hasPrevious ? 0 : [
      scmCurrent > scmPrev && scmPrev > 0,
      earlyCurrent < earlyPrev && earlyPrev > 0,
      lateCurrent < latePrev && latePrev > 0,
      hitCurrent > hitPrev
    ].filter(Boolean).length;

    const bestTrio = [...currentAttempts]
      .sort((a, b) => Math.abs((a.endTime - a.startTime) - SS_AIM_MS) - Math.abs((b.endTime - b.startTime) - SS_AIM_MS))
      .slice(0, 3)
      .map((a) => ssFormatAttempt(a.endTime - a.startTime, a.accuracy));

    const absErrMs = (a) => Math.abs((a.endTime - a.startTime) - SS_AIM_MS);
    const bestThisMs = currentAttempts.length ? Math.min(...currentAttempts.map(absErrMs)) : 9999999;
    const otherAttempts = db.attempts.filter((a) => a.sessionId !== sessionId);
    const bestEverMs = otherAttempts.length ? Math.min(...otherAttempts.map(absErrMs)) : 9999999;

    return {
      hasPrevious,
      improvedCount,
      overallBetter: improvedCount >= 3,
      overallWorse: improvedCount <= 1,
      scmPaceCurrent: scmCurrent,
      scmPacePrevious: scmPrev,
      earlyErrCurrentMs: earlyCurrent,
      earlyErrPreviousMs: earlyPrev,
      lateErrCurrentMs: lateCurrent,
      lateErrPreviousMs: latePrev,
      hitRateCurrent: hitCurrent,
      hitRatePrevious: hitPrev,
      bestTrio,
      isPersonalRecord: bestThisMs < bestEverMs,
      personalRecordMs: Math.round(bestThisMs)
    };
  }

  function ssMetricRow(label, prevText, currText, improved, worsened, higherBetter) {
    const arrow = improved && higherBetter ? "\u2191"
      : improved ? "\u2193"
      : worsened && !higherBetter ? "\u2191"
      : worsened ? "\u2193"
      : "\u2192";
    const cls = improved ? "ss2-good" : worsened ? "ss2-bad" : "ss2-flat";
    return `<div class="ss2-row"><span class="ss2-label">${label}</span><span class="ss2-vals"><span class="ss2-prev">${prevText}</span><span class="ss2-arrow ${cls}">${arrow}</span><span class="ss2-curr ${cls}">${currText}</span></span></div>`;
  }

  function ssSingleRow(label, value) {
    return `<div class="ss2-row"><span class="ss2-label">${label}</span><span class="ss2-curr">${value}</span></div>`;
  }

  function sessionSummaryOverlay() {
    const g = sessionSummary;
    const headline = !g.hasPrevious
      ? "Session complete!"
      : g.overallBetter
        ? "\u2713 Session was BETTER than previous"
        : g.overallWorse
          ? "Session was WORSE than previous"
          : "Session was about the SAME as previous";

    const rows = g.hasPrevious ? [
      ssMetricRow("SCM pace", `${g.scmPacePrevious.toFixed(2)} c/att`, `${g.scmPaceCurrent.toFixed(2)} c/att`,
        g.scmPaceCurrent > g.scmPacePrevious && g.scmPacePrevious > 0,
        g.scmPaceCurrent < g.scmPacePrevious && g.scmPacePrevious > 0, true),
      ssMetricRow("Early errors", `${Math.round(g.earlyErrPreviousMs)} ms`, `${Math.round(g.earlyErrCurrentMs)} ms`,
        g.earlyErrCurrentMs < g.earlyErrPreviousMs && g.earlyErrPreviousMs > 0,
        g.earlyErrCurrentMs > g.earlyErrPreviousMs && g.earlyErrPreviousMs > 0, false),
      ssMetricRow("Late errors", `${Math.round(g.lateErrPreviousMs)} ms`, `${Math.round(g.lateErrCurrentMs)} ms`,
        g.lateErrCurrentMs < g.lateErrPreviousMs && g.lateErrPreviousMs > 0,
        g.lateErrCurrentMs > g.lateErrPreviousMs && g.lateErrPreviousMs > 0, false),
      ssMetricRow("Hit rate", `${g.hitRatePrevious}%`, `${g.hitRateCurrent}%`,
        g.hitRateCurrent > g.hitRatePrevious, g.hitRateCurrent < g.hitRatePrevious, true)
    ].join("") : [
      ssSingleRow("SCM pace", `${g.scmPaceCurrent.toFixed(2)} c/att`),
      ssSingleRow("Early errors", `${Math.round(g.earlyErrCurrentMs)} ms`),
      ssSingleRow("Late errors", `${Math.round(g.lateErrCurrentMs)} ms`),
      ssSingleRow("Hit rate", `${g.hitRateCurrent}%`)
    ].join("");

    const trioHtml = g.bestTrio.length ? `
          <div class="ss2-trio">
            <span class="ss2-trio-label">Best trio:</span>
            <div class="ss2-trio-vals">${g.bestTrio.join("  \u00b7  ")}</div>
          </div>` : "";

    const recordHtml = g.isPersonalRecord ? `<div class="ss2-record">\ud83c\udfc6 New personal record: ${g.personalRecordMs} ms!</div>` : "";

    return `
      <div class="overlay">
        <div class="dialog session-summary" id="sessionSummaryCard">
          <div class="ss2-head">
            <div class="ss2-headline">${headline}</div>
            <span class="ss2-close" id="sessionSummaryClose">\u00d7</span>
          </div>
          <div class="ss2-table">${rows}</div>
          ${trioHtml}
          ${recordHtml}
          <div class="ss2-hint">Tap anywhere to dismiss</div>
        </div>
      </div>`;
  }

  // Native HelpTapOverlay port: tap "?" → tap any zone → bubble explains it.
  // Help texts (2026-10-06): same pages as native HelpTapOverlay.kt helpPages(),
  // minus what the web does not have (period arrows, crown). Each array item is
  // one bubble page (paged with ‹ 1/2 ›), \n starts a paragraph, **…** is bold.
  const HELP_TEXT = {
    quest: [
      "**Quest** — your mission for this session.\n**Targets** — how many hits it needs. A target you hit turns into the spinning logo.\n**Overlapping targets** — the hits must come in a row, and a miss resets the streak. **Targets apart** — the hits may come anywhere in the session.",
      "**Circles** — your attempts: green = hit, red = early, blue = late. Empty circles are the attempts you have left.\n**Goal** (3.00) — the precision of the level. After a struggle the quest is eased and a tolerance appears: 3.00 ± 0.01.\n**Check mark** — the quest is taken."
    ],
    stopwatch: [
      "**Stopwatch.** Tap RUN, feel three seconds pass, tap STOP. The button reacts to the touch, not the release.\n**Result colour:** green — on the goal (in an eased quest, within the tolerance), red — stopped early, blue — late.",
      "**Bar above the digits** — a magnifying glass around 3.0: red edge early, blue edge late, green zone the tolerance. On auto scale it zooms in as you get closer.\n**Face** — smiles when the attempt was no worse than the previous one.",
      "**HOORAY!** — the quest is taken. **RESET** arms the next attempt.\n**Hint line** under the stopwatch names a pattern in your recent attempts — for example, stopping early again and again — and suggests what to adjust."
    ],
    log: [
      "**Attempt log** — today's attempts, grouped by session.\nEach row: the **number**, the **stopped time** and the **error bar** — its side is the direction (red early, blue late), its length the size. A hit shows a green dot instead.",
      "**Last column** — the Serenity the attempt earned. The newest row shows «SC»: its coins are counted when the next attempt lands.\n**Ovals** mark repeats: green — the same miss twice in a row, on either side; yellow — misses one step of precision apart. Switch them off in Settings.\n**Thumb up** on a session — its quest was taken."
    ],
    distribution: [
      "**Distribution** — where today's errors pile up.\nEach **column** counts the attempts with that error: red to the left = early, blue to the right = late. The further from the centre, the bigger the miss.\n**Green centre column** — the exact aim at this level's precision: 3.0, 3.00 or 3.000.",
      "**Dashed lines** — your typical early and late misses.\n**Total tries** — today's attempts. **Coin** — the Serenity they earned.\nA narrow pile around a tall centre means control; a pile shifted to one side shows your lean."
    ],
    geom: [
      "**Geom. Mean** — your typical miss today.\n**Red** — the typical early error. **Blue** — the typical late error. **Middle number** — your overall lean: minus = you tend to stop early, plus = late.\nThe same three lines run through the distribution and the attempt log.",
      "It is a geometric mean, so a few wild misses sway it less than an ordinary average.\nRed and blue close to the centre mean a tight hand. The middle number near zero means you have found the serenity point."
    ],
    statPeriod: [
      "**Period** — what the cards below are counted for: the current **Session**, **Today**, a calendar **Week**, a **Month** or **All time**.\nA session that crossed midnight counts whole.",
      "**Gauge** on the left — today's balance of early and late stops.\n**Not filtered:** the sessions log at the bottom always shows your whole history, and **Practice** in the top card is counted over all time."
    ],
    statSummary: [
      "**Totals** for the selected period.\n**Play time** — time spent on the Play screen. **Attempts**, **Sessions** — how many. **Serenity coins** — earned in the period.\n**Wins/precise** — wins, eased quests included; after the slash, exact hits with no tolerance.",
      "**Pace of play** — attempts per minute of play.\n**Practice** — your average play time per day you played, over all time.\n**Face** on the right sums up the period. Tap it for the full report; it needs at least 30 attempts."
    ],
    statChance: [
      "**Chance of win** for the period.\n**Hit rate** — the share of attempts that won, eased quests included.\n**Attempts per win** — what one win costs you on average.",
      "**Lucky Index** — how much more often you hit exactly 3.000 than blind chance would. Chance here is one millisecond out of the spread of your results.\n1 = no better than chance, above 1 = skill. It appears after your first exact 3.000."
    ],
    statGeom: [
      "**Geometric mean ÷ spread**, with the extreme 3% of results cut off at each end.\n**Early** — your typical early miss. **Late** — your typical late miss. **Absolute** — your overall lean: minus = early, plus = late.\nA geometric mean is swayed less by a few wild misses than an ordinary average.",
      "**Factor after ÷** — your spread: a typical attempt lands between the mean ÷ factor and the mean × factor.\nThe closer it is to 1, the steadier your hand."
    ],
    statRange: [
      "**Range of deviations.**\n**Top strip** — the period's attempts split into early (red), wins (green) and late (blue). The **beak** marks the serenity point, exactly 3.0.",
      "**Lower strip** — the range of your results, 97% on both sides: the extreme 3% is cut off at each end.\nIts **ends** are your furthest usual misses; the **colour boundary** shows how the range divides between early and late.",
      "**White ticks** — your most frequent miss on each side, written below as «0.020 × 6»: that miss, repeated 6 times.\nOnly misses repeated at least twice count."
    ],
    statPace: [
      "**Chart** — how your play changed over the period. Pick what it shows in the **block title**: Pace of learning, Hit rate or Lucky index.\n**One point:** today — a block of attempts in a row; week — one sitting (play with no break over 10 minutes); month — a day; all time — an hour, a day or a month, depending on how long you have played.",
      "**Pace of learning** — how good your attempts were: the average share of the maximum Serenity an attempt can earn at its level, so levels compare fairly. **SC/att** — the average Serenity per attempt.\n**Hit rate** — the share of attempts that won.\n**Lucky index** — how often you hit exactly 3.000, compared with chance.",
      "A **rising line** means you are getting better."
    ],
    statSessions: [
      "**Sessions log** — your whole history, whatever period is selected: year, month, week, day, session. Tap a row to unfold it.\n**lucky** — the Lucky Index of the row («no luck» = no exact 3.000 in the session). **mean** — typical early / late miss, ms. **att4win** — attempts per win.",
      "**Under each row:** play time · win %. Above the day, both are averages over the days you played.\n**Thumb up:** session — its quest was taken (eased too); day — at least one quest was taken; week, month, year — at least half of the days played are up."
    ]
  };



  // "**bold**" → <b>, "\n" → paragraph. Texts are ours, but escape anyway.
  function helpMarkup(text) {
    return text.split("\n").map((p) => "<p>" + escapeHtml(p).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>") + "</p>").join("");
  }

  function helpTapOverlay() {
    const t = helpTopic;
    const bubble = t
      ? `<div class="help-frame" id="helpFrame"></div>
         <div class="help-bubble-wrap" id="helpBubbleWrap">
           <span class="help-beak" id="helpBeak"></span>
           <div class="help-bubble" id="helpBubble">
             <div class="help-bubble-body">
               <div class="help-bubble-text">${helpMarkup(HELP_TEXT[t][Math.min(helpPage, HELP_TEXT[t].length - 1)])}</div>
               ${HELP_TEXT[t].length > 1 ? `<div class="help-pager">
                 <button class="help-page-btn" id="helpPrev" type="button" ${helpPage > 0 ? "" : "disabled"}>‹</button>
                 <span class="help-page-num">${helpPage + 1} / ${HELP_TEXT[t].length}</span>
                 <button class="help-page-btn" id="helpNext" type="button" ${helpPage < HELP_TEXT[t].length - 1 ? "" : "disabled"}>›</button>
               </div>` : ""}
             </div>
             <button class="help-bubble-x" id="helpBubbleX" type="button">×</button>
           </div>
         </div>`
      : `<div class="help-banner">Tap any element to see what it does. Tap ? again to exit.</div>`;
    return `<div class="help-tap-overlay" id="helpTap">${bubble}</div>`;
  }

  function bindHelpTap() {
    if (!helpMode) return;
    const overlay = document.getElementById("helpTap");
    if (!overlay) return;

    // Clicks on the bubble body / × must not fall through to zone hit-testing.
    document.getElementById("helpBubble") && document.getElementById("helpBubble").addEventListener("click", (e) => e.stopPropagation());
    const pageBtn = (id, step) => document.getElementById(id) && document.getElementById(id).addEventListener("click", (e) => {
      e.stopPropagation();
      helpPage += step;
      render();
    });
    pageBtn("helpPrev", -1);
    pageBtn("helpNext", 1);
    document.getElementById("helpBubbleX") && document.getElementById("helpBubbleX").addEventListener("click", (e) => {
      e.stopPropagation();
      helpTopic = null;
      render();
    });

    overlay.addEventListener("click", (e) => {
      const x = e.clientX, y = e.clientY;
      // Tap on "?" exits help mode.
      const q = document.querySelector(".help-button");
      if (q) {
        const r = q.getBoundingClientRect();
        if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
          helpMode = false; helpTopic = null; render(); return;
        }
      }
      // Smallest containing zone wins (handles nesting).
      const hit = [...document.querySelectorAll("[data-help]")]
        .map((el) => ({ topic: el.dataset.help, r: el.getBoundingClientRect() }))
        .filter((z) => x >= z.r.left && x <= z.r.right && y >= z.r.top && y <= z.r.bottom)
        .sort((a, b) => a.r.width * a.r.height - b.r.width * b.r.height)[0];
      helpTopic = hit ? hit.topic : null;
      helpPage = 0;
      render();
    });

    positionHelp();
  }

  function positionHelp() {
    const anchor = document.querySelector(".quest-strip") ||
      document.querySelector(".analytics-screen .sticky");
    const anchorBottom = anchor ? anchor.getBoundingClientRect().bottom : 40;
    if (!helpTopic) {
      const banner = document.querySelector(".help-banner");
      if (banner) banner.style.top = (anchorBottom + 8) + "px";
      return;
    }
    // A zone may be several elements (geom = triangles under the histogram +
    // the numbers row) -- frame their union.
    const parts = [...document.querySelectorAll(`[data-help="${helpTopic}"]`)].map((el) => el.getBoundingClientRect());
    const frame = document.getElementById("helpFrame");
    const wrap = document.getElementById("helpBubbleWrap");
    const beak = document.getElementById("helpBeak");
    if (!parts.length || !frame || !wrap) return;
    const left = Math.min(...parts.map((p) => p.left)), top = Math.min(...parts.map((p) => p.top));
    const right = Math.max(...parts.map((p) => p.right)), bottom = Math.max(...parts.map((p) => p.bottom));
    const r = { left, top, right, bottom, width: right - left, height: bottom - top };
    const pad = 4;
    frame.style.left = (r.left - pad) + "px";
    frame.style.top = (r.top - pad) + "px";
    frame.style.width = (r.width + pad * 2) + "px";
    frame.style.height = (r.height + pad * 2) + "px";
    // Bubble goes below the zone if it sits in the top half, else above it.
    const below = (r.top + r.height / 2) < window.innerHeight / 2;
    wrap.classList.toggle("above", !below);
    const gap = 8;
    const bh = wrap.getBoundingClientRect().height;
    wrap.style.top = below ? (r.bottom + gap) + "px" : (r.top - gap - bh) + "px";
    // Beak points at the zone's horizontal centre (clamped inside the bubble).
    if (beak) {
      const cx = Math.max(26, Math.min(window.innerWidth - 26, r.left + r.width / 2));
      beak.style.left = (cx - 12) + "px"; // wrap starts 12px from the left edge
    }
  }

  function welcomeOverlay() {
    return `
      <div class="overlay">
        <div class="dialog welcome-card">
          <h1>Play of Time</h1>
          <p>Time is a tool for self-control. The objective is simple: stop the timer at exactly three seconds. As you progress, the challenge becomes increasingly precise — 3, 3.0, 3.00, or 3.000 seconds.</p>
          <p>This is not a reaction game. Every attempt is an exercise in self-regulation. To stop the timer at the right moment, your conscious mind must compensate for your natural temperament.</p>
          <p>Each attempt lasts only a few seconds and provides immediate, objective feedback. This rapid learning cycle allows you to experiment with focus, relaxation, breathing, visualization, or any strategy of your own.</p>
          <p>As your ability to regulate yourself improves, the effects extend beyond the game. Your history of attempts becomes a record of your functional state over time.</p>
          <button id="welcomeClose" class="welcome-start">Start playing</button>
        </div>
      </div>`;
  }

  function summaryCard(label, value, note) {
    return `<div class="panel"><span class="metric-label">${label}</span><div class="big-number">${value}</div><span class="metric-note">${note}</span></div>`;
  }

  function statRow(label, value, cls = "", labelCls = "") {
    return `<div class="stat-row"><span class="stat-label ${labelCls}">${label}</span><span class="stat-value ${cls}">${value}</span></div>`;
  }

  // Block title above the card, normal case (native StatCard, Alex 2026-10-05).
  function statBlock(title, period, body, help = "", panelCls = "") {
    return `<div class="stat-block"${help ? ` data-help="${help}"` : ""}>
      <h2 class="block-title">${title}${period}</h2>
      <div class="panel ${panelCls}">${body}</div>
    </div>`;
  }

  // Same wording as native toSlashLabel(): "/ day", not "/ today".
  function periodSlash(period) {
    if (period === "SESSION") return "/ session";
    if (period === "TODAY") return "/ day";
    if (period === "WEEK") return "/ week";
    if (period === "MONTH") return "/ month";
    return "/ all time";
  }

  function periodLabel(period) {
    if (period === "SESSION") return "Session";
    if (period === "TODAY") return "Today";
    if (period === "WEEK") return "Week";
    if (period === "MONTH") return "Month";
    return "All time";
  }

  function formatMs(value, signed = false) {
    // Native deviation format: "−21ms" / "+58ms" (no space before the unit).
    if (!Number.isFinite(value) || Math.abs(value) < 0.0001) return signed ? "0ms" : "-";
    const prefix = signed && value > 0 ? "+" : "";
    return prefix + (Math.abs(value) >= 10 ? value.toFixed(0) : value.toFixed(1)) + "ms";
  }

  function formatDuration(ms) {
    // Native compact h/m/s: "3h 34m 26s", "1m 30s", "26s".
    const total = Math.floor((ms || 0) / 1000);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;"
    })[char]);
  }

  recalcScm();
  saveDb();
  window.addEventListener("pagehide", () => {
    stopMetronome();
    flushPlayTicker();
    performSync(true);
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      stopMetronome();
      flushPlayTicker();
      performSync(true);
    } else if (view === "play") {
      startPlayTicker();
    }
  });
  // Browser bars off (Alex 2026-10-14): the page asks for fullscreen on a
  // tap, as the game does when it opens. A browser grants it only inside a
  // real tap, so it happens on a tap, and again on the next tap after the
  // player leaves fullscreen. Never on RUN or while an attempt runs: the
  // screen resize would jump under the stopwatch mid-attempt. iPhone Safari has no
  // fullscreen for pages; there the bars go only when the page is added to
  // the home screen (manifest.webmanifest, display: fullscreen).
  function requestPageFullscreen(event) {
    // Inside the landing page's frame the landing itself holds fullscreen.
    if (window.self !== window.top) return;
    if (timer.state === "running") return;
    // RUN itself starts an attempt — no resize then; the RESET tap after it,
    // or any other tap, switches to fullscreen.
    const onRun = event && event.target && event.target.closest && event.target.closest("#runButton");
    if (onRun && timer.state === "ready") return;
    if (document.fullscreenElement || document.webkitFullscreenElement) return;
    const el = document.getElementById("gameOverlay") || document.documentElement;
    const req = el.requestFullscreen || el.webkitRequestFullscreen;
    if (!req) return;
    try {
      const p = req.call(el, { navigationUI: "hide" });
      if (p && p.catch) p.catch(() => {});
    } catch (_) { /* not allowed here — the next tap tries again */ }
  }
  document.addEventListener("pointerdown", requestPageFullscreen, true);

  // Close button, top-right corner (Alex 2026-10-14). Inside the landing
  // page's frame it asks the landing to leave full screen (the landing then
  // hides the app); opened on its own (play.html, home-screen icon) it goes
  // to the landing page.
  (function addCloseButton() {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "app-close";
    btn.setAttribute("aria-label", "Close");
    btn.textContent = "×";
    btn.addEventListener("click", () => {
      stopMetronome();
      flushPlayTicker();
      if (timer.state === "running") {
        cancelAnimationFrame(timer.raf);
        timer.state = "ready";
        timer.elapsed = 0;
        timer.resultType = "none";
        render();
      }
      if (window.self !== window.top) {
        window.parent.postMessage("pot-close", location.origin);
      } else {
        if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
        location.href = "./index.html";
      }
    });
    (document.getElementById("gameOverlay") || document.body).appendChild(btn);
  })();

  // The landing page (index.html) shows this app in a frame and tells it
  // when the frame is hidden (player left full screen) or shown again. Hidden
  // time is not play time: stop the ticker, drop a running attempt.
  window.addEventListener("message", (event) => {
    if (event.origin !== location.origin) return;
    if (event.data === "pot-hidden") {
      appFrameHidden = true;
      stopMetronome();
      flushPlayTicker();
      if (timer.state === "running") {
        cancelAnimationFrame(timer.raf);
        timer.state = "ready";
        timer.elapsed = 0;
        timer.resultType = "none";
        render();
      }
    } else if (event.data === "pot-shown") {
      appFrameHidden = false;
      if (view === "play") startPlayTicker();
    }
  });

  // Measurement page: play.html is the app itself (no landing overlay to
  // open), so audio is unlocked on the first real tap anywhere.
  document.addEventListener("pointerdown", unlockAudioPlayback, { once: true });
  document.addEventListener("touchstart", unlockAudioPlayback, { once: true, passive: true });
  bindHostPage();
  render();
  syncMeasurements(); // registers the player (name) and sends anything left from a previous visit // catch up anything left over from a previous visit, or backfill on first run
})();
