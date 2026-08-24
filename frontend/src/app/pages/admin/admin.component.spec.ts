import { of } from 'rxjs';
import { AdminComponent } from './admin.component';

describe('AdminComponent skip', () => {
  function createComponent() {
    const api = jasmine.createSpyObj('AdminApiService', [
      'randomSkip', 'randomNext', 'getRandomState'
    ]);
    api.randomSkip.and.returnValue(of(null));
    api.randomNext.and.returnValue(of(null));
    api.getRandomState.and.returnValue(of({ remaining: {}, skipped: {}, openSlots: {} }));
    const snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    const component = new AdminComponent(api, {} as any, snackBar);
    component.player = 'Giocatore';
    component.team = 'Roma';
    return { component, api };
  }

  it('disables skip while an active round has bidders', () => {
    const { component, api } = createComponent();
    component.round = { closed: false };
    component.activeUsers = ['Squadra'];

    component.skip();

    expect(component.skipDisabled).toBeTrue();
    expect(component.skipTooltip).toContain('sono presenti offerte');
    expect(api.randomSkip).not.toHaveBeenCalled();
  });

  it('uses the safe skip endpoint when the active round has no bids', () => {
    const { component, api } = createComponent();
    component.round = { closed: false };
    component.activeUsers = [];

    component.skip();

    expect(api.randomSkip).toHaveBeenCalledOnceWith('Giocatore', 'Roma');
    expect(component.round).toBeNull();
    expect(component.skipping).toBeFalse();
  });
});
