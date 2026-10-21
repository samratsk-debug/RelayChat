import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  identifier = ''; // login: email OR username
  email = ''; // register only
  username = ''; // register only
  password = '';

  mode = signal<'login' | 'register'>('login');
  loading = signal(false);
  errorMessage = signal('');

  toggleMode() {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.errorMessage.set('');
  }

  submit() {
    if (this.loading()) return;

    const isLogin = this.mode() === 'login';
    const ready = isLogin
      ? this.identifier.trim() && this.password
      : this.email.trim() && this.username.trim() && this.password;
    if (!ready) return;

    this.loading.set(true);
    this.errorMessage.set('');

    const request$ = isLogin
      ? this.auth.login(this.identifier, this.password)
      : this.auth.register(this.email, this.username, this.password);

    request$.subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractError(err));
      },
    });
  }

  // The backend sends either {error: "..."} or, for validation failures,
  // {fieldName: "message"}. Show the first readable message either way.
  private extractError(err: any): string {
    if (err?.status === 0) {
      return "Can't reach the server. Is the backend running?";
    }
    const body = err?.error;
    if (body?.error) {
      return body.error;
    }
    if (body && typeof body === 'object') {
      const first = Object.values(body)[0];
      if (typeof first === 'string') {
        return first;
      }
    }
    return 'Something went wrong. Try again.';
  }
}
