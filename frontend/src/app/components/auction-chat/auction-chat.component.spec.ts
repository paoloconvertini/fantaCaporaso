import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AuctionChatComponent } from './auction-chat.component';
import { AuthService } from '../../services/auth.service';

describe('AuctionChatComponent', () => {
  let fixture: ComponentFixture<AuctionChatComponent>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let socket: { close: jasmine.Spy; onmessage: (event: MessageEvent) => void };

  beforeEach(async () => {
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    socket = { close: jasmine.createSpy('close'), onmessage: (_event: MessageEvent) => {} };
    spyOn(window, 'WebSocket').and.returnValue(socket as unknown as WebSocket);
    await TestBed.configureTestingModule({
      declarations: [AuctionChatComponent],
      imports: [FormsModule, HttpClientTestingModule, MatButtonModule, MatIconModule, MatTooltipModule, NoopAnimationsModule],
      providers: [
        { provide: AuthService, useValue: { username: 'angelo' } },
        { provide: Clipboard, useValue: clipboard }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AuctionChatComponent);
    fixture.detectChanges();
  });

  it('marks incoming messages when collapsed and clears the badge on opening', () => {
    const panel: HTMLElement = fixture.nativeElement;
    const deliver = (event: object) => {
      socket.onmessage(new MessageEvent('message', { data: JSON.stringify(event) }));
      fixture.detectChanges();
    };

    deliver({ type: 'history', messages: [{ author: 'admin', text: 'Storico' }] });
    expect(panel.querySelector('.unread')).toBeNull();
    deliver({ type: 'message', message: { author: 'angelo', text: 'Mio' } });
    expect(panel.querySelector('.message.own')?.textContent).toContain('Mio');
    expect(panel.querySelector('.unread')).toBeNull();
    deliver({ type: 'message', message: { author: 'admin', text: 'Nuovo' } });
    expect(panel.querySelector('.unread')?.textContent).toContain('1 nuovo messaggio');

    const details = panel.querySelector('details')!;
    details.open = true;
    details.dispatchEvent(new Event('toggle'));
    fixture.detectChanges();
    expect(panel.querySelector('.unread')).toBeNull();
    deliver({ type: 'message', message: { author: 'admin', text: 'Già visibile' } });
    expect(panel.querySelector('.unread')).toBeNull();

    const send = panel.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(send.disabled).toBeTrue();
    fixture.componentInstance.connected = true;
    fixture.componentInstance.draft = 'Pronto';
    fixture.detectChanges();
    expect(send.disabled).toBeFalse();
    fixture.destroy();
  });

  it('requests an invite from the missing team chip and offers manual copy when needed', () => {
    const http = TestBed.inject(HttpTestingController);
    const panel: HTMLElement = fixture.nativeElement;
    fixture.componentInstance.admin = true;
    fixture.componentInstance.presence = {
      blocking: true,
      teams: [
        { id: 17, name: 'Squadra A', online: false },
        { id: 18, name: 'Squadra B', online: false }
      ]
    };
    fixture.detectChanges();

    spyOn(window, 'open').and.returnValue(null);
    const chips = panel.querySelectorAll<HTMLButtonElement>('.invite-share');
    expect(chips.length).toBe(2);
    expect(chips[0].getAttribute('aria-label')).toBe('Condividi su WhatsApp per Squadra A');
    expect(chips[0].textContent?.trim()).toBe('share');
    const row = panel.querySelector<HTMLElement>('.presence-chips')!;
    expect(getComputedStyle(row).flexWrap).toBe('wrap');
    expect(panel.querySelector('.invite-fallback')).toBeNull();
    chips[0].click();
    const request = http.expectOne('/api/admin/participants/17/auction-invite');
    expect(request.request.method).toBe('POST');
    request.flush({ message: 'Testo verificato' });
    fixture.detectChanges();
    expect(panel.querySelector('textarea')?.value).toBe('Testo verificato');
    expect(panel.querySelector<HTMLAnchorElement>('.invite-fallback a')?.href).toBe('https://wa.me/?text=Testo%20verificato');

    clipboard.copy.and.returnValue(true);
    panel.querySelector<HTMLButtonElement>('.invite-fallback button')!.click();
    fixture.detectChanges();
    expect(clipboard.copy).toHaveBeenCalledWith('Testo verificato');
    expect(panel.querySelector('textarea')).toBeNull();
    http.verify();
  });
  it('opens WhatsApp in the tab reserved at the click with the verified message', () => {
    const replace = jasmine.createSpy('replace');
    const shareWindow = { closed: false, opener: window, location: { replace } };
    const open = spyOn(window, 'open').and.returnValue(shareWindow as unknown as Window);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.shareInvite({ id: 18, name: 'Squadra', online: false });
    expect(open).toHaveBeenCalledWith('', '_blank');
    expect(shareWindow.opener).toBeNull();
    expect(replace).not.toHaveBeenCalled();
    http.expectOne('/api/admin/participants/18/auction-invite').flush({ message: 'Riga 1\nRiga 2 & altro' });
    expect(replace).toHaveBeenCalledOnceWith('https://wa.me/?text=Riga%201%0ARiga%202%20%26%20altro');
    http.verify();
  });
});
