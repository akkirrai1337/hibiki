// Settings > Network's traffic chart: bytes per day as stacked bars (video, source data,
// pictures), for the last 7 or 30 days, with a per-day tooltip and a legend that carries each
// kind's total - so the numbers never depend on telling the colors apart.
import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/cn";
import { dayKey, type TrafficDay, type TrafficHistory } from "@/lib/trafficStats";

export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(0, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(bytes < 10 * 1024 * 1024 ? 1 : 0)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

type Kind = keyof TrafficDay;
/** Stacked bottom to top in this order; colors are the validated categorical slots 1-3. */
const KINDS: Array<{ kind: Kind; color: string; label: string }> = [
  { kind: "video", color: "var(--series-1)", label: "settings.network.trafficVideo" },
  { kind: "data", color: "var(--series-2)", label: "settings.network.trafficData" },
  { kind: "images", color: "var(--series-3)", label: "settings.network.trafficImages" },
];

const HEIGHT = 140;
const AXIS_WIDTH = 52;
const GAP = 2;
/** Room above the top gridline for its label. */
const PAD_TOP = 10;

/** A round step (1, 2, 5 x 10^n bytes in MB units) so gridlines read as plain numbers. */
function niceMax(value: number): number {
  if (value <= 0) return 1024 * 1024;
  const mb = value / 1024 / 1024;
  const exponent = Math.floor(Math.log10(mb));
  const base = 10 ** exponent;
  const step = [1, 2, 5, 10].find((m) => m * base >= mb) ?? 10;
  return step * base * 1024 * 1024;
}

export function TrafficChart({ history, range, onRangeChange }: { history: TrafficHistory; range: 7 | 30; onRangeChange: (range: 7 | 30) => void }) {
  const { t, i18n } = useTranslation();
  const wrapRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [hover, setHover] = useState<number | null>(null);

  useLayoutEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const kinds = history.imagesCounted ? KINDS : KINDS.filter((k) => k.kind !== "images");
  const days = useMemo(() => {
    const list: Array<{ key: string; date: Date; values: TrafficDay; total: number }> = [];
    for (let i = range - 1; i >= 0; i--) {
      const date = new Date();
      date.setHours(12, 0, 0, 0);
      date.setDate(date.getDate() - i);
      const key = dayKey(date);
      const values = history.days[key] ?? { data: 0, images: 0, video: 0 };
      list.push({ key, date, values, total: kinds.reduce((sum, k) => sum + values[k.kind], 0) });
    }
    return list;
  }, [history, range, kinds]);

  const totals = kinds.map((k) => ({ ...k, bytes: days.reduce((sum, d) => sum + d.values[k.kind], 0) }));
  const grand = totals.reduce((sum, k) => sum + k.bytes, 0);
  const max = niceMax(Math.max(...days.map((d) => d.total)));
  const plotWidth = Math.max(0, width - AXIS_WIDTH);
  const slot = plotWidth / days.length;
  const barWidth = Math.max(3, Math.min(28, slot * 0.7));
  const y = (bytes: number) => PAD_TOP + HEIGHT - (bytes / max) * HEIGHT;
  const dateLabel = (date: Date) => date.toLocaleDateString(i18n.language, { day: "numeric", month: "short" });
  const hovered = hover === null ? null : days[hover];

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-text">{t("settings.network.trafficTotal", { total: formatBytes(grand), days: range })}</p>
        <div className="flex gap-1">
          {([7, 30] as const).map((option) => (
            <button
              key={option}
              onClick={() => onRangeChange(option)}
              className={cn(
                "rounded-md px-2 py-1 text-xs font-medium transition-colors",
                range === option ? "bg-text/[.1] text-text" : "text-muted hover:text-text",
              )}
            >
              {t("settings.network.trafficDays", { count: option })}
            </button>
          ))}
        </div>
      </div>

      {/* Legend with totals: identity and numbers never rest on color alone. */}
      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
        {totals.map((k) => (
          <span key={k.kind} className="flex items-center gap-1.5 text-xs text-muted">
            <span className="h-2.5 w-2.5 rounded-sm" style={{ background: k.color }} />
            {t(k.label)} <span className="text-text">{formatBytes(k.bytes)}</span>
          </span>
        ))}
      </div>

      <div ref={wrapRef} className="relative mt-3" onMouseLeave={() => setHover(null)}>
        {width > 0 && (
          <svg width={width} height={PAD_TOP + HEIGHT + 20} role="img" aria-label={t("settings.network.trafficTotal", { total: formatBytes(grand), days: range })}>
            {[0, 0.5, 1].map((f) => (
              <g key={f}>
                <line x1={AXIS_WIDTH} x2={width} y1={y(max * f)} y2={y(max * f)} stroke="rgb(var(--color-text) / 0.08)" strokeWidth={1} />
                <text x={AXIS_WIDTH - 8} y={y(max * f) + 4} textAnchor="end" fontSize={10} fill="rgb(var(--color-muted))">
                  {f === 0 ? "0" : formatBytes(max * f)}
                </text>
              </g>
            ))}
            {days.map((day, index) => {
              const x = AXIS_WIDTH + slot * index + (slot - barWidth) / 2;
              let base = PAD_TOP + HEIGHT;
              const segments = kinds
                .filter((k) => day.values[k.kind] > 0)
                .map((k) => {
                  const h = (day.values[k.kind] / max) * HEIGHT;
                  const top = base - h;
                  const seg = { kind: k.kind, color: k.color, top, h };
                  base = top;
                  return seg;
                });
              return (
                <g key={day.key} opacity={hover === null || hover === index ? 1 : 0.45}>
                  {segments.map((seg, i) => {
                    const isTop = i === segments.length - 1;
                    // 2px surface gap between stacked segments; only the top end is rounded.
                    const h = Math.max(1, seg.h - (isTop ? 0 : GAP));
                    const r = isTop ? Math.min(4, barWidth / 2, h) : 0;
                    return (
                      <path
                        key={seg.kind}
                        d={`M${x},${seg.top + h} V${seg.top + r} Q${x},${seg.top} ${x + r},${seg.top} H${x + barWidth - r} Q${x + barWidth},${seg.top} ${x + barWidth},${seg.top + r} V${seg.top + h} Z`}
                        fill={seg.color}
                      />
                    );
                  })}
                  {/* Hit target: the whole column, taller than the bar. */}
                  <rect x={AXIS_WIDTH + slot * index} y={0} width={slot} height={PAD_TOP + HEIGHT + 20} fill="transparent" onMouseEnter={() => setHover(index)} />
                </g>
              );
            })}
            {[0, Math.floor((days.length - 1) / 2), days.length - 1].map((index) => (
              <text
                key={index}
                x={AXIS_WIDTH + slot * index + slot / 2}
                y={PAD_TOP + HEIGHT + 15}
                textAnchor={index === 0 ? "start" : index === days.length - 1 ? "end" : "middle"}
                fontSize={10}
                fill="rgb(var(--color-muted))"
              >
                {dateLabel(days[index].date)}
              </text>
            ))}
          </svg>
        )}

        {hovered && hover !== null && (
          <div
            className="pointer-events-none absolute top-0 z-10 min-w-[150px] rounded-lg border border-text/10 bg-bg/95 px-3 py-2 text-xs shadow-lg"
            style={{
              left: Math.min(Math.max(0, AXIS_WIDTH + slot * hover + slot / 2 - 75), Math.max(0, width - 160)),
            }}
          >
            <p className="font-semibold text-text">{dateLabel(hovered.date)}</p>
            {kinds.map((k) => (
              <p key={k.kind} className="mt-1 flex items-center justify-between gap-3 text-muted">
                <span className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-sm" style={{ background: k.color }} />
                  {t(k.label)}
                </span>
                <span className="text-text">{formatBytes(hovered.values[k.kind])}</span>
              </p>
            ))}
            <p className="mt-1 flex justify-between border-t border-text/10 pt-1 text-text">
              <span>{t("settings.network.trafficDayTotal")}</span>
              <span>{formatBytes(hovered.total)}</span>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
