import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

interface AuthResponse {
  token: string;
  email: string;
  username: string | null;
}

const TOKEN_KEY = 'relay_token';
const EMAIL_KEY = 'relay_email';
const USERNAME_KEY = 'relay_username';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  // Seed from localStorage so a page refresh doesn't log the user out.
  private readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly email = signal<string | null>(localStorage.getItem(EMAIL_KEY));
  readonly username = signal<string | null>(localStorage.getItem(USERNAME_KEY));
  readonly isAuthenticated = computed(() => !!this.token());

  // What the sidebar shows. Accounts made before usernames existed fall back
  // to the part of the email before the @.
  readonly displayName = computed(
    () => this.username() || (this.email() ?? '').split('@')[0] || ''
  );

  getToken(): string | null {
    return this.token();
  }

  register(email: string, username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, {
        email: email.trim(),
        username: username.trim(),
        password,
      })
      .pipe(tap((res) => this.storeSession(res)));
  }

  /** identifier is either an email or a username. */
  login(identifier: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, {
        identifier: identifier.trim(),
        password,
      })
      .pipe(tap((res) => this.storeSession(res)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(USERNAME_KEY);
    this.token.set(null);
    this.email.set(null);
    this.username.set(null);
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(EMAIL_KEY, res.email);
    if (res.username) {
      localStorage.setItem(USERNAME_KEY, res.username);
    } else {
      localStorage.removeItem(USERNAME_KEY);
    }
    this.token.set(res.token);
    this.email.set(res.email);
    this.username.set(res.username ?? null);
  }
}
