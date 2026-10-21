import { Component, ElementRef, ViewChild, AfterViewChecked, OnInit, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { MessageBubbleComponent } from '../message-bubble/message-bubble.component';
import { ChatInputComponent } from '../chat-input/chat-input.component';
import { ChatService } from '../../services/chat.service';

@Component({
  selector: 'app-chat-window',
  standalone: true,
  imports: [CommonModule, SidebarComponent, MessageBubbleComponent, ChatInputComponent],
  templateUrl: './chat-window.component.html',
  styleUrl: './chat-window.component.scss',
})
export class ChatWindowComponent implements OnInit, AfterViewChecked {
  chat = inject(ChatService);

  @ViewChild('scrollAnchor') private scrollAnchor?: ElementRef<HTMLDivElement>;

  session = computed(() =>
    this.chat.sessions().find((s) => s.id === this.chat.activeSessionId())
  );

  ngOnInit() {
    this.chat.initialize();
  }

  onSend(text: string) {
    this.chat.sendMessage(text);
  }

  ngAfterViewChecked() {
    this.scrollAnchor?.nativeElement.scrollIntoView({ behavior: 'smooth' });
  }
}
