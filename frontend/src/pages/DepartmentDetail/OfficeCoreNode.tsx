export default function OfficeCoreNode({ label, className }: { label: string; className: string; }) {
  return <div className={`office-floor__core ${className}`}>{label}</div>;
}
