import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "motion/react";
import { cn } from "@/lib/cn";
import type { GenreShare, StyleAxis, WatchStats } from "@/lib/watchStats";

// The profile's statistics, laid out the way a game's career screen is: a radar of how you watch
// (or of genres) next to a column of plain numbers.

const SIZE = 260;
const CENTER = SIZE / 2;
const RADIUS = 112;
const RINGS = [0.25, 0.5, 0.75, 1];

/** The point on axis `index` of `count`, at `fraction` of the radius; the first axis points up. */
function point(index: number, count: number, fraction: number): [number, number] {
  const angle = -Math.PI / 2 + (index * 2 * Math.PI) / count;
  return [CENTER + Math.cos(angle) * RADIUS * fraction, CENTER + Math.sin(angle) * RADIUS * fraction];
}

const polygon = (points: [number, number][]) => points.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ");

/** What an axis measured, in words: "3.2 episodes a day you watch", "64% of what you started". */
function axisDetail(axis: StyleAxis, t: ReturnType<typeof useTranslation>["t"]): string {
  const percent = Math.round(axis.raw * 100);
  switch (axis.id) {
    case "binge": return t("profile.style.bingeDetail", { value: axis.raw.toFixed(1) });
    case "consistency": return t("profile.style.consistencyDetail", { count: axis.raw });
    case "completion": return t("profile.style.completionDetail", { value: percent });
    case "variety": return t("profile.style.varietyDetail", { count: axis.raw });
    case "night": return t("profile.style.nightDetail", { value: percent });
    case "ongoing": return t("profile.style.ongoingDetail", { value: percent });
  }
}

/** The same measure in a few characters, for under the axis's name: "0.9 ep./day", "64%". */
function axisShort(axis: StyleAxis, t: ReturnType<typeof useTranslation>["t"], locale: string): string {
  const percent = `${Math.round(axis.raw * 100)}%`;
  switch (axis.id) {
    case "binge": return t("profile.style.bingeShort", { value: (Math.round(axis.raw * 10) / 10).toLocaleString(locale) });
    case "consistency": return t("profile.style.consistencyShort", { count: axis.raw });
    case "variety": return t("profile.style.varietyShort", { count: axis.raw });
    default: return percent;
  }
}

/** One corner of the radar, already in words. */
interface RadarAxis {
  id: string;
  label: string;
  /** 0..1 */
  value: number;
  /** Under the name, always shown. */
  short: string;
  /** Under the radar, on hover. */
  detail: string;
}

// The genre radar: enough corners to be a shape, few enough that the names still fit around it.
const GENRE_AXES = 6;
const MIN_GENRE_AXES = 3;
const LABEL_MAX = 14;

const shorten = (label: string) => (label.length > LABEL_MAX ? `${label.slice(0, LABEL_MAX - 1)}…` : label);

function genreAxes(genres: GenreShare[], t: ReturnType<typeof useTranslation>["t"]): RadarAxis[] {
  const shown = genres.slice(0, GENRE_AXES);
  // Scaled to the leading genre, so the shape shows the balance between them rather than a speck.
  const top = shown[0]?.share || 1;
  return shown.map((g) => ({
    id: g.name,
    label: g.name,
    value: g.share / top,
    short: `${Math.round(g.share * 100)}%`,
    detail: t("profile.style.genreDetail", { name: g.name, value: Math.round(g.share * 100) }),
  }));
}

function Radar({ axes, label, footer }: { axes: RadarAxis[]; label: string; footer?: React.ReactNode }) {
  const gradientId = useId();
  const [hovered, setHovered] = useState<string | null>(null);
  const count = axes.length;
  // Drawn on a square-root scale: most people sit in the lower part of most axes, and on a straight
  // one the shape was a thin spike lost in the middle of the grid. The order of the corners, and
  // the numbers under the names, are unchanged. An empty radar still shows a speck in the middle.
  const shape = axes.map((a, i) => point(i, count, Math.max(0.06, Math.sqrt(a.value))));
  const active = axes.find((a) => a.id === hovered) ?? null;

  return (
    <div className="flex flex-col items-center">
      <svg viewBox={`-72 -16 ${SIZE + 144} ${SIZE + 32}`} className="w-full max-w-[480px] overflow-visible" role="img" aria-label={label}>
        <defs>
          <radialGradient id={gradientId}>
            <stop offset="0%" stopColor="rgb(var(--color-accent))" stopOpacity="0.75" />
            <stop offset="100%" stopColor="rgb(var(--color-accent))" stopOpacity="0.18" />
          </radialGradient>
        </defs>

        {/* Outermost first, each band a shade lighter than the one inside it, so the grid reads as
            a solid backdrop rather than lines on nothing. */}
        {[...RINGS].reverse().map((r, i) => (
          <polygon key={r} points={polygon(axes.map((_, j) => point(j, count, r)))} fill={`rgb(var(--color-text) / ${i % 2 === 0 ? 0.035 : 0.02})`} stroke="rgb(var(--color-text) / 0.09)" strokeWidth={1} />
        ))}
        {axes.map((a, i) => {
          const [x, y] = point(i, count, 1);
          return <line key={a.id} x1={CENTER} y1={CENTER} x2={x} y2={y} stroke={hovered === a.id ? "rgb(var(--color-text) / 0.3)" : "rgb(var(--color-text) / 0.09)"} strokeWidth={1} />;
        })}

        <motion.g initial={{ scale: 0, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }} style={{ transformOrigin: `${CENTER}px ${CENTER}px` }}>
          <polygon points={polygon(shape)} fill={`url(#${gradientId})`} stroke="rgb(var(--color-accent-text))" strokeWidth={1.5} strokeLinejoin="round" />
          {shape.map(([x, y], i) => (
            <circle key={axes[i].id} cx={x} cy={y} r={hovered === axes[i].id ? 4.5 : 3} fill="rgb(var(--color-accent-text))" className="transition-[r]" />
          ))}
        </motion.g>

        {axes.map((a, i) => {
          const [x, y] = point(i, count, 1.13);
          const centred = Math.abs(x - CENTER) < 4;
          const anchor = centred ? "middle" : x > CENTER ? "start" : "end";
          // Name and value as two lines, kept clear of the grid: above it at the top corner the
          // value goes over the name, everywhere else under it.
          const top = centred && y < CENTER;
          const nameY = top ? y + 2 : centred ? y - 2 : y - 6;
          const valueY = top ? y - 12 : centred ? y + 12 : y + 8;
          return (
            <g key={a.id} onMouseEnter={() => setHovered(a.id)} onMouseLeave={() => setHovered((h) => (h === a.id ? null : h))} onClick={() => setHovered((h) => (h === a.id ? null : a.id))} className="cursor-default">
              {/* A wider invisible target than the letters themselves. */}
              <circle cx={x} cy={y} r={26} fill="transparent" />
              <text
                x={x}
                y={nameY}
                textAnchor={anchor}
                dominantBaseline="middle"
                className={cn("select-none text-[10.5px] font-semibold uppercase tracking-[0.14em] transition-colors", hovered === a.id ? "fill-text" : "fill-muted")}
              >
                {shorten(a.label)}
              </text>
              <text x={x} y={valueY} textAnchor={anchor} dominantBaseline="middle" className="select-none fill-accent-text text-[12px] font-semibold tabular-nums">
                {a.short}
              </text>
            </g>
          );
        })}
      </svg>
      {/* Under the radar, where the column would otherwise stand empty beside the taller list of
          numbers: a couple of facts, and in their place what a hovered corner was measured from.
          Fixed height, so hovering doesn't push the page around. */}
      <div className="mt-2.5 flex h-10 w-full flex-col items-center justify-center gap-0.5 text-center text-xs text-muted">
        {active ? <p>{active.detail}</p> : footer}
      </div>
    </div>
  );
}

function StatLine({ value, label }: { value: string | number; label: string }) {
  return (
    <div className="flex items-baseline gap-4">
      <span className="w-16 shrink-0 text-right text-lg font-semibold tabular-nums text-accent-text">{value}</span>
      <span className="min-w-0 truncate text-sm text-muted">{label}</span>
    </div>
  );
}

function hourLabel(hour: number, locale: string): string {
  return new Date(2000, 0, 1, hour).toLocaleTimeString(locale, { hour: "numeric", minute: "2-digit" });
}

/** The radar and the numbers beside it. */
export function WatchStyleCard({ stats, compact }: { stats: WatchStats; compact?: boolean }) {
  const { t, i18n } = useTranslation();
  const empty = stats.episodesWatched === 0 && stats.activeDays === 0;
  const round1 = (n: number) => (Math.round(n * 10) / 10).toLocaleString(i18n.language);
  const [mode, setMode] = useState<"style" | "genres">("style");
  const genres = genreAxes(stats.genres, t);
  const canShowGenres = genres.length >= MIN_GENRE_AXES;
  const showGenres = mode === "genres" && canShowGenres;
  const axes: RadarAxis[] = showGenres
    ? genres
    : stats.axes.map((a) => ({ id: a.id, label: t(`profile.style.axes.${a.id}`), value: a.value, short: axisShort(a, t, i18n.language), detail: axisDetail(a, t) }));
  // How much is watched, then the records - two groups on the desktop, one run of tiles on the phone.
  // Episodes a day is not among them: it is the value under the radar's "binging" corner.
  const pace = [
    { value: Math.round(stats.minutesPerActiveDay), label: t("profile.style.minutesPerDay") },
    { value: stats.activeDays, label: t("profile.style.activeDays") },
  ];
  const records = [
    { value: stats.bestDayEpisodes, label: t("profile.style.bestDay") },
    { value: stats.longestStreak, label: t("profile.style.longestStreak") },
    { value: stats.titlesFinished, label: t("profile.style.finished") },
    { value: stats.titlesDropped, label: t("profile.style.dropped") },
    ...(stats.ratingAverage !== null ? [{ value: round1(stats.ratingAverage), label: t("profile.style.ratingAverage", { count: stats.ratingCount }) }] : []),
  ];

  return (
    <section className={cn("rounded-2xl border border-border bg-text/[.03]", compact ? "p-3.5" : "p-5")}>
      {/* Wraps on a narrow phone, so the switch drops under the title instead of squeezing it. */}
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-2">
        <h2 className={cn("font-bold text-text", compact ? "text-[15px]" : "text-sm")}>{t("profile.style.title")}</h2>
        <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-muted/70">{t("profile.style.allTime")}</span>
        {canShowGenres && (
          <div className="ml-1 flex self-center rounded-lg bg-text/[.05] p-0.5" role="tablist">
            {(["style", "genres"] as const).map((m) => (
              <button
                key={m}
                type="button"
                role="tab"
                aria-selected={mode === m}
                onClick={() => setMode(m)}
                className={cn("rounded-md px-2.5 py-1 text-xs font-semibold transition-colors", mode === m ? "bg-text/[.1] text-text" : "text-muted hover:text-text")}
              >
                {t(m === "style" ? "profile.style.modeStyle" : "profile.style.modeGenres")}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className={cn("mt-2 grid items-center gap-6", compact ? "grid-cols-1" : "grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]")}>
        {/* Keyed by mode, so the shape grows out of the middle again when switched. */}
        <Radar
          key={showGenres ? "genres" : "style"}
          axes={axes}
          label={t("profile.style.title")}
          footer={!empty && (stats.peakHour !== null || stats.topDub) && (
            <>
              {stats.peakHour !== null && <p>{t("profile.style.peakHour")} <span className="font-semibold text-text">{hourLabel(stats.peakHour, i18n.language)}</span></p>}
              {stats.topDub && <p className="truncate">{t("profile.style.topDub")} <span className="font-semibold text-text">{stats.topDub.name}</span></p>}
            </>
          )}
        />

        {empty ? (
          <p className="text-sm text-muted">{t("profile.style.empty")}</p>
        ) : compact ? (
          // The phone has no room beside the radar, and a column of lines under it ran a screen long:
          // tiles two to a row instead, an odd last one across both.
          <div className="grid grid-cols-2 gap-2">
            {[...pace, ...records].map((s, i, all) => (
              <div key={s.label} className={cn("rounded-xl bg-text/[.04] px-3 py-2.5", all.length % 2 === 1 && i === all.length - 1 && "col-span-2")}>
                <p className="text-lg font-semibold tabular-nums leading-tight text-accent-text">{s.value}</p>
                <p className="mt-0.5 text-[11px] leading-tight text-muted">{s.label}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col border-l border-border pl-6">
            <div className="flex flex-col gap-2">
              {pace.map((s) => <StatLine key={s.label} value={s.value} label={s.label} />)}
            </div>
            <div className="mt-4 flex flex-col gap-2 border-t border-border pt-4">
              {records.map((s) => <StatLine key={s.label} value={s.value} label={s.label} />)}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
