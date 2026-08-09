/**
 * Goal G5 — lightweight Web Audio beds + stings (no external assets).
 * Autoplay-safe: only starts after user gesture (unmute / first choice).
 */

let ctx: AudioContext | null = null;
let muted = true;
let ambientNodes: { osc: OscillatorNode; gain: GainNode }[] = [];
let unlocked = false;

function ensureCtx(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const AC =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AC) return null;
  if (!ctx) ctx = new AC();
  return ctx;
}

export function isMuted(): boolean {
  return muted;
}

export function setMuted(next: boolean): void {
  muted = next;
  if (muted) {
    stopAmbient();
  } else {
    unlocked = true;
    void resume();
    startAmbient("alley");
  }
}

export async function unlockFromGesture(): Promise<void> {
  unlocked = true;
  await resume();
}

async function resume(): Promise<void> {
  const c = ensureCtx();
  if (c && c.state === "suspended") {
    try {
      await c.resume();
    } catch {
      /* ignore */
    }
  }
}

export function stopAmbient(): void {
  for (const n of ambientNodes) {
    try {
      n.osc.stop();
      n.osc.disconnect();
      n.gain.disconnect();
    } catch {
      /* already stopped */
    }
  }
  ambientNodes = [];
}

/** Soft drone bed keyed by rough biome/tag. */
export function startAmbient(tag: string = "alley"): void {
  if (muted || !unlocked) return;
  const c = ensureCtx();
  if (!c) return;
  stopAmbient();

  const base =
    tag.includes("combat") || tag.includes("square")
      ? 55
      : tag.includes("river")
        ? 70
        : 90;
  const freqs = [base, base * 1.5, base * 2.02];
  for (const f of freqs) {
    const osc = c.createOscillator();
    const gain = c.createGain();
    osc.type = "sine";
    osc.frequency.value = f;
    gain.gain.value = 0.012;
    osc.connect(gain);
    gain.connect(c.destination);
    osc.start();
    ambientNodes.push({ osc, gain });
  }
}

export type StingKind = "crit" | "miss" | "death" | "discover" | "check";

export function playSting(kind: StingKind): void {
  if (muted || !unlocked) return;
  const c = ensureCtx();
  if (!c) return;
  void resume();

  const now = c.currentTime;
  const osc = c.createOscillator();
  const gain = c.createGain();
  osc.connect(gain);
  gain.connect(c.destination);

  switch (kind) {
    case "crit":
      osc.type = "sawtooth";
      osc.frequency.setValueAtTime(440, now);
      osc.frequency.exponentialRampToValueAtTime(880, now + 0.12);
      gain.gain.setValueAtTime(0.08, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
      osc.start(now);
      osc.stop(now + 0.26);
      break;
    case "miss":
      osc.type = "triangle";
      osc.frequency.setValueAtTime(200, now);
      osc.frequency.exponentialRampToValueAtTime(90, now + 0.15);
      gain.gain.setValueAtTime(0.05, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);
      osc.start(now);
      osc.stop(now + 0.2);
      break;
    case "death":
      osc.type = "sine";
      osc.frequency.setValueAtTime(180, now);
      osc.frequency.exponentialRampToValueAtTime(60, now + 0.4);
      gain.gain.setValueAtTime(0.07, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
      osc.start(now);
      osc.stop(now + 0.5);
      break;
    case "discover":
      osc.type = "sine";
      osc.frequency.setValueAtTime(523, now);
      osc.frequency.setValueAtTime(659, now + 0.08);
      osc.frequency.setValueAtTime(784, now + 0.16);
      gain.gain.setValueAtTime(0.05, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
      osc.start(now);
      osc.stop(now + 0.36);
      break;
    default:
      osc.type = "square";
      osc.frequency.setValueAtTime(330, now);
      gain.gain.setValueAtTime(0.04, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);
      osc.start(now);
      osc.stop(now + 0.14);
  }
}
