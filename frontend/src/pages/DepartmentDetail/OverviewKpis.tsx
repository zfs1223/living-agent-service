export default function OverviewKpis({ kpis }: any) {
  return (
    <section className="office-kpi-grid office-kpi-grid--compact">
      {kpis.map((kpi: any, idx: number) => (
        <div key={idx} className="card office-kpi-card office-kpi-card--compact office-kpi-card--tiny">
          <div className="office-kpi-card__label office-kpi-card__label--tiny">
            <span>{kpi.icon}</span>
            <span>{kpi.label}</span>
          </div>
          <div className="office-kpi-card__value office-kpi-card__value--tiny">{kpi.value}</div>
          <div className="office-kpi-card__hint office-kpi-card__hint--tiny">{kpi.hint}</div>
        </div>
      ))}
    </section>
  );
}
