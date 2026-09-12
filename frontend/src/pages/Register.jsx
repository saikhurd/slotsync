import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function Register() {
  const { register, error } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    const ok = await register(email, password);
    setSubmitting(false);
    if (ok) navigate("/book");
  }

  return (
    <div className="board-panel" style={{ maxWidth: 420, margin: "60px auto" }}>
      <div className="board-title">Create account</div>
      {error && <div className="error-banner">{error}</div>}
      <form onSubmit={handleSubmit}>
        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label>Password (min 8 characters)</label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          minLength={8}
          required
        />
        <button className="btn btn-primary" type="submit" disabled={submitting} style={{ width: "100%" }}>
          {submitting ? "Creating account..." : "Create account"}
        </button>
      </form>
      <p style={{ marginTop: 16, fontSize: "0.85rem", color: "var(--flap-muted)" }}>
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </div>
  );
}
