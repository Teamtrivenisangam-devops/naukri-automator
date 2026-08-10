
import type { StartJobRequest, StartJobResponse, ParsedEmailRow } from "./types";

declare global {
  interface Window {
    NAUKRI_BE_PORT?: number;
    /** Injected by the Electron preload when --e2e-mock=<url> is present. */
    __E2E_MOCK__?: string;
  }
}

function getPort(): number {
  return window.NAUKRI_BE_PORT ?? 5000;
}

/**
 * Base URL for backend calls.
 *
 * - Dev mode (Vite dev-server): return "" so fetch uses relative URLs like
 *   /api/jobs. The Vite proxy (see vite.config.ts) forwards these to
 *   http://127.0.0.1:8080 where the standalone BE runs.
 * - Prod mode (packaged Electron app): use the port injected by the preload
 *   via contextBridge (window.NAUKRI_BE_PORT).
 */
function base(): string {
  if (import.meta.env.DEV) return "";
  return `http://127.0.0.1:${getPort()}`;
}

export async function startJob(req: StartJobRequest): Promise<StartJobResponse> {
  // When Electron is launched with --e2e-mock=<url>, forward the override so
  // the backend targets the mock Naukri server instead of the real site.
  const e2eMock = (window as Window).__E2E_MOCK__;
  const payload: StartJobRequest = e2eMock
    ? { ...req, baseUrlOverride: e2eMock }
    : req;

  const res = await fetch(`${base()}/api/jobs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error(`startJob failed: ${res.status}`);
  return res.json() as Promise<StartJobResponse>;
}

export async function stopJob(id: string): Promise<void> {
  const res = await fetch(`${base()}/api/jobs/${id}/stop`, { method: "POST" });
  if (!res.ok) throw new Error(`stopJob failed: ${res.status}`);
}

export async function continueJob(id: string): Promise<void> {
  const res = await fetch(`${base()}/api/jobs/${id}/continue`, { method: "POST" });
  if (!res.ok) throw new Error(`continueJob failed: ${res.status}`);
}

export async function skipJob(id: string): Promise<void> {
  const res = await fetch(`${base()}/api/jobs/${id}/skip`, { method: "POST" });
  if (!res.ok) throw new Error(`skipJob failed: ${res.status}`);
}

export function downloadTemplate(): void {
  const a = document.createElement("a");
  a.href = `${base()}/api/template`;
  a.download = "naukri-emails-template.xlsx";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

export async function parseExcel(file: File): Promise<ParsedEmailRow[]> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${base()}/api/parse-excel`, {
    method: "POST",
    body: form
  });
  if (!res.ok) throw new Error(`parseExcel failed: ${res.status}`);
  return res.json() as Promise<ParsedEmailRow[]>;
}