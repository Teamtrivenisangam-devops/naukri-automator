import type { JobEvent } from "./types";

declare global {
  interface Window {
    NAUKRI_BE_PORT?: number;
  }
}

function getPort(): number {
  return window.NAUKRI_BE_PORT ?? 5000;
}

function wsUrl(jobId: string): string {
  if (import.meta.env.MODE === "test") {
    return `ws://127.0.0.1:5000/ws/jobs/${jobId}`;
  }

  if (import.meta.env.DEV) {
    // DEV: connect directly to the standalone BE at :8080.
    return `ws://127.0.0.1:8080/ws/jobs/${jobId}`;
  }

  return `ws://127.0.0.1:${getPort()}/ws/jobs/${jobId}`;
}

export interface JobStreamHandle {
  close(): void;
}

export function connectJobStream(
  jobId: string,
  onEvent: (event: JobEvent) => void,
  onClose: () => void
): JobStreamHandle {
  const url = wsUrl(jobId);
  const socket = new WebSocket(url);

  socket.addEventListener("message", (msg) => {
    try {
      const event = JSON.parse(msg.data as string) as JobEvent;
      onEvent(event);
    } catch {
      // ignore malformed frames
    }
  });

  socket.addEventListener("close", () => {
    onClose();
  });

  socket.addEventListener("error", () => {
    onClose();
  });

  return {
    close() {
      socket.close();
    }
  };
}
