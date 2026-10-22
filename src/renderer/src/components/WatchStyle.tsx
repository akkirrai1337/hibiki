import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "motion/react";
import { cn } from "@/lib/cn";
import type { GenreShare, StyleAxis, WatchStats } from "@/lib/watchStats";

// The profile's statistics, laid out the way a game's career screen is: a radar of how you watch
// (or of genres) next to a column of plain numbers, then when you watch - hours of the day, weekdays.

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

/** One corner of the radar, already in words. */
interface RadarAxis {
  id: string;
  label: string;
  /** 0..1 */
  value: number;
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
  return shown.map((g) => ({ id: g.name, label: g.name, value: g.share / top, detail: t("profile.style.genreDetail", { name: g.name, value: Math.round(g.share * 100) }) }));
}

function Radar({ axes, label }: { axes: RadarAxis[]; label: string }) {
  const gradientId = useId();
  const [hovered, setHovered] = useState<string | null>(null);
  const count = axes.length;
  // An empty radar still shows a speck in the middle, not nothing - same as the reference's
  // inner pentagon, which is where every shape starts from.
  const shape = axes.map((a, i) => point(i, count, Math.max(0.06, a.value)));
  const active = axes.find((a) => a.id === hovered) ?? null;

  return (
    <div className="flex flex-col items-center">
      <svg viewBox={`-72 -6 ${SIZE + 144} ${SIZE + 12}`} className="w-full max-w-[460px] overflow-visible" role="img" aria-label={label}>
        <defs>
          <radialGradient id={gradientId}>
            <stop offset="0%" stopColor="rgb(var(--color-accent))" stopOpacity="0.75" />
            <stop offset="100%" stopColor="rgb(var(--color-accent))" stopOpacity="0.18" />
          </radialGradient>
        </defs>

        {RINGS.map((r) => (
          <polygon key={r} points={polygon(axes.map((_, i) => point(i, count, r)))} fill={r === 1 ? "rgb(var(--color-text) / 0.025)" : "none"} stroke="rgb(var(--color-text) / 0.09)" strokeWidth={1} />
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
          const anchor = Math.abs(x - CENTER) < 4 ? "middle" : x > CENTER ? "start" : "end";
          return (
            <g key={a.id} onMouseEnter={() => setHovered(a.id)} onMouseLeave={() => setHovered((h) => (h === a.id ? null : h))} onClick={() => setHovered((h) => (h === a.id ? null : a.id))} className="cursor-default">
              {/* A wider invisible target than the letters themselves. */}
              <circle cx={x} cy={y} r={26} fill="transparent" />
              <text
                x={x}
                y={y}
                textAnchor={anchor}
                dominantBaseline="middle"
                className={cn("select-none text-[10.5px] font-semibold uppercase tracking-[0.14em] transition-colors", hovered === a.id ? "fill-text" : "fill-muted")}
              >
                {shorten(a.label)}
              </text>
            </g>
          );
        })}
      </svg>
      {/* Fixed height, so hovering doesn't push the page around. */}
      <p className="h-5 text-center text-xs text-muted">{active?.detail ?? ""}</p>
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
    : stats.axes.map((a) => ({ id: a.id, label: t(`profile.style.axes.${a.id}`), value: a.value, detail: axisDetail(a, t) }));

  return (
    <section className={cn("rounded-2xl border border-border bg-text/[.03]", compact ? "p-3.5" : "p-5")}>
      <div className="flex items-baseline gap-3">
        <h2 className={cn("font-bold text-text", compact ? "text-[15px]" : "text-sm")}>{t("profile.style.title")}</h2>
        <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-muted/70">{t("profile.style.allTime")}</span>
        {canShowGenres && (
          <div className="ml-auto flex rounded-lg bg-text/[.05] p-0.5" role="tablist">
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
        <Radar key={showGenres ? "genres" : "style"} axes={axes} label={t("profile.style.title")} />

        {empty ? (
          <p className="text-sm text-muted">{t("profile.style.empty")}</p>
        ) : (
          <div className={cn("flex flex-col", compact ? "gap-4" : "border-l border-border pl-6")}>
            <div className="flex flex-col gap-2">
              <StatLine value={round1(stats.episodesPerActiveDay)} label={t("profile.style.perDay")} />
              <StatLine value={Math.round(stats.minutesPerActiveDay)} label={t("profile.style.minutesPerDay")} />
              <StatLine value={stats.activeDays} label={t("profile.style.activeDays")} />
            </div>
            <div className={cn("flex flex-col gap-2 border-t border-border", compact ? "pt-4" : "mt-4 pt-4")}>
              <StatLine value={stats.bestDayEpisodes} label={t("profile.style.bestDay")} />
              <StatLine value={stats.longestStreak} label={t("profile.style.longestStreak")} />
              <StatLine value={stats.titlesFinished} label={t("profile.style.finished")} />
              <StatLine value={stats.titlesDropped} label={t("profile.style.dropped")} />
              {stats.ratingAverage !== null && <StatLine value={round1(stats.ratingAverage)} label={t("profile.style.ratingAverage", { count: stats.ratingCount })} />}
            </div>
            {(stats.peakHour !== null || stats.topDub) && (
              <div className={cn("flex flex-col gap-1 border-t border-border text-xs text-muted", compact ? "pt-4" : "mt-4 pt-4")}>
                {stats.peakHour !== null && <p>{t("profile.style.peakHour")} <span className="font-semibold text-text">{hourLabel(stats.peakHour, i18n.language)}</span></p>}
                {stats.topDub && <p className="truncate">{t("profile.style.topDub")} <span className="font-semibold text-text">{stats.topDub.name}</span></p>}
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function MiniBars({ values, labels, highlight, height }: { values: number[]; labels: (string | null)[]; highlight: number | null; height: string }) {
  const max = Math.max(1, ...values);
  return (
    <div>
      <div className={cn("flex items-end gap-[3px]", height)}>
        {values.map((v, i) => (
          <motion.div
            key={i}
            initial={{ height: 0 }}
            animate={{ height: v > 0 ? `${Math.max(6, (v / max) * 100)}%` : 3 }}
            transition={{ duration: 0.4, ease: "easeOut", delay: i * 0.01 }}
            className={cn("flex-1 rounded-[3px]", v === 0 ? "bg-text/[.07]" : i === highlight ? "bg-accent" : "bg-accent/40")}
          />
        ))}
      </div>
      <div className="mt-1.5 flex gap-[3px]">
        {labels.map((l, i) => (
          <span key={i} className="flex-1 text-center text-[10px] text-muted/70">{l ?? ""}</span>
        ))}
      </div>
    </div>
  );
}

export function WatchClock({ stats, compact }: { stats: WatchStats; compact?: boolean }) {
  const { t, i18n } = useTranslation();
  // 2024-01-01 was a Monday.
  const weekdays = Array.from({ length: 7 }, (_, i) => new Date(2024, 0, 1 + i).toLocaleDateString(i18n.language, { weekday: "short" }));
  const peakWeekday = stats.byWeekday.some((v) => v > 0) ? stats.byWeekday.indexOf(Math.max(...stats.byWeekday)) : null;
  const hourLabels = stats.byHour.map((_, h) => (h % 6 === 0 ? String(h) : null));
  return (
    <section className={cn("rounded-2xl border border-border bg-text/[.03]", compact ? "p-3.5" : "p-5")}>
      <h2 className={cn("mb-4 font-bold text-text", compact ? "text-[15px]" : "text-sm")}>{t("profile.style.clockTitle")}</h2>
      <div className={cn("grid gap-5", compact ? "grid-cols-1" : "grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] gap-8")}>
        <div>
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-muted/70">{t("profile.style.byHour")}</p>
          <MiniBars values={stats.byHour} labels={hourLabels} highlight={stats.peakHour} height="h-20" />
        </div>
        <div>
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-muted/70">{t("profile.style.byWeekday")}</p>
          <MiniBars values={stats.byWeekday} labels={weekdays} highlight={peakWeekday} height="h-14" />
        </div>
      </div>
    </section>
  );
}
