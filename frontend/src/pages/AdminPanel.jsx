import { useEffect, useState } from "react";
import { api } from "../api/axios";

export default function AdminPanel() {
  const [resources, setResources] = useState([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState(null);

  function load() {
    api.get("/resources").then(({ data }) => setResources(data));
  }

  useEffect(load, []);

  async function handleCreate(e) {
    e.preventDefault();
    setError(null);
    try {
      await api.post("/admin/resources", { name, description });
      setName("");
      setDescription("");
      load();
    } catch (err) {
      const status = err.response?.status;
      setError(
        status === 403
          ? "Admin access required for this action."
          : "Could not create resource."
      );
    }
  }

  async function handleDeactivate(id) {
    try {
      await api.put(`/admin/resources/${id}/deactivate`);
      load();
    } catch {
      setError("Could not deactivate that resource.");
    }
  }

  return (
    <div>
      <div className="board-panel">
        <div className="board-title">Add resource</div>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={handleCreate}>
          <label>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required />
          <label>Description</label>
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
          <button className="btn btn-primary" type="submit">Add resource</button>
        </form>
      </div>

      <div className="board-panel">
        <div className="board-title">Resources</div>
        <div className="flap-row header">
          <span>Name</span>
          <span>Description</span>
          <span>Status</span>
          <span></span>
        </div>
        {resources.map((r) => (
          <div className="flap-row" key={r.id}>
            <span>{r.name}</span>
            <span>{r.description}</span>
            <span className={r.active ? "status-confirmed" : "status-cancelled"}>
              {r.active ? "ACTIVE" : "INACTIVE"}
            </span>
            <span>
              {r.active && (
                <button className="btn btn-danger" onClick={() => handleDeactivate(r.id)}>Deactivate</button>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
