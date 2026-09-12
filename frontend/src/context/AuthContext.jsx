import { createContext, useContext, useState, useCallback } from "react";
import { api, setTokens, clearTokens, getTokens } from "../api/axios";

const AuthContext = createContext(null);

function decodeRole(token) {
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.role || null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(!!getTokens().accessToken);
  const [role, setRole] = useState(() => decodeRole(getTokens().accessToken));
  const [error, setError] = useState(null);

  const login = useCallback(async (email, password) => {
    setError(null);
    try {
      const { data } = await api.post("/auth/login", { email, password });
      setTokens(data);
      setIsAuthenticated(true);
      setRole(decodeRole(data.accessToken));
      return true;
    } catch (err) {
      setError(err.response?.data?.message || "Login failed.");
      return false;
    }
  }, []);

  const register = useCallback(async (email, password) => {
    setError(null);
    try {
      const { data } = await api.post("/auth/register", { email, password });
      setTokens(data);
      setIsAuthenticated(true);
      setRole(decodeRole(data.accessToken));
      return true;
    } catch (err) {
      setError(err.response?.data?.message || "Registration failed.");
      return false;
    }
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    setIsAuthenticated(false);
    setRole(null);
  }, []);

  const isAdmin = role === "ADMIN";

  return (
    <AuthContext.Provider value={{ isAuthenticated, isAdmin, error, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
