import { Directive, ElementRef, HostListener, Input, OnDestroy, inject } from '@angular/core';

/**
 * Hover/focus tooltip: <button appTooltip="Log out">
 *
 * The bubble is appended to <body> and positioned with fixed coordinates, so it
 * never gets clipped by a scrolling sidebar. Its styles are set inline, which
 * means no global stylesheet changes are needed.
 */
@Directive({ selector: '[appTooltip]', standalone: true })
export class TooltipDirective implements OnDestroy {
  @Input('appTooltip') text = '';

  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private tip: HTMLDivElement | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private hovering = false;

  @HostListener('mouseenter')
  @HostListener('focus')
  onEnter(): void {
    this.hovering = true;
    this.scheduleShow(250);
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  onLeave(): void {
    this.hovering = false;
    this.hide();
  }

  // A click often changes the label (e.g. "Delete" -> "Click again to confirm"),
  // so drop the old bubble and re-show it with the fresh text.
  @HostListener('click')
  onClick(): void {
    this.hide();
    if (this.hovering) {
      this.scheduleShow(60);
    }
  }

  ngOnDestroy(): void {
    this.hovering = false;
    this.hide();
  }

  private scheduleShow(delayMs: number): void {
    this.clearTimer();
    this.timer = setTimeout(() => this.render(), delayMs);
  }

  private render(): void {
    if (!this.text || this.tip) return;

    const tip = document.createElement('div');
    tip.textContent = this.text;
    tip.setAttribute('role', 'tooltip');
    Object.assign(tip.style, {
      position: 'fixed',
      left: '0px',
      top: '0px',
      zIndex: '1000',
      background: 'var(--panel-raised, #23272b)',
      color: 'var(--text, #e8e6e1)',
      border: '1px solid var(--border, #2a2e31)',
      borderRadius: '6px',
      padding: '4px 8px',
      fontSize: '12px',
      fontFamily: 'var(--font-body, sans-serif)',
      whiteSpace: 'nowrap',
      pointerEvents: 'none',
      boxShadow: '0 4px 12px rgba(0, 0, 0, 0.35)',
    });
    document.body.appendChild(tip);

    const rect = this.host.nativeElement.getBoundingClientRect();
    const tipRect = tip.getBoundingClientRect();
    const margin = 8;

    // Centered above the button; flip below if there's no room; stay on screen.
    let left = rect.left + rect.width / 2 - tipRect.width / 2;
    left = Math.max(margin, Math.min(left, window.innerWidth - tipRect.width - margin));
    let top = rect.top - tipRect.height - 8;
    if (top < margin) {
      top = rect.bottom + 8;
    }

    tip.style.left = `${left}px`;
    tip.style.top = `${top}px`;
    this.tip = tip;
  }

  private hide(): void {
    this.clearTimer();
    this.tip?.remove();
    this.tip = null;
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
