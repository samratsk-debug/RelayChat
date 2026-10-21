import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatService } from '../../services/chat.service';
import { AuthService } from '../../services/auth.service';
import { ChatSession } from '../../models/session.model';
import { TooltipDirective } from '../../directives/tooltip.directive';
import { AutofocusDirective } from '../../directives/autofocus.directive';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, TooltipDirective, AutofocusDirective],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  chat = inject(ChatService);
  auth = inject(AuthService);
  private router = inject(Router);

  editingId = signal<string | null>(null);
  editValue = '';
  confirmDeleteId = signal<string | null>(null);

  // The backend already returns these in the right order; we just split them.
  pinnedSessions = computed(() => this.chat.sessions().filter((s) => s.pinned));
  recentSessions = computed(() => this.chat.sessions().filter((s) => !s.pinned));

  startRename(session: ChatSession, event: Event) {
    event.stopPropagation();
    this.confirmDeleteId.set(null);
    this.editValue = session.title;
    this.editingId.set(session.id);
  }

  commitRename(session: ChatSession) {
    // Enter and the following blur both land here; only the first should act.
    if (this.editingId() !== session.id) return;

    const value = this.editValue.trim();
    this.editingId.set(null);
    if (value && value !== session.title) {
      this.chat.renameSession(session.id, value);
    }
  }

  cancelRename() {
    this.editingId.set(null);
  }

  togglePin(session: ChatSession, event: Event) {
    event.stopPropagation();
    this.chat.togglePin(session.id);
  }

  // Two-step delete: the first click arms it, the second click confirms.
  requestDelete(session: ChatSession, event: Event) {
    event.stopPropagation();
    if (this.confirmDeleteId() === session.id) {
      this.confirmDeleteId.set(null);
      this.chat.deleteSession(session.id);
    } else {
      this.confirmDeleteId.set(session.id);
    }
  }

  clearDeleteConfirm() {
    if (this.confirmDeleteId()) {
      this.confirmDeleteId.set(null);
    }
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
