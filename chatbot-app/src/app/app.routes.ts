import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { ChatWindowComponent } from './components/chat-window/chat-window.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'chat', component: ChatWindowComponent, canActivate: [authGuard] },
];
