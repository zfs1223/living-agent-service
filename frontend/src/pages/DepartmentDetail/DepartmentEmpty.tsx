export default function DepartmentEmpty({ title, desc }: { title: string; desc: string; }) {
  return (
    <div className="empty-state-small office-empty-block">
      {title}
      <span>{desc}</span>
    </div>
  );
}
