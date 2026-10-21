import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatMessage } from '../../models/message.model';
import { marked } from 'marked';

marked.setOptions({ breaks: true });
@Component({
  selector: 'app-message-bubble',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './message-bubble.component.html',
  styleUrl: './message-bubble.component.scss',
})
export class MessageBubbleComponent {
  @Input({ required: true }) message!: ChatMessage;

    renderedContent(): string {
      return marked.parse(this.message.content || '', { async: false }) as string;
    }
}
