import { useEffect, useState } from "react";
import { api } from "../api/axios";

export default function MyBookings() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  function load() {
    setLoading(true);
    api
      .get("/bookings/me")
      .then(({ data }) => setBookings(data))
      .catch(() => setError("Could not load bookings."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCancel(id) {
    try {
      await api.delete(`/bookings/${id}`);
      load();
    } catch {
      setError("Could not cancel that booking.");
    }
  }

  return (
    <div className="board-panel">
      <div className="board-title">My bookings</div>
      {error && <div className="error-banner">{error}</div>}
      {loading ? (
        <p>Loading...</p>
      ) : bookings.length === 0 ? (
        <p style={{ color: "var(--flap-muted)" }}>No bookings yet.</p>
      ) : (
        <>
          <div className="flap-row header">
            <span>Resource</span>
            <span>Start</span>
            <span>Status</span>
            <span></span>
          </div>
          {bookings.map((b) => (
            <div className="flap-row" key={b.id}>
              <span>{b.resourceName}</span>
              <span>{new Date(b.slotStart).toLocaleString()}</span>
              <span className={b.status === "CONFIRMED" ? "status-confirmed" : "status-cancelled"}>
                {b.status}
              </span>
              <span>
                {b.status === "CONFIRMED" && (
                  <button className="btn btn-danger" onClick={() => handleCancel(b.id)}>Cancel</button>
                )}
              </span>
            </div>
          ))}
        </>
      )}
    </div>
  );
}
