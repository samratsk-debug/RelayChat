import { AfterViewInit, Directive, ElementRef, inject } from '@angular/core';

// Focuses and selects an input as soon as it appears (used for inline rename).
@Directive({ selector: '[appAutofocus]', standalone: true })
export class AutofocusDirective implements AfterViewInit {
  private el = inject<ElementRef<HTMLInputElement>>(ElementRef);

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.el.nativeElement.focus();
      this.el.nativeElement.select();
    });
  }
}
