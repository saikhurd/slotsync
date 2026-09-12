import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function NavBar() {
  const { isAuthenticated, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="top-nav">
      <span className="brand">SLOTSYNC</span>
      <div>
        {isAuthenticated ? (
          <>
            <Link to="/book">Book a slot</Link>
            <Link to="/my-bookings">My bookings</Link>
            {isAdmin && <Link to="/admin">Admin</Link>}
            <button className="btn" onClick={handleLogout}>Log out</button>
          </>
        ) : (
          <>
            <Link to="/login">Log in</Link>
            <Link to="/register">Register</Link>
          </>
        )}
      </div>
    </div>
  );
}
