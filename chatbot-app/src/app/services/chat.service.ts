import { Injectable, signal, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ChatMessage, MessageRole } from '../models/message.model';
import { ChatSession } from '../models/session.model';
import { environment } from '../../environments/environment';

interface SessionDto {
  id: string;
  title: string;
  pinned: boolean;
  createdAt: string;
}

interface MessageDto {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  timestamp: string;
}

const BASE = environment.apiBaseUrl;
const DEFAULT_TITLE = 'New session';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private http = inject(HttpClient);

  readonly sessions = signal<ChatSession[]>([]);
  readonly activeSessionId = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly isAssistantTyping = signal(false);
  readonly isLoadingSessions = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Call once when the chat page loads. */
  async initialize(): Promise<void> {
    // This service lives for the whole browser session, so clear anything left
    // over from a previous login before loading the new user's data.
    this.sessions.set([]);
    this.activeSessionId.set(null);
    this.messages.set([]);
    this.errorMessage.set(null);

    this.isLoadingSessions.set(true);
    try {
      const dtos = await firstValueFrom(this.http.get<SessionDto[]>(`${BASE}/sessions`));
      this.sessions.set(dtos.map(this.toSession));

      if (this.sessions().length > 0) {
        await this.selectSession(this.sessions()[0].id);
      } else {
        await this.newSession();
      }
    } catch {
      this.errorMessage.set("Couldn't load your sessions. Is the backend running?");
    } finally {
      this.isLoadingSessions.set(false);
    }
  }

  /** Re-fetches the list; the backend returns it pinned-first. */
  async refreshSessions(): Promise<void> {
    try {
      const dtos = await firstValueFrom(this.http.get<SessionDto[]>(`${BASE}/sessions`));
      this.sessions.set(dtos.map(this.toSession));
    } catch {
      // Not critical: the list just stays as it was.
    }
  }

  async selectSession(id: string): Promise<void> {
    this.activeSessionId.set(id);
    this.messages.set([]);
    try {
      const dtos = await firstValueFrom(
        this.http.get<MessageDto[]>(`${BASE}/sessions/${id}/messages`)
      );
      this.messages.set(dtos.map(this.toMessage));
    } catch {
      this.errorMessage.set("Couldn't load messages for that session.");
    }
  }

  async newSession(): Promise<void> {
    // Already looking at a blank chat? Don't pile up empty "New session" entries.
    if (this.activeSessionId() && this.messages().length === 0 && !this.isAssistantTyping()) {
      return;
    }

    try {
      const dto = await firstValueFrom(
        this.http.post<SessionDto>(`${BASE}/sessions`, { title: DEFAULT_TITLE })
      );
      const session = this.toSession(dto);
      this.sessions.update((s) => [session, ...s]);
      this.activeSessionId.set(session.id);
      this.messages.set([]);
    } catch {
      this.errorMessage.set("Couldn't create a new session.");
    }
  }

  async renameSession(id: string, title: string): Promise<void> {
    const trimmed = title.trim();
    if (!trimmed) return;

    try {
      const dto = await firstValueFrom(
        this.http.put<SessionDto>(`${BASE}/sessions/${id}`, { title: trimmed })
      );
      const updated = this.toSession(dto);
      this.sessions.update((list) => list.map((s) => (s.id === id ? updated : s)));
    } catch {
      this.errorMessage.set("Couldn't rename that chat.");
    }
  }

  async togglePin(id: string): Promise<void> {
    const current = this.sessions().find((s) => s.id === id);
    if (!current) return;

    try {
      await firstValueFrom(
        this.http.put<SessionDto>(`${BASE}/sessions/${id}`, { pinned: !current.pinned })
      );
      await this.refreshSessions();
    } catch {
      this.errorMessage.set("Couldn't update that chat's pin.");
    }
  }

  async deleteSession(id: string): Promise<void> {
    try {
      await firstValueFrom(this.http.delete<void>(`${BASE}/sessions/${id}`));
    } catch {
      this.errorMessage.set("Couldn't delete that chat.");
      return;
    }

    const wasActive = this.activeSessionId() === id;
    this.sessions.update((list) => list.filter((s) => s.id !== id));

    if (wasActive) {
      const next = this.sessions()[0];
      if (next) {
        await this.selectSession(next.id);
      } else {
        this.activeSessionId.set(null);
        this.messages.set([]);
        await this.newSession();
      }
    }
  }

  /**
   * Sends a message and streams the assistant's reply in as it's generated,
   * instead of waiting for the whole response. Uses HttpClient's download-progress
   * events (rather than fetch()) so the auth/error interceptors still apply.
   */
  sendMessage(content: string): void {
    const trimmed = content.trim();
    const sessionId = this.activeSessionId();
    if (!trimmed || !sessionId) return;

    const userMessage: ChatMessage = {
      id: `pending-user-${Date.now()}`,
      role: 'user' as MessageRole,
      content: trimmed,
      timestamp: new Date(),
    };
    const assistantMessage: ChatMessage = {
      id: `pending-assistant-${Date.now()}`,
      role: 'assistant' as MessageRole,
      content: '',
      timestamp: new Date(),
      pending: true,
    };

    this.messages.update((m) => [...m, userMessage, assistantMessage]);
    this.isAssistantTyping.set(true);
    this.errorMessage.set(null);

    this.http
      .post(`${BASE}/sessions/${sessionId}/messages/stream`, { content: trimmed }, {
        observe: 'events',
        responseType: 'text',
        reportProgress: true,
      })
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.DownloadProgress) {
            // partialText is the *cumulative* text received so far, not just the
            // newest chunk, so it can be assigned straight to the bubble.
            const partial = (event as any).partialText ?? '';
            this.updateAssistantContent(assistantMessage.id, partial, true);
          } else if (event.type === HttpEventType.Response) {
            const finalText = (event.body as string) ?? '';
            this.updateAssistantContent(assistantMessage.id, finalText, false);
            this.isAssistantTyping.set(false);

            // The backend titles a chat after its first exchange; pull that in.
            const current = this.sessions().find((s) => s.id === sessionId);
            if (current?.title === DEFAULT_TITLE) {
              this.refreshSessions();
            }
          }
        },
        error: (err: HttpErrorResponse) => {
          this.isAssistantTyping.set(false);
          this.messages.update((m) => m.filter((msg) => msg.id !== assistantMessage.id));

          if (err.status === 429) {
            this.errorMessage.set(
              err.error?.error || "You're sending messages too fast. Wait a moment and try again."
            );
          } else if (err.status !== 401) {
            // 401 is already handled (logout + redirect) by the error interceptor.
            this.errorMessage.set("Your message didn't send. Check the backend and try again.");
          }
        },
      });
  }

  private updateAssistantContent(id: string, content: string, pending: boolean): void {
    this.messages.update((msgs) =>
      msgs.map((m) => (m.id === id ? { ...m, content, pending } : m))
    );
  }

  private toSession = (dto: SessionDto): ChatSession => ({
    id: dto.id,
    title: dto.title,
    pinned: dto.pinned ?? false,
    createdAt: new Date(dto.createdAt),
  });

  private toMessage = (dto: MessageDto): ChatMessage => ({
    id: dto.id,
    role: dto.role === 'USER' ? ('user' as MessageRole) : ('assistant' as MessageRole),
    content: dto.content,
    timestamp: new Date(dto.timestamp),
  });
}
