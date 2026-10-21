import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TooltipDirective } from '../../directives/tooltip.directive';

@Component({
  selector: 'app-chat-input',
  standalone: true,
  imports: [CommonModule, FormsModule, TooltipDirective],
  templateUrl: './chat-input.component.html',
  styleUrl: './chat-input.component.scss',
})
export class ChatInputComponent {
  @Input() disabled = false;
  @Output() send = new EventEmitter<string>();

  value = '';

  submit() {
    if (this.disabled || !this.value.trim()) return;
    this.send.emit(this.value);
    this.value = '';
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
  }
}
