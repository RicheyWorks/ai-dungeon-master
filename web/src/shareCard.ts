/**
 * Goal G5 — export recap / identity as markdown download + canvas PNG share card.
 */

export type SharePayload = {
  partyTitle?: string;
  questTitle?: string;
  epithets?: string[];
  scars?: string[];
  recap?: string[];
  lastCheck?: {
    stakes?: string;
    roll?: number;
    total?: number;
    difficulty?: number;
    success?: boolean;
    critical?: boolean;
    narration?: string;
  } | null;
};

export function buildShareMarkdown(p: SharePayload): string {
  const lines: string[] = ["# AI Dungeon Master — Run Card", ""];
  if (p.partyTitle) lines.push(`**${p.partyTitle}**`, "");
  if (p.questTitle) lines.push(`Quest: ${p.questTitle}`, "");
  if (p.epithets?.length) {
    lines.push("## Epithets", ...p.epithets.map((e) => `- ${e}`), "");
  }
  if (p.scars?.length) {
    lines.push("## Scars", ...p.scars.map((s) => `- ${s}`), "");
  }
  if (p.recap?.length) {
    lines.push("## Last time…", ...p.recap.map((r) => `- ${r}`), "");
  }
  if (p.lastCheck?.narration) {
    lines.push(
      "## Last check",
      p.lastCheck.stakes ? `Stakes: ${p.lastCheck.stakes}` : "",
      p.lastCheck.roll != null
        ? `Roll: ${p.lastCheck.roll} → ${p.lastCheck.total} vs ${p.lastCheck.difficulty} (${
            p.lastCheck.critical ? "CRIT" : p.lastCheck.success ? "hit" : "miss"
          })`
        : "",
      p.lastCheck.narration,
      "",
    );
  }
  lines.push("_Exported from AI Dungeon Master_");
  return lines.filter((l) => l !== undefined).join("\n");
}

export function downloadText(filename: string, text: string): void {
  const blob = new Blob([text], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** Draw a simple share card PNG and download it. */
export function downloadSharePng(p: SharePayload, filename = "run-card.png"): void {
  const w = 720;
  const h = 420;
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const g = canvas.getContext("2d");
  if (!g) return;

  // background
  const grad = g.createLinearGradient(0, 0, w, h);
  grad.addColorStop(0, "#0f1419");
  grad.addColorStop(1, "#1a2430");
  g.fillStyle = grad;
  g.fillRect(0, 0, w, h);

  // accent bar
  g.fillStyle = "#5b8def";
  g.fillRect(0, 0, 8, h);

  g.fillStyle = "#e8eef6";
  g.font = "bold 28px system-ui, sans-serif";
  g.fillText(p.partyTitle || "Adventurer", 32, 56);

  g.fillStyle = "#8aa0b8";
  g.font = "16px system-ui, sans-serif";
  g.fillText(p.questTitle || "An unfinished tale", 32, 88);

  let y = 130;
  g.fillStyle = "#c5d4e8";
  g.font = "15px system-ui, sans-serif";
  const recap = (p.recap ?? []).slice(0, 3);
  if (recap.length === 0) {
    g.fillText("No chronicle lines yet — make a choice.", 32, y);
  } else {
    for (const line of recap) {
      const wrapped = wrap(g, line, w - 64);
      for (const row of wrapped) {
        g.fillText(row, 32, y);
        y += 22;
      }
      y += 6;
    }
  }

  if (p.epithets?.length) {
    y += 10;
    g.fillStyle = "#5b8def";
    g.font = "bold 13px system-ui, sans-serif";
    g.fillText(p.epithets.slice(0, 4).join("  ·  "), 32, y);
  }

  g.fillStyle = "#5a6a7c";
  g.font = "12px system-ui, sans-serif";
  g.fillText("AI Dungeon Master", 32, h - 24);

  canvas.toBlob((blob) => {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }, "image/png");
}

function wrap(g: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let cur = "";
  for (const word of words) {
    const test = cur ? `${cur} ${word}` : word;
    if (g.measureText(test).width > maxWidth && cur) {
      lines.push(cur);
      cur = word;
    } else {
      cur = test;
    }
  }
  if (cur) lines.push(cur);
  return lines.slice(0, 4);
}
