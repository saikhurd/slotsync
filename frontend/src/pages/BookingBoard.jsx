import { useEffect, useState } from "react";
import { api } from "../api/axios";

export default function BookingBoard() {
  const [resources, setResources] = useState([]);
  const [resourceId, setResourceId] = useState("");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
  const [durationHours, setDurationHours] = useState(1);
  const [message, setMessage] = useState(null);
  const [messageType, setMessageType] = useState("info"); // "info" | "error"
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    api.get("/resources").then(({ data }) => {
      setResources(data.filter((r) => r.active));
      if (data.length > 0) setResourceId(data[0].id);
    });
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setMessage(null);
    setSubmitting(true);

    const slotStart = `${date}T${time}:00`;
    const [hours, minutes] = time.split(":").map(Number);
    const endHours = hours + Number(durationHours);
    const slotEnd = `${date}T${String(endHours).padStart(2, "0")}:${String(minutes).padStart(2,
    "0")}:00`;

    try {
      await api.post("/bookings", { resourceId: Number(resourceId), slotStart, slotEnd });
      setMessageType("info");
      setMessage("Booked. Check My Bookings to confirm.");
    } catch (err) {
      const status = err.response?.status;
      setMessageType("error");
      if (status === 409) {
        // The concurrency-safe path: someone else took this exact slot
        // between page load and submit. Don't retry silently — tell the
        // user plainly and let them pick a different time.
        setMessage(err.response.data?.message || "That slot was just taken. Please pick another time.");
      } else if (status === 429) {
        setMessage("Too many booking attempts — please wait a moment before trying again.");
      } else {
        setMessage(err.response?.data?.message || "Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="board-panel" style={{ maxWidth: 480 }}>
      <div className="board-title">Book a slot</div>
      {message && <div className={messageType === "error" ? "error-banner" : "board-panel"}>{message}</div>}
      <form onSubmit={handleSubmit}>
        <label>Resource</label>
        <select value={resourceId} onChange={(e) => setResourceId(e.target.value)} required>
          {resources.map((r) => (
            <option key={r.id} value={r.id}>{r.name}</option>
          ))}
        </select>

        <label>Date</label>
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />

        <label>Start time</label>
        <input type="time" value={time} onChange={(e) => setTime(e.target.value)} required />

        <label>Duration (hours)</label>
        <input
          type="number"
          min="1"
          max="8"
          value={durationHours}
          onChange={(e) => setDurationHours(e.target.value)}
        />

        <button className="btn btn-primary" type="submit" disabled={submitting} style={{ width: "100%" }}>
          {submitting ? "Booking..." : "Confirm booking"}
        </button>
      </form>
    </div>
  );
}
